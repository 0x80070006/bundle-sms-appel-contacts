package app.people.contacts.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One account's copy of a label: the Android contact group that holds it there. */
data class LabelGroup(val id: Long, val accountType: String?, val accountName: String?)

/**
 * A label: a name, and who's in it, whatever account they're saved in. Android groups belong
 * to one account each, so a label is every group with the same name (ignoring case), one per
 * account; a group is made in an account the first time one of its contacts joins the label.
 * [id] is one of the groups' ids (the oldest), used to open the label.
 */
data class Label(
    val id: Long,
    val title: String,
    val groups: List<LabelGroup>,
    val memberIds: Set<Long>,
) {
    val groupIds: Set<Long> get() = groups.mapTo(HashSet()) { it.id }
}

/** Labels with the same name are one label, wherever their groups are. */
private fun labelKey(title: String) = title.trim().lowercase()

private fun sameAccount(type: String?, name: String?, otherType: String?, otherName: String?): Boolean =
    if (type.isNullOrEmpty()) otherType.isNullOrEmpty()
    else type == otherType && name.orEmpty() == otherName.orEmpty()

/** Reads and changes labels. */
class LabelStore(private val context: Context) {
    private val resolver get() = context.contentResolver

    /** Every label the user made (system groups like "My Contacts" aren't labels), by name. */
    suspend fun load(): List<Label> = withContext(Dispatchers.IO) {
        val members = HashMap<Long, MutableSet<Long>>()
        runCatching {
            resolver.query(
                Data.CONTENT_URI, arrayOf(Data.CONTACT_ID, GroupMembership.GROUP_ROW_ID),
                "${Data.MIMETYPE} = ?", arrayOf(GroupMembership.CONTENT_ITEM_TYPE), null,
            )?.use { c -> while (c.moveToNext()) members.getOrPut(c.getLong(1)) { HashSet() }.add(c.getLong(0)) }
        }
        // (title, group) in id order, so the oldest group of a label names it and opens it.
        val groups = ArrayList<Pair<String, LabelGroup>>()
        runCatching {
            resolver.query(
                Groups.CONTENT_URI,
                arrayOf(Groups._ID, Groups.TITLE, Groups.ACCOUNT_TYPE, Groups.ACCOUNT_NAME),
                "${Groups.DELETED} = 0 AND ${Groups.SYSTEM_ID} IS NULL AND ${Groups.TITLE} IS NOT NULL",
                null, "${Groups._ID} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val title = c.getString(1)
                    if (title.isBlank()) continue
                    groups += title to LabelGroup(c.getLong(0), c.getString(2), c.getString(3))
                }
            }
        }
        groups.groupBy { (title, _) -> labelKey(title) }.values.map { same ->
            val all = same.map { it.second }
            Label(
                id = all.first().id,
                title = same.first().first.trim(),
                groups = all,
                memberIds = all.flatMapTo(HashSet()) { members[it.id].orEmpty() },
            )
        }.sortedBy { it.title.lowercase() }
    }

    /**
     * Makes a label. It starts on the phone; it gets a group in each other account as soon as
     * a contact saved there joins it. A label of that name already existing is fine: it's the same.
     */
    suspend fun create(title: String): Boolean = withContext(Dispatchers.IO) {
        val name = title.trim()
        if (name.isEmpty()) return@withContext false
        if (findGroups(name).isNotEmpty()) return@withContext true
        insertGroup(name, null, null) != null
    }

    /** Renames the label in every account. */
    suspend fun rename(label: Label, title: String): Boolean = withContext(Dispatchers.IO) {
        val name = title.trim()
        if (name.isEmpty()) return@withContext false
        runCatching {
            resolver.update(
                Groups.CONTENT_URI,
                ContentValues().apply { put(Groups.TITLE, name) },
                "${Groups._ID} IN (${label.groupIds.joinToString(",")})", null,
            ) > 0
        }.getOrDefault(false)
    }

    /** Deletes the label from every account. Its contacts stay; they're just no longer in it. */
    suspend fun delete(label: Label): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            resolver.delete(Groups.CONTENT_URI, "${Groups._ID} IN (${label.groupIds.joinToString(",")})", null) > 0
        }.getOrDefault(false)
    }

    /**
     * Adds contacts to the label, whatever account they're saved in: each joins through one of
     * its copies, in an account where the label already is if it can, else the label is made
     * in that copy's account. Returns how many were added.
     */
    suspend fun addMembers(label: Label, contactIds: Collection<Long>): Int = withContext(Dispatchers.IO) {
        val groups = label.groups.toMutableList()
        val writable = writableTypes()
        var added = 0
        contactIds.filter { it !in label.memberIds }.forEach { contactId ->
            val copies = rawContactsOf(contactId)
            // Copies in accounts the label is already in first, then the one holding the name,
            // then the rest; copies in read-only accounts (messaging apps…) only as a last resort.
            val ordered = copies.sortedWith(
                compareBy<RawCopy>(
                    { copy -> groups.none { sameAccount(it.accountType, it.accountName, copy.type, copy.name) } },
                    { !it.holdsName },
                    { !(it.type.isNullOrEmpty() || it.type in writable) },
                )
            )
            for (copy in ordered) {
                val group = groups.firstOrNull { sameAccount(it.accountType, it.accountName, copy.type, copy.name) }
                    ?: insertGroup(label.title, copy.type, copy.name)
                        ?.let { LabelGroup(it, copy.type, copy.name) }
                        ?.also { groups += it }
                    ?: continue
                val ok = runCatching {
                    resolver.insert(
                        Data.CONTENT_URI,
                        ContentValues().apply {
                            put(Data.RAW_CONTACT_ID, copy.id)
                            put(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
                            put(GroupMembership.GROUP_ROW_ID, group.id)
                        },
                    ) != null
                }.getOrDefault(false)
                if (ok) { added++; break }
            }
        }
        added
    }

    /** Takes the contact out of the label, in every account. */
    suspend fun removeMember(label: Label, contactId: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            resolver.delete(
                Data.CONTENT_URI,
                "${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} = ? AND ${GroupMembership.GROUP_ROW_ID} IN (${label.groupIds.joinToString(",")})",
                arrayOf(contactId.toString(), GroupMembership.CONTENT_ITEM_TYPE),
            ) > 0
        }.getOrDefault(false)
    }

    /**
     * The same labels as [groupIds], as groups of [account]: a group already there is kept, a
     * group of another account is swapped for its label's group in [account], made if needed.
     * Used when a contact saved in [account] joins labels.
     */
    suspend fun inAccount(groupIds: Collection<Long>, account: ContactAccount?): Set<Long> = withContext(Dispatchers.IO) {
        if (groupIds.isEmpty()) return@withContext emptySet()
        val type = account?.type
        val name = account?.name
        val result = HashSet<Long>()
        val known = HashMap<Long, Pair<String, LabelGroup>>()
        runCatching {
            resolver.query(
                Groups.CONTENT_URI, arrayOf(Groups._ID, Groups.TITLE, Groups.ACCOUNT_TYPE, Groups.ACCOUNT_NAME),
                "${Groups._ID} IN (${groupIds.joinToString(",")})", null, null,
            )?.use { c ->
                while (c.moveToNext()) known[c.getLong(0)] = c.getString(1).orEmpty() to LabelGroup(c.getLong(0), c.getString(2), c.getString(3))
            }
        }
        groupIds.forEach { id ->
            val (title, group) = known[id] ?: return@forEach
            if (sameAccount(group.accountType, group.accountName, type, name)) { result += id; return@forEach }
            val there = findGroups(title).firstOrNull { sameAccount(it.accountType, it.accountName, type, name) }?.id
                ?: insertGroup(title.trim(), type, name)
            there?.let { result += it }
        }
        result
    }

    private data class RawCopy(val id: Long, val type: String?, val name: String?, val holdsName: Boolean)

    private fun rawContactsOf(contactId: Long): List<RawCopy> {
        val nameRaw = resolver.query(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
            arrayOf(ContactsContract.Contacts.NAME_RAW_CONTACT_ID), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        return resolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts._ID, RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
            "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0", arrayOf(contactId.toString()), null,
        )?.use { c ->
            buildList { while (c.moveToNext()) add(RawCopy(c.getLong(0), c.getString(1), c.getString(2), c.getLong(0) == nameRaw)) }
        }.orEmpty()
    }

    /** The groups named [title] (ignoring case), in any account. */
    private fun findGroups(title: String): List<LabelGroup> {
        val key = labelKey(title)
        return runCatching {
            resolver.query(
                Groups.CONTENT_URI, arrayOf(Groups._ID, Groups.TITLE, Groups.ACCOUNT_TYPE, Groups.ACCOUNT_NAME),
                "${Groups.DELETED} = 0 AND ${Groups.SYSTEM_ID} IS NULL AND ${Groups.TITLE} IS NOT NULL", null, "${Groups._ID} ASC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) if (labelKey(c.getString(1)) == key) add(LabelGroup(c.getLong(0), c.getString(2), c.getString(3)))
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /** Makes a group named [title] in the account; its id, or null if the account refused. */
    private fun insertGroup(title: String, type: String?, name: String?): Long? = runCatching {
        resolver.insert(
            Groups.CONTENT_URI,
            ContentValues().apply {
                put(Groups.TITLE, title)
                if (type.isNullOrEmpty()) {
                    putNull(Groups.ACCOUNT_NAME); putNull(Groups.ACCOUNT_TYPE)
                } else {
                    put(Groups.ACCOUNT_NAME, name); put(Groups.ACCOUNT_TYPE, type)
                }
                put(Groups.GROUP_VISIBLE, 1)
            },
        )?.let { ContentUris.parseId(it) }
    }.getOrNull()

    /** Account types that keep changes made on the phone: on-device ones and syncing ones. */
    private fun writableTypes(): Set<String> =
        ContentResolver.getSyncAdapterTypes()
            .filter { it.authority == ContactsContract.AUTHORITY && it.supportsUploading() }
            .mapTo(HashSet()) { it.accountType } + ON_DEVICE_ACCOUNT_TYPE
}
