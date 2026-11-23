package app.people.contacts.sync

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import bundle.core.sync.AuthException
import bundle.core.sync.Crypto
import bundle.core.sync.DeviceFile
import bundle.core.sync.LocalContact
import bundle.core.sync.Nextcloud
import bundle.core.sync.OkHttpWebDav
import bundle.core.sync.Record
import bundle.core.sync.StateStore
import bundle.core.sync.SyncAbortedException
import bundle.core.sync.SyncEngine
import bundle.core.sync.SyncReport
import bundle.core.sync.SyncState
import bundle.core.sync.UnsupportedFormatException
import bundle.core.sync.Vault
import bundle.core.sync.WrongPassphraseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.SSLException

/** Seals small secrets (the sync key, the app password) with a key that never leaves the Android Keystore. */
private object KeystoreBox {
    private const val ALIAS = "bundle-sync-wrap"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun seal(plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain), Base64.NO_WRAP)
    }

    fun open(sealed: String): ByteArray {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        return cipher.doFinal(bytes, 12, bytes.size - 12)
    }

    fun forget() {
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) }
    }
}

/** Why a sync didn't happen, for the message the user sees. */
enum class SyncProblem { NotSetUp, Auth, Network, Certificate, Passphrase, Aborted, Permission, Format, Other }

/** The last sync, as shown in Settings. */
data class SyncStatus(
    val configured: Boolean,
    val server: String = "",
    val user: String = "",
    val lastAt: Long = 0,
    val report: SyncReport? = null,
    val problem: SyncProblem? = null,
    val detail: String = "",
    val running: Boolean = false,
    /** Google and other accounts' contacts are synced too, not only the phone's. */
    val allAccounts: Boolean = true,
)

/**
 * The Nextcloud sync on this phone: its settings, its secrets (sealed by the Keystore, the
 * passphrase itself is never kept), its encrypted working files, and one run of the engine.
 */
object ContactSync {
    private const val PREFS = "bundle_sync"
    private val _status = MutableStateFlow(SyncStatus(false))
    val status: StateFlow<SyncStatus> = _status

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun dir(context: Context) = File(context.applicationContext.noBackupFilesDir, "sync").apply { mkdirs() }

    fun refresh(context: Context) {
        val p = prefs(context)
        val last = p.getString("last", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        _status.value = SyncStatus(
            configured = p.contains("key"),
            server = p.getString("server", "").orEmpty(),
            user = p.getString("user", "").orEmpty(),
            lastAt = last?.optLong("at") ?: 0,
            report = last?.takeIf { it.has("created") }?.let {
                SyncReport(it.optInt("created"), it.optInt("updated"), it.optInt("deleted"), it.optInt("suggestions"), it.optInt("skipped"), it.optInt("failures"), devices = it.optInt("devices"), total = it.optInt("total"))
            },
            problem = last?.optString("problem")?.takeIf { it.isNotEmpty() }?.let { runCatching { SyncProblem.valueOf(it) }.getOrNull() },
            detail = last?.optString("detail").orEmpty(),
            running = _status.value.running,
            allAccounts = p.getBoolean("allAccounts", true),
        )
    }

    /** Whether Google and other accounts' contacts are synced too. */
    fun setAllAccounts(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("allAccounts", on).apply()
        refresh(context)
    }

    private fun deviceId(context: Context): String = prefs(context).let { p ->
        p.getString("device", null) ?: UUID.randomUUID().toString().also { p.edit().putString("device", it).apply() }
    }

    /**
     * Connects to the server with the group's [passphrase]: checks the login and the passphrase
     * (creating the group on the first phone) before anything is saved. Throws on any problem.
     */
    fun connect(context: Context, server: String, user: String, appPassword: String, passphrase: CharArray, allAccounts: Boolean = true) {
        val base = Nextcloud.davBase(server, user)
        val dav = OkHttpWebDav(base, user.trim(), appPassword)
        val key = Vault.open(dav, passphrase)
        prefs(context).edit()
            .putString("server", base).putString("user", user.trim())
            .putString("password", KeystoreBox.seal(appPassword.toByteArray()))
            .putString("key", KeystoreBox.seal(key))
            .remove("last")
            .putBoolean("allAccounts", allAccounts)
            .apply()
        key.fill(0)
        refresh(context)
    }

    /** Stops syncing on this phone and forgets its secrets. The server and the other phones keep their copy. */
    fun disconnect(context: Context) {
        prefs(context).edit().clear().apply()
        dir(context).deleteRecursively()
        KeystoreBox.forget()
        refresh(context)
    }

    /** One sync now (call it off the main thread). Records the result for Settings. */
    fun run(context: Context): SyncReport? {
        val p = prefs(context)
        if (!p.contains("key")) {
            refresh(context)
            return null
        }
        _status.value = _status.value.copy(running = true)
        val result = runCatching {
            val key = KeystoreBox.open(p.getString("key", "")!!)
            val password = String(KeystoreBox.open(p.getString("password", "")!!))
            val dav = OkHttpWebDav(p.getString("server", "")!!, p.getString("user", "")!!, password)
            SyncEngine(
                dav, RawContactStore(context, p.getBoolean("allAccounts", true)), EncryptedState(dir(context), key), key,
                deviceId(context), Build.MODEL ?: "Android",
                beforeChanges = { Backups.write(dir(context), key, it) },
            ).sync()
        }
        val last = JSONObject().put("at", System.currentTimeMillis())
        result.onSuccess { r ->
            last.put("created", r.created).put("updated", r.updated).put("deleted", r.deleted).put("suggestions", r.suggestions)
                .put("skipped", r.skippedDeletes).put("failures", r.failures).put("devices", r.devices).put("total", r.total)
                .put("detail", r.unreadable.joinToString("\n"))
        }.onFailure { e ->
            val problem = syncProblemOf(e)
            last.put("problem", problem.name).put("detail", e.message.orEmpty())
        }
        p.edit().putString("last", last.toString()).apply()
        _status.value = _status.value.copy(running = false)
        refresh(context)
        return result.getOrNull()
    }

    /** Whether a failed run is worth trying again soon (the network), or needs the user. */
    fun retryable(context: Context): Boolean = _status.value.problem.let { it == SyncProblem.Network || it == SyncProblem.Other }
}

/** The kind of problem behind [e], for the message the user sees. */
fun syncProblemOf(e: Throwable): SyncProblem = when (e) {
    is AuthException -> SyncProblem.Auth
    is SSLException -> SyncProblem.Certificate
    is WrongPassphraseException -> SyncProblem.Passphrase
    is SyncAbortedException -> SyncProblem.Aborted
    is SecurityException -> SyncProblem.Permission
    is UnsupportedFormatException -> SyncProblem.Format
    is IllegalArgumentException -> SyncProblem.Format
    is IOException -> SyncProblem.Network
    else -> SyncProblem.Other
}

/** The engine's memory, sealed with the sync key; written to a new file first, so a crash can't leave half a file. */
private class EncryptedState(private val dir: File, private val key: ByteArray) : StateStore {
    private val file = File(dir, "state.bsync")

    override fun load(deviceId: String): SyncState = runCatching {
        SyncState.fromBytes(Crypto.open(key, file.readBytes(), SyncState.LABEL)).takeIf { it.deviceId == deviceId }
    }.getOrNull() ?: SyncState(deviceId)

    override fun save(state: SyncState) {
        val tmp = File(dir, "state.tmp")
        tmp.writeBytes(Crypto.seal(key, state.toBytes(), SyncState.LABEL))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}

/** Copies of the phone's contacts taken before a sync changes them: encrypted, the 5 latest kept. */
private object Backups {
    private const val KEEP = 5
    const val LABEL = "bundle-sync backup"

    fun write(dir: File, key: ByteArray, contacts: List<LocalContact>) {
        val folder = File(dir, "backups").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val file = DeviceFile("backup", Build.MODEL ?: "Android", now, contacts.map { Record(it.handle, it.fields, now) }, emptyMap())
        File(folder, "$now.bsync").writeBytes(Crypto.seal(key, file.toBytes(), LABEL))
        folder.listFiles()?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
    }
}
