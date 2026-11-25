package bundle.core.appearance

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A release on GitHub with this app's APK. */
data class Release(
    val tag: String,
    val version: String,
    val prerelease: Boolean,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    /** The APK's SHA-256 from the release's SHA256SUMS.txt, when there is one. */
    val sha256: String?,
)

/** Where an update is at, for the screen. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val latest: String) : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val fraction: Float) : UpdateState
    data object Installing : UpdateState
    /** Android needs the user to allow this app to install apps first. */
    data class NeedsPermission(val release: Release) : UpdateState
    data class Failed(val reason: UpdateFailure, val detail: String = "") : UpdateState
}

enum class UpdateFailure { Network, NoApk, Checksum, Install, Signature }

/**
 * Updates the app from the project's GitHub releases: finds the latest release (optionally a beta),
 * compares it with the version installed, downloads this app's APK, checks it against the
 * release's SHA-256 sums, and installs it through Android's package installer. Android itself
 * refuses an APK not signed with the same key as the installed app. Once updated, the app opens
 * again by itself ([UpdatedReceiver]).
 */
object Updater {
    const val REPO = "0x80070006/bundle-sms-appel-contacts"
    private const val API = "https://api.github.com/repos/$REPO"
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    fun set(state: UpdateState) { _state.value = state }

    /** The bundle version this build was made as (the release it belongs to), "0" if unknown. */
    fun installedVersion(context: Context): String = runCatching {
        context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            .metaData?.get("bundle.version")?.toString()
    }.getOrNull() ?: "0"

    /** This app's file in a release. */
    fun assetName(context: Context): String = when {
        ".messages" in context.packageName -> "SMS.apk"
        ".phone" in context.packageName -> "Appel.apk"
        else -> "Contacts.apk"
    }

    /** The newest release (betas too when [beta]), or null if there's none with this app's APK. Network: call off the main thread. */
    fun fetchLatest(context: Context, beta: Boolean): Release? {
        val releases: List<JSONObject> = if (beta) {
            JSONArray(get("$API/releases?per_page=15")).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
                .filter { !it.optBoolean("draft") }
        } else {
            listOf(JSONObject(get("$API/releases/latest")))
        }
        val asset = assetName(context)
        val release = releases.mapNotNull { r ->
            val assets = r.optJSONArray("assets") ?: return@mapNotNull null
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name") == asset } ?: return@mapNotNull null
            val sums = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name") == "SHA256SUMS.txt" }
            r to Pair(apk, sums)
        }.maxWithOrNull { a, b -> compareVersions(versionOf(a.first.optString("tag_name")), versionOf(b.first.optString("tag_name"))) }
            ?: return null
        val (r, files) = release
        val (apk, sums) = files
        val sha = sums?.let { s ->
            runCatching {
                get(s.getString("browser_download_url")).lines()
                    .map { it.trim().split(Regex("\\s+")) }
                    .firstOrNull { it.size >= 2 && it[1].trimStart('*') == asset }?.get(0)?.lowercase()
            }.getOrNull()
        }
        return Release(
            r.optString("tag_name"), versionOf(r.optString("tag_name")), r.optBoolean("prerelease"),
            r.optString("body"), apk.getString("browser_download_url"), apk.optLong("size"), sha,
        )
    }

    /** Whether [remote] is newer than [local] ("0.4.1" > "0.4.0" > "0.4.0-beta"). */
    fun isNewer(remote: String, local: String): Boolean = compareVersions(remote, local) > 0

    /** Downloads the release's APK into the app's cache and checks its SHA-256. Network: off the main thread. */
    fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, assetName(context))
        val digest = MessageDigest.getInstance("SHA-256")
        open(release.apkUrl).let { c ->
            try {
                val total = c.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
                c.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            done += n
                            if (total > 0) onProgress((done.toFloat() / total).coerceAtMost(1f))
                        }
                    }
                }
            } finally {
                c.disconnect()
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (release.sha256 != null && release.sha256 != actual) {
            file.delete()
            throw ChecksumException()
        }
        return file
    }

    /** Whether Android lets this app install apps (the user allows it once in settings). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the settings page where the user allows this app to install apps. */
    fun openInstallPermission(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * Installs [apk] over this app with a package installer session. Android asks the user to confirm
     * (the first time, at least); then it closes the app, replaces it, and [UpdatedReceiver] opens it
     * again.
     */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val status = PendingIntent.getBroadcast(
                context, id, Intent(context, UpdateStatusReceiver::class.java).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(status.intentSender)
        }
        _state.value = UpdateState.Installing
    }

    private fun open(url: String): HttpURLConnection {
        var current = URL(url)
        repeat(5) {
            val c = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "BundleUpdater")
                setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream, */*")
            }
            val code = c.responseCode
            if (code in 300..399) {
                val next = c.getHeaderField("Location") ?: throw IOException("redirect without location")
                c.disconnect()
                current = URL(current, next)
                // Only https downloads (GitHub sends its files from its own https servers).
                if (current.protocol != "https") throw IOException("refusing a non-https download")
                return@repeat
            }
            if (code != 200) {
                c.disconnect()
                throw IOException("HTTP $code")
            }
            return c
        }
        throw IOException("too many redirects")
    }

    private fun get(url: String): String = open(url).let { c ->
        try {
            c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    internal fun versionOf(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")

    /** Compares "1.2.3", "1.2.3-beta"…: numbers first, then a release beats a pre-release of the same numbers. */
    internal fun compareVersions(a: String, b: String): Int {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        val preA = '-' in a
        val preB = '-' in b
        return when {
            preA == preB -> 0
            preA -> -1
            else -> 1
        }
    }
}

class ChecksumException : IOException("the downloaded file doesn't match the release's checksum")

/** Receives the package installer's answers: asks the user to confirm, or reports a failure. */
class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // The app restarts with UpdatedReceiver.
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.set(UpdateState.Idle)
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                Updater.set(UpdateState.Failed(UpdateFailure.Signature, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()))
            else -> Updater.set(UpdateState.Failed(UpdateFailure.Install, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()))
        }
    }
}

/**
 * Opens the app again once it has been updated. If Android doesn't allow starting it from the
 * background, a notification offers to open it instead.
 */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        runCatching { context.startActivity(launch) }
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java)
            val fr = context.resources.configuration.locales[0]?.language == "fr"
            nm.createNotificationChannel(NotificationChannel("bundle-updates", if (fr) "Mises à jour" else "Updates", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE)
            val label = context.applicationInfo.loadLabel(context.packageManager)
            val notification = android.app.Notification.Builder(context, "bundle-updates")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(if (fr) "$label est à jour" else "$label is up to date")
                .setContentText(if (fr) "Version ${Updater.installedVersion(context)} installée. Touchez pour ouvrir." else "Version ${Updater.installedVersion(context)} installed. Tap to open.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(7301, notification)
        }
    }
}
