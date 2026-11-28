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
    /** When this phone last kept a full copy of its contacts on the server. */
    val lastBackupAt: Long = 0,
    /** Bundle: when this phone last synced (0 = never), to warn after a long absence. */
    val lastSyncAt: Long = 0,
    /** Bundle: the newest file seen from each other phone (device id -> writtenAt), so an older copy put back on the server is refused. */
    val seen: Map<String, Long> = emptyMap(),
) {
    fun toBytes(): ByteArray = JSONObject()
        .put("v", 1).put("device", deviceId).put("backupAt", lastBackupAt)
        .put("links", JSONObject(links))
        .put("base", JSONObject(base.mapValues { (_, b) -> JSONObject().put("at", b.at).put("f", b.fields.toJson()) }))
        .put("tombstones", JSONObject(tombstones))
        .put("syncAt", lastSyncAt)
        .put("seen", JSONObject(seen))
        .toString().toByteArray(Charsets.UTF_8)

    companion object {
        const val LABEL = "bundle-sync state"

        fun fromBytes(bytes: ByteArray): SyncState {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            val links = o.optJSONObject("links") ?: JSONObject()
            val base = o.optJSONObject("base") ?: JSONObject()
            val tombs = o.optJSONObject("tombstones") ?: JSONObject()
            val seen = o.optJSONObject("seen") ?: JSONObject()
            return SyncState(
                o.getString("device"),
                links.keys().asSequence().associateWith { links.getString(it) },
                base.keys().asSequence().associateWith { Base(fieldsFromJson(base.getJSONObject(it).getJSONObject("f")), base.getJSONObject(it).optLong("at")) },
                tombs.keys().asSequence().associateWith { tombs.getLong(it) },
                o.optLong("backupAt"),
                o.optLong("syncAt"),
                seen.keys().asSequence().associateWith { seen.getLong(it) },
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
    /** Contacts that are another copy of one already synced (the same person in two accounts): not published twice. */
    val copies: Int = 0,
    /** Whether a full encrypted copy of the contacts was kept on the server this time. */
    val backedUp: Boolean = false,
    /** Bundle: this phone had not synced for more than 90 days; contacts deleted elsewhere meanwhile may come back. */
    val longAbsence: Boolean = false,
    /** Bundle: other phones' files older than one already seen from them (an old copy put back), ignored. */
    val replayed: Int = 0,
) {
    val changedHere: Boolean get() = created + updated + deleted > 0
}

/**
 * A sync that must not go on: nothing has been changed. [deletions] is how many contacts would
 * have been deleted (0 when it is not about deletions); the user can confirm them (see SyncEngine).
 */
class SyncAbortedException(message: String, val deletions: Int = 0) : Exception(message)

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
    /** Also keep a full encrypted copy of the contacts on the server (daily, and after changes), the last [KEEP_BACKUPS]. */
    private val remoteBackups: Boolean = true,
    /**
     * Bundle: the user saw how many contacts this run deletes (more than the safe limit) and
     * confirmed. Only for one run: the next ones are guarded again.
     */
    private val confirmDeletions: Boolean = false,
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
        val seen = st.seen.toMutableMap()
        var replayed = 0
        var devices = 0
        for (entry in dav.list("$root/devices")) {
            if (entry.isFolder || !entry.name.endsWith(EXT) || entry.name == "$deviceId$EXT") continue
            val bytes = dav.get("$root/devices/${entry.name}") ?: continue
            val owner = entry.name.removeSuffix(EXT)
            val file = try {
                // Bundle: a file is sealed for the phone that wrote it, so it cannot be passed off as
                // another phone's; files from earlier versions used the shared label.
                val plain = try {
                    Crypto.open(key, bytes, DeviceFile.labelFor(owner))
                } catch (e: WrongPassphraseException) {
                    Crypto.open(key, bytes, DeviceFile.LABEL)
                }
                DeviceFile.fromBytes(plain).also { f ->
                    if (f.deviceId != owner) throw UnsupportedFormatException("file of ${f.deviceId} stored as $owner")
                }
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
            // Bundle: an older copy of a phone's file than one already seen is a replay: ignore it.
            val lastSeen = seen[file.deviceId] ?: 0L
            if (file.writtenAt < lastSeen) {
                replayed++
                unreadable += "${entry.name}: older than the copy already seen"
                continue
            }
            seen[file.deviceId] = file.writtenAt
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
        if (!confirmDeletions && vanished.size > maxOf(MASS, st.links.size / 5)) {
            throw SyncAbortedException("${vanished.size} contacts of ${st.links.size} are gone from the phone: not trusting that", vanished.size)
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
        if (!confirmDeletions && deletes.size > maxOf(MASS, locals.size / 5)) {
            skipped = deletes.size
            deletes.clear()
        }

        // Apply.
        if (creates.isNotEmpty() || updates.isNotEmpty() || deletes.isNotEmpty()) beforeChanges(locals)
        var created = 0
        var updated = 0
        var deleted = 0
        var failures = 0
        val createdFields = HashMap<String, Fields>()
        for (record in creates) {
            try {
                val handle = local.create(record.fields)
                createdFields[handle] = record.fields
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

        // New contacts of this phone get their identity, unless they're another copy of a contact
        // already synced (the same person saved in two accounts, such as the phone and Google, which
        // Android shows as one): publishing both would duplicate them on the other phones.
        val index = HashMap<String, MutableList<Fields>>()
        fun keysOf(f: Fields) = f.keys("phones").map { "p:$it" } + f.keys("emails").map { "e:$it" }
        fun indexFields(f: Fields) = keysOf(f).forEach { index.getOrPut(it) { ArrayList() } += f }
        links.keys.forEach { h -> (byHandle[h]?.fields ?: createdFields[h])?.let(::indexFields) }
        var copies = 0
        for (c in unlinked) {
            if (!c.fields.hasContent) continue
            if (keysOf(c.fields).flatMap { index[it].orEmpty() }.any { Merge.match(it, c.fields) == Match.Same }) {
                copies++
                continue
            }
            val uid = UUID.randomUUID().toString()
            links[c.handle] = uid
            publishedAt[uid] = now
            indexFields(c.fields)
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
        val newState = SyncState(deviceId, links.filterKeys { it in afterHandles }, newBase, liveTombs, st.lastBackupAt, now, seen)
        state.save(newState)

        dav.put("$root/devices/$deviceId$EXT", Crypto.seal(key, DeviceFile(deviceId, deviceName, now, records, liveTombs).toBytes(), DeviceFile.labelFor(deviceId)))

        // The second copy: every contact of the phone, in one encrypted file, kept apart from the
        // sync so it can be restored even if the sync went wrong. A failure here doesn't fail the sync.
        var backedUp = false
        if (remoteBackups && (st.lastBackupAt == 0L || now - st.lastBackupAt >= DAY_MS || created + updated + deleted > 0 || newBase != st.base)) {
            backedUp = runCatching {
                val folder = "$root/backups/$deviceId"
                dav.ensureFolder(folder)
                val snapshot = DeviceFile(deviceId, deviceName, now, after.map { Record(links[it.handle] ?: "local-${it.handle}", it.fields, now) }, emptyMap())
                dav.put("$folder/$now$EXT", Crypto.seal(key, snapshot.toBytes(), BACKUP_LABEL))
                val names = dav.list(folder).filter { !it.isFolder && it.name.endsWith(EXT) }.map { it.name }
                val keep = keptBackups(names, now)
                names.filter { it !in keep }.forEach { dav.delete("$folder/$it") }
            }.isSuccess
            if (backedUp) state.save(newState.copy(lastBackupAt = now))
        }
        val longAbsence = st.lastSyncAt > 0 && now - st.lastSyncAt > TOMBSTONE_MS
        return SyncReport(created, updated, deleted, suggestions, skipped, failures, unreadable, devices + 1, after.size, copies, backedUp, longAbsence, replayed)
    }

    companion object {
        const val EXT = ".bsync"
        const val BACKUP_LABEL = "bundle-sync backup"

        /** Full copies kept on the server, per phone: the latest ones... */
        const val KEEP_BACKUPS = 10

        /** ...plus the newest copy of each of the last [KEEP_DAYS] days (Bundle). */
        const val KEEP_DAYS = 7

        /** Which backup files to keep: the [KEEP_BACKUPS] newest, and the newest of each of the last [KEEP_DAYS] days. */
        fun keptBackups(names: List<String>, now: Long): Set<String> {
            val byTime = names.mapNotNull { n -> n.removeSuffix(EXT).toLongOrNull()?.let { it to n } }.sortedByDescending { it.first }
            val keep = byTime.take(KEEP_BACKUPS).mapTo(HashSet()) { it.second }
            byTime.filter { now - it.first < KEEP_DAYS * DAY_MS }
                .groupBy { it.first / DAY_MS }
                .values.forEach { day -> keep += day.first().second }
            return keep
        }
        private const val DAY_MS = 24L * 60 * 60 * 1000

        /** Changes beyond this many (and beyond a fifth of the contacts) are refused as probably a mistake. */
        const val MASS = 10
        const val TOMBSTONE_MS = 90L * 24 * 60 * 60 * 1000
    }
}
