package app.people.contacts.sync

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.SipAddress
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import app.people.contacts.data.ON_DEVICE_ACCOUNT_TYPE
import bundle.core.sync.Fields
import bundle.core.sync.Item
import bundle.core.sync.LocalContact
import bundle.core.sync.LocalStore

/**
 * The contacts the sync works on, at the level Android stores them (one raw contact each): the
 * phone's own (no account, this app's on-device accounts, Samsung's "Phone"), and with
 * [allAccounts] also Google's and those of any other account whose contacts can be changed
 * (a CardDAV account, for example). Read-only contacts that messaging apps add (WhatsApp,
 * Signal…) are always left out. New contacts from other phones are saved on the phone.
 *
 * Reading throws if contacts access is missing, so the sync stops instead of believing the
 * contacts are gone.
 */
class RawContactStore(private val context: Context, private val allAccounts: Boolean = true) : LocalStore {
    private val resolver get() = context.contentResolver

    /** Account types in scope besides "no account". */
    private val types: List<String> by lazy {
        val local = listOf(ON_DEVICE_ACCOUNT_TYPE, "vnd.sec.contact.phone", "com.android.localphone", "com.local.contacts")
        if (!allAccounts) return@lazy local
        // Accounts whose contacts sync both ways: Google, CardDAV… (not apps that only add read-only contacts).
        val writable = runCatching {
            ContentResolver.getSyncAdapterTypes()
                .filter { it.authority == ContactsContract.AUTHORITY && it.supportsUploading() && it.isUserVisible }
                .map { it.accountType }
        }.getOrDefault(emptyList())
        (local + writable + "com.google").distinct()
    }

    private val scope: String
        get() = "(${RawContacts.ACCOUNT_TYPE} IS NULL OR ${RawContacts.ACCOUNT_TYPE} = '' OR ${RawContacts.ACCOUNT_TYPE} IN (${types.joinToString { "?" }}))" +
            " AND ${RawContacts.DELETED} = 0 AND ${RawContacts.RAW_CONTACT_IS_READ_ONLY} = 0"

    override fun readAll(): List<LocalContact> {
        val starred = HashMap<Long, Boolean>()
        resolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts._ID, RawContacts.STARRED),
            scope, types.toTypedArray(), null,
        )?.use { c -> while (c.moveToNext()) starred[c.getLong(0)] = c.getInt(1) == 1 }
            ?: throw SecurityException("contacts are not readable")
        val groups = groupTitles()
        val scalars = HashMap<Long, MutableMap<String, String>>()
        val sets = HashMap<Long, MutableMap<String, MutableList<Item>>>()
        fun put(id: Long, name: String, value: String?) {
            if (!value.isNullOrBlank()) scalars.getOrPut(id) { HashMap() }.putIfAbsent(name, value)
        }
        fun add(id: Long, kind: String, value: String?, type: Int = 0, label: String? = null) {
            if (!value.isNullOrBlank()) sets.getOrPut(id) { HashMap() }.getOrPut(kind) { ArrayList() } += Fields.item(kind, value, type, label)
        }
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5, Data.DATA6, Data.DATA7, Data.DATA8, Data.DATA9),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (id !in starred) continue
                fun s(i: Int): String? = c.getString(i)
                fun type(i: Int): Int = if (c.isNull(i)) 0 else c.getInt(i)
                when (c.getString(1)) {
                    StructuredName.CONTENT_ITEM_TYPE -> {
                        put(id, "first", s(3)); put(id, "last", s(4)); put(id, "prefix", s(5)); put(id, "middle", s(6)); put(id, "suffix", s(7))
                        put(id, "phoneticFirst", s(8)); put(id, "phoneticMiddle", s(9)); put(id, "phoneticLast", s(10))
                        // A name saved as a single display name, with no parts.
                        if (s(3).isNullOrBlank() && s(4).isNullOrBlank()) put(id, "first", s(2))
                    }
                    Nickname.CONTENT_ITEM_TYPE -> put(id, "nickname", s(2))
                    Organization.CONTENT_ITEM_TYPE -> { put(id, "company", s(2)); put(id, "title", s(5)) }
                    Note.CONTENT_ITEM_TYPE -> put(id, "note", s(2))
                    Phone.CONTENT_ITEM_TYPE -> add(id, "phones", s(2), type(3), s(4))
                    Email.CONTENT_ITEM_TYPE -> add(id, "emails", s(2), type(3), s(4))
                    StructuredPostal.CONTENT_ITEM_TYPE -> add(id, "addresses", s(2), type(3), s(4))
                    Event.CONTENT_ITEM_TYPE -> add(id, "events", s(2), type(3), s(4))
                    Website.CONTENT_ITEM_TYPE -> add(id, "websites", s(2), type(3))
                    Relation.CONTENT_ITEM_TYPE -> add(id, "relations", s(2), type(3), s(4))
                    SipAddress.CONTENT_ITEM_TYPE -> add(id, "sips", s(2), type(3), s(4))
                    Im.CONTENT_ITEM_TYPE -> add(id, "chats", s(2), type(6), s(7))
                    GroupMembership.CONTENT_ITEM_TYPE -> s(2)?.toLongOrNull()?.let { groups[it] }?.let { add(id, "labels", it) }
                }
            }
        } ?: throw SecurityException("contacts are not readable")
        return starred.map { (id, star) ->
            val sc = scalars[id].orEmpty().toMutableMap()
            if (star) sc["starred"] = "1"
            LocalContact(id.toString(), Fields.of(sc, sets[id].orEmpty()))
        }
    }

    override fun create(fields: Fields): String {
        val ops = ArrayList<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_TYPE, null).withValue(RawContacts.ACCOUNT_NAME, null)
            .withValue(RawContacts.STARRED, if (fields.scalar("starred") == "1") 1 else 0)
            .build()
        rows(fields, null, null to null, ops)
        val result = resolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(ops))
        return ContentUris.parseId(result[0].uri ?: error("no raw contact made")).toString()
    }

    override fun update(handle: String, fields: Fields) {
        val id = handle.toLong()
        val ops = ArrayList<ContentProviderOperation>()
        // Replace every kind of detail the sync manages; photos and anything else stay untouched. Of
        // the group memberships, only labels: a Google contact must stay in "My Contacts".
        ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
            .withSelection("${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} IN (${DETAILS.joinToString { "?" }})", arrayOf(id.toString()) + DETAILS)
            .build()
        val labelIds = groupTitles().keys
        if (labelIds.isNotEmpty()) {
            ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection(
                    "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ? AND ${GroupMembership.GROUP_ROW_ID} IN (${labelIds.joinToString()})",
                    arrayOf(id.toString(), GroupMembership.CONTENT_ITEM_TYPE),
                )
                .build()
        }
        ops += ContentProviderOperation.newUpdate(ContentUris.withAppendedId(RawContacts.CONTENT_URI, id))
            .withValue(RawContacts.STARRED, if (fields.scalar("starred") == "1") 1 else 0).build()
        rows(fields, id, accountOf(id), ops)
        // One batch: the contact changes completely or not at all.
        resolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    override fun delete(handle: String) {
        resolver.delete(ContentUris.withAppendedId(RawContacts.CONTENT_URI, handle.toLong()), null, null)
    }

    /** The data rows for [fields], on raw contact [id] or on the one made by the batch's first operation. */
    private fun rows(fields: Fields, id: Long?, account: Pair<String?, String?>, ops: MutableList<ContentProviderOperation>) {
        fun row(mime: String, vararg values: Pair<String, Any?>) {
            val b = ContentProviderOperation.newInsert(Data.CONTENT_URI)
            if (id == null) b.withValueBackReference(Data.RAW_CONTACT_ID, 0) else b.withValue(Data.RAW_CONTACT_ID, id)
            b.withValue(Data.MIMETYPE, mime)
            values.forEach { (k, v) -> b.withValue(k, v) }
            ops += b.build()
        }
        fun f(name: String) = fields.scalar(name).ifEmpty { null }
        if (listOf("prefix", "first", "middle", "last", "suffix", "phoneticFirst", "phoneticMiddle", "phoneticLast").any { f(it) != null }) {
            row(
                StructuredName.CONTENT_ITEM_TYPE,
                StructuredName.GIVEN_NAME to f("first"), StructuredName.FAMILY_NAME to f("last"),
                StructuredName.PREFIX to f("prefix"), StructuredName.MIDDLE_NAME to f("middle"), StructuredName.SUFFIX to f("suffix"),
                StructuredName.PHONETIC_GIVEN_NAME to f("phoneticFirst"), StructuredName.PHONETIC_MIDDLE_NAME to f("phoneticMiddle"),
                StructuredName.PHONETIC_FAMILY_NAME to f("phoneticLast"),
            )
        }
        f("nickname")?.let { row(Nickname.CONTENT_ITEM_TYPE, Nickname.NAME to it) }
        if (f("company") != null || f("title") != null) row(Organization.CONTENT_ITEM_TYPE, Organization.COMPANY to f("company"), Organization.TITLE to f("title"))
        f("note")?.let { row(Note.CONTENT_ITEM_TYPE, Note.NOTE to it) }
        fun items(kind: String) = fields.items(kind).sortedBy { it.key }
        items("phones").forEach { row(Phone.CONTENT_ITEM_TYPE, Phone.NUMBER to it.value, Phone.TYPE to it.type, Phone.LABEL to it.label) }
        items("emails").forEach { row(Email.CONTENT_ITEM_TYPE, Email.ADDRESS to it.value, Email.TYPE to it.type, Email.LABEL to it.label) }
        items("addresses").forEach { row(StructuredPostal.CONTENT_ITEM_TYPE, StructuredPostal.FORMATTED_ADDRESS to it.value, StructuredPostal.TYPE to it.type, StructuredPostal.LABEL to it.label) }
        items("events").forEach { row(Event.CONTENT_ITEM_TYPE, Event.START_DATE to it.value, Event.TYPE to it.type, Event.LABEL to it.label) }
        items("websites").forEach { row(Website.CONTENT_ITEM_TYPE, Website.URL to it.value, Website.TYPE to it.type) }
        items("relations").forEach { row(Relation.CONTENT_ITEM_TYPE, Relation.NAME to it.value, Relation.TYPE to it.type, Relation.LABEL to it.label) }
        items("sips").forEach { row(SipAddress.CONTENT_ITEM_TYPE, SipAddress.SIP_ADDRESS to it.value, SipAddress.TYPE to it.type, SipAddress.LABEL to it.label) }
        items("chats").forEach { row(Im.CONTENT_ITEM_TYPE, Im.DATA to it.value, Im.TYPE to Im.TYPE_OTHER, Im.PROTOCOL to it.type, Im.CUSTOM_PROTOCOL to it.label) }
        items("labels").forEach { label -> row(GroupMembership.CONTENT_ITEM_TYPE, GroupMembership.GROUP_ROW_ID to groupId(label.value, account)) }
    }

    /** Label titles by group id (system groups such as "My Contacts" aren't labels). */
    private fun groupTitles(): Map<Long, String> {
        val out = HashMap<Long, String>()
        resolver.query(
            Groups.CONTENT_URI, arrayOf(Groups._ID, Groups.TITLE),
            "${Groups.DELETED} = 0 AND ${Groups.SYSTEM_ID} IS NULL AND ${Groups.TITLE} IS NOT NULL", null, null,
        )?.use { c -> while (c.moveToNext()) out[c.getLong(0)] = c.getString(1) }
        return out
    }

    /** (account name, account type) of raw contact [id]. */
    private fun accountOf(id: Long): Pair<String?, String?> =
        resolver.query(ContentUris.withAppendedId(RawContacts.CONTENT_URI, id), arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) to c.getString(1) else null } ?: (null to null)

    /** The label called [title] in [account] (a label belongs to one account), made if it doesn't exist yet. */
    private fun groupId(title: String, account: Pair<String?, String?>): Long {
        val (name, type) = account
        val (where, args) = if (type.isNullOrEmpty()) {
            "${Groups.TITLE} = ? AND ${Groups.DELETED} = 0 AND (${Groups.ACCOUNT_TYPE} IS NULL OR ${Groups.ACCOUNT_TYPE} = '')" to arrayOf(title)
        } else {
            "${Groups.TITLE} = ? AND ${Groups.DELETED} = 0 AND ${Groups.ACCOUNT_TYPE} = ? AND ${Groups.ACCOUNT_NAME} = ?" to arrayOf(title, type, name.orEmpty())
        }
        resolver.query(Groups.CONTENT_URI, arrayOf(Groups._ID), where, args, null)?.use { c -> if (c.moveToFirst()) return c.getLong(0) }
        val uri = resolver.insert(
            Groups.CONTENT_URI,
            ContentValues().apply {
                put(Groups.TITLE, title)
                if (type.isNullOrEmpty()) { putNull(Groups.ACCOUNT_NAME); putNull(Groups.ACCOUNT_TYPE) } else { put(Groups.ACCOUNT_NAME, name); put(Groups.ACCOUNT_TYPE, type) }
                put(Groups.GROUP_VISIBLE, 1)
            },
        ) ?: error("label not made")
        return ContentUris.parseId(uri)
    }

    private companion object {
        /** The details the sync rewrites (group memberships are handled apart). */
        val DETAILS = arrayOf(
            StructuredName.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE, Organization.CONTENT_ITEM_TYPE, Note.CONTENT_ITEM_TYPE,
            Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, StructuredPostal.CONTENT_ITEM_TYPE, Event.CONTENT_ITEM_TYPE,
            Website.CONTENT_ITEM_TYPE, Relation.CONTENT_ITEM_TYPE, SipAddress.CONTENT_ITEM_TYPE, Im.CONTENT_ITEM_TYPE,
        )
    }
}
