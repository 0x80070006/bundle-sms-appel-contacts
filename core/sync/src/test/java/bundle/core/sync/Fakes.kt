package bundle.core.sync

/** An in-memory WebDAV server. */
class FakeDav : WebDav {
    val files = LinkedHashMap<String, ByteArray>()
    val folders = HashSet<String>()
    var beforePutIfAbsent: (() -> Unit)? = null

    override fun ensureFolder(path: String) {
        var current = ""
        for (segment in path.split('/').filter { it.isNotEmpty() }) {
            current = if (current.isEmpty()) segment else "$current/$segment"
            folders += current
        }
    }

    override fun list(path: String): List<DavEntry> =
        files.keys.filter { it.startsWith("$path/") && '/' !in it.removePrefix("$path/") }.map { DavEntry(it.substringAfterLast('/'), false) }

    override fun get(path: String): ByteArray? = files[path]
    override fun put(path: String, data: ByteArray) { files[path] = data }
    override fun delete(path: String) { files.remove(path) }

    override fun putIfAbsent(path: String, data: ByteArray): Boolean {
        beforePutIfAbsent?.invoke()
        if (path in files) return false
        files[path] = data
        return true
    }
}

/** A phone's contacts. */
class FakeLocal : LocalStore {
    val contacts = LinkedHashMap<String, Fields>()
    private var next = 1
    var failCreate = false
    var readFails = false

    fun add(fields: Fields): String = "r${next++}".also { contacts[it] = fields }

    override fun readAll(): List<LocalContact> {
        if (readFails) throw SecurityException("no permission")
        return contacts.map { LocalContact(it.key, it.value) }
    }
    override fun create(fields: Fields): String {
        if (failCreate) throw IllegalStateException("provider said no")
        return add(fields)
    }
    override fun update(handle: String, fields: Fields) { contacts[handle] = fields }
    override fun delete(handle: String) { contacts.remove(handle) }
}

class MemoryState : StateStore {
    var saved: SyncState? = null
    override fun load(deviceId: String): SyncState = saved ?: SyncState(deviceId)
    override fun save(state: SyncState) { saved = SyncState.fromBytes(state.toBytes()) }
}

fun person(first: String? = null, last: String? = null, vararg phones: String, emails: List<String> = emptyList(), note: String? = null, starred: Boolean = false): Fields =
    Fields.of(
        listOfNotNull(first?.let { "first" to it }, last?.let { "last" to it }, note?.let { "note" to it }, if (starred) "starred" to "1" else null).toMap(),
        mapOf(
            "phones" to phones.map { Fields.item("phones", it, 2) },
            "emails" to emails.map { Fields.item("emails", it, 1) },
        ),
    )

/** One phone: its contacts, its state, and a engine on the shared server. */
class Phone(val id: String, val dav: FakeDav, val key: ByteArray, var now: Long = 1_000) {
    val local = FakeLocal()
    val state = MemoryState()
    var backups = 0
    fun sync(): SyncReport = SyncEngine(
        dav, local, state, key, id, "phone $id", { now }, beforeChanges = { backups++ },
    ).sync().also { now += 1_000 }
}

val TEST_KEY = ByteArray(32) { it.toByte() }
