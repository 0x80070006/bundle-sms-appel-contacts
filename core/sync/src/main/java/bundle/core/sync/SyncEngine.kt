package bundle.core.sync

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A contact of the phone, as the sync sees it. [handle] identifies it on this phone (a raw contact id). */
data class LocalContact(val handle: String, val fields: Fields)

/** The phone's contacts, read and changed by the app. Every method may throw; the engine copes. */
interface LocalStore {
    fun readAll(): List<LocalContact>

    /** Adds a contact and returns its handle. */
    fun create(fields: Fields): String
    fun update(handle: String, fields: Fields)
    fun delete(handle: String)
}

/** What the last sync agreed on for a contact: [fields] at [at]. */
data class Base(val fields: Fields, val at: Long)

/** The sync's memory on this phone: which local contact is which [uid], and what was agreed last. */
data class SyncState(
    val deviceId: String,
    val links: Map<String, String> = emptyMap(),
    val base: Map<String, Base> = emptyMap(),
    val tombstones: Map<String, Long> = emptyMap(),
) {
    fun toBytes(): ByteArray = JSONObject()
        .put("v", 1).put("device", deviceId)
        .put("links", JSONObject(links))
        .put("base", JSONObject(base.mapValues { (_, b) -> JSONObject().put("at", b.at).put("f", b.fields.toJson()) }))
        .put("tombstones", JSONObject(tombstones))
        .toString().toByteArray(Charsets.UTF_8)

    companion object {
        const val LABEL = "bundle-sync state"

        fun fromBytes(bytes: ByteArray): SyncState {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            val links = o.optJSONObject("links") ?: JSONObject()
            val base = o.optJSONObject("base") ?: JSONObject()
            val tombs = o.optJSONObject("tombstones") ?: JSONObject()
            return SyncState(
                o.getString("device"),
                links.keys().asSequence().associateWith { links.getString(it) },
                base.keys().asSequence().associateWith { Base(fieldsFromJson(base.getJSONObject(it).getJSONObject("f")), base.getJSONObject(it).optLong("at")) },
                tombs.keys().asSequence().associateWith { tombs.getLong(it) },
            )
        }
    }
}

/** Where the sync keeps its memory (encrypted by the app). */
interface StateStore {
    /** The saved state, or a fresh one for [deviceId] the first time. */
    fun load(deviceId: String): SyncState
    fun save(state: SyncState)
}

/** What a sync did, for the user. */
data class SyncReport(
    val created: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    /** Contacts that look like one already on the phone (same number or email, other name): kept apart, for the merge screen. */
    val suggestions: Int = 0,
    /** Deletions that were not applied because there were too many to be safe. */
    val skippedDeletes: Int = 0,
    /** Changes that failed on this phone; they're tried again next time. */
    val failures: Int = 0,
    /** Other phones' files that could not be read, with the reason. */
    val unreadable: List<String> = emptyList(),
    val devices: Int = 0,
    val total: Int = 0,
) {
    val changedHere: Boolean get() = created + updated + deleted > 0
}

/** A sync that must not go on: nothing has been changed. */
class SyncAbortedException(message: String) : Exception(message)

/**
 * One sync run, in the order that keeps contacts safe:
 *
 * 1. read the other phones' files and this phone's contacts;
 * 2. work out every change **before** touching anything, link what's the same person, and refuse
 *    mass deletions or a suspiciously empty contact list;
 * 3. keep a copy of the phone's contacts ([beforeChanges]), then apply the changes;
 * 4. save the state, then publish this phone's file (the only file it writes).
 */
class SyncEngine(
    private val dav: WebDav,
    private val local: LocalStore,
    private val state: StateStore,
    private val key: ByteArray,
    private val deviceId: String,
    private val deviceName: String,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Called with the phone's contacts just before the first change is applied. */
    private val beforeChanges: (List<LocalContact>) -> Unit = {},
    private val root: String = Vault.ROOT,
) {
    private data class Update(val handle: String, val uid: String, val fields: Fields)

    fun sync(): SyncReport {
        val now = clock()
        dav.ensureFolder("$root/devices")
        val st = state.load(deviceId)
        val locals = local.readAll()
        val byHandle = locals.associateBy { it.handle }

        // The other phones.
        val remote = HashMap<String, Record>()
        val remoteTombs = HashMap<String, Long>()
        val unreadable = ArrayList<String>()
        var devices = 0
        for (entry in dav.list("$root/devices")) {
            if (entry.isFolder || !entry.name.endsWith(EXT) || entry.name == "$deviceId$EXT") continue
            val bytes = dav.get("$root/devices/${entry.name}") ?: continue
            val file = try {
                DeviceFile.fromBytes(Crypto.open(key, bytes, DeviceFile.LABEL))
            } catch (e: WrongPassphraseException) {
                unreadable += "${entry.name}: damaged or encrypted with another passphrase"
                continue
            } catch (e: UnsupportedFormatException) {
                unreadable += "${entry.name}: ${e.message}"
                continue
            } catch (e: Exception) {
                unreadable += "${entry.name}: unreadable"
                continue
            }
            devices++
            file.records.forEach { r -> remote.merge(r.uid, r) { a, b -> if (b.updatedAt > a.updatedAt) b else a } }
            file.tombstones.forEach { (uid, at) -> remoteTombs.merge(uid, at) { a, b -> maxOf(a, b) } }
        }

        // What this phone had that is gone now: the user deleted it. If a lot is gone at once, the
        // contacts were probably not readable: stop rather than tell every other phone to delete.
        val links = st.links.toMutableMap()
        val base = st.base.toMutableMap()
        val tombs = st.tombstones.toMutableMap()
        val vanished = links.filterKeys { it !in byHandle }
        if (vanished.size > maxOf(MASS, st.links.size / 5)) {
            throw SyncAbortedException("${vanished.size} contacts of ${st.links.size} are gone from the phone: not trusting that")
        }
        vanished.forEach { (handle, uid) -> links.remove(handle); base.remove(uid); tombs[uid] = now }

        val uidToHandle = links.entries.associate { (h, u) -> u to h }.toMutableMap()
        val unlinked = locals.filter { it.handle !in links }.toMutableList()

        // The plan.
        val creates = ArrayList<Record>()
        val updates = ArrayList<Update>()
        val deletes = ArrayList<String>()
        val publishedAt = HashMap<String, Long>()
        var suggestions = 0

        // Delete here only what has not been touched since the last sync; otherwise keep it (and
        // so bring it back on the phone that deleted it).
        fun applyDeletion(uid: String, handle: String, tomb: Long) {
            val l = byHandle.getValue(handle).fields
            val b = base[uid]
            if (b != null && b.fields == l && tomb >= b.at) {
                deletes += uid
            } else {
                tombs.remove(uid)
                publishedAt[uid] = now
            }
        }

        for ((uid, record) in remote) {
            if (!record.fields.hasContent) continue
            val tomb = maxOf(remoteTombs[uid] ?: 0L, tombs[uid] ?: 0L)
            val handle = uidToHandle[uid]
            // A deletion newer than the contact's last change wins; at a tie, the contact does.
            if (tomb > 0 && tomb > record.updatedAt) {
                if (handle != null) applyDeletion(uid, handle, tomb)
                continue
            }
            if (handle != null) {
                val l = byHandle.getValue(handle).fields
                val b = base[uid]
                val localChanged = b == null || l != b.fields
                val remoteChanged = b == null || record.fields != b.fields
                if (!localChanged && !remoteChanged) continue
                val merged = Merge.threeWay(b?.fields, l, record.fields, if (localChanged) now else b!!.at, record.updatedAt)
                if (merged != l) updates += Update(handle, uid, merged)
                publishedAt[uid] = when {
                    b != null && merged == b.fields -> b.at
                    merged == record.fields -> record.updatedAt
                    else -> now
                }
            } else {
                val twin = unlinked.firstOrNull { Merge.match(it.fields, record.fields) == Match.Same }
                if (twin != null) {
                    unlinked.remove(twin)
                    links[twin.handle] = uid
                    uidToHandle[uid] = twin.handle
                    val merged = Merge.threeWay(null, twin.fields, record.fields, now, record.updatedAt)
                    if (merged != twin.fields) updates += Update(twin.handle, uid, merged)
                    publishedAt[uid] = if (merged == record.fields) record.updatedAt else now
                } else {
                    if (unlinked.any { Merge.match(it.fields, record.fields) == Match.Similar }) suggestions++
                    creates += record
                }
            }
        }

        // Deletions with no copy of the contact left in any file.
        for ((uid, at) in remoteTombs) {
            if (uid in remote) continue
            uidToHandle[uid]?.let { applyDeletion(uid, it, maxOf(at, tombs[uid] ?: 0L)) }
        }

        // Too many deletions at once is not a sync, it's an accident (or an attack).
        var skipped = 0
        if (deletes.size > maxOf(MASS, locals.size / 5)) {
            skipped = deletes.size
            deletes.clear()
        }

        // Apply.
        if (creates.isNotEmpty() || updates.isNotEmpty() || deletes.isNotEmpty()) beforeChanges(locals)
        var created = 0
        var updated = 0
        var deleted = 0
        var failures = 0
        for (record in creates) {
            try {
                val handle = local.create(record.fields)
                links[handle] = record.uid
                uidToHandle[record.uid] = handle
                publishedAt[record.uid] = record.updatedAt
                created++
            } catch (e: Exception) {
                failures++
            }
        }
        for (u in updates) {
            try {
                local.update(u.handle, u.fields)
                updated++
            } catch (e: Exception) {
                failures++
            }
        }
        for (uid in deletes) {
            val handle = uidToHandle[uid] ?: continue
            try {
                local.delete(handle)
                links.remove(handle)
                base.remove(uid)
                tombs[uid] = maxOf(remoteTombs[uid] ?: 0L, tombs[uid] ?: 0L)
                deleted++
            } catch (e: Exception) {
                failures++
            }
        }

        // New contacts of this phone get their identity.
        unlinked.filter { it.fields.hasContent }.forEach { c ->
            val uid = UUID.randomUUID().toString()
            links[c.handle] = uid
            publishedAt[uid] = now
        }

        // What the provider holds now is the new agreed state (it may normalise values), so read again.
        val after = local.readAll()
        val newBase = HashMap<String, Base>()
        val records = ArrayList<Record>()
        for (c in after) {
            val uid = links[c.handle] ?: continue
            val previous = base[uid]
            val at = publishedAt[uid] ?: when {
                previous == null -> now
                previous.fields == c.fields -> previous.at
                else -> now
            }
            newBase[uid] = Base(c.fields, at)
            records += Record(uid, c.fields, at)
        }
        val liveTombs = tombs.filterValues { now - it < TOMBSTONE_MS }.filterKeys { it !in newBase }
        val afterHandles = after.mapTo(HashSet()) { it.handle }
        val newState = SyncState(deviceId, links.filterKeys { it in afterHandles }, newBase, liveTombs)
        state.save(newState)

        dav.put("$root/devices/$deviceId$EXT", Crypto.seal(key, DeviceFile(deviceId, deviceName, now, records, liveTombs).toBytes(), DeviceFile.LABEL))
        return SyncReport(created, updated, deleted, suggestions, skipped, failures, unreadable, devices + 1, after.size)
    }

    companion object {
        const val EXT = ".bsync"

        /** Changes beyond this many (and beyond a fifth of the contacts) are refused as probably a mistake. */
        const val MASS = 10
        private const val TOMBSTONE_MS = 90L * 24 * 60 * 60 * 1000
    }
}
