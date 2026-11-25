package bundle.core.appearance

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** The update screen's text in French and English. */
class UpdateStrings(private val fr: Boolean) {
    private fun t(fr: String, en: String) = if (this.fr) fr else en
    val rowTitle = t("Mises à jour", "Updates")
    fun rowSummary(version: String) = t("Version $version · rechercher une mise à jour", "Version $version · check for an update")
    val title = t("Mise à jour", "Update")
    fun installed(version: String) = t("Version installée : $version", "Installed version: $version")
    val beta = t("Versions bêta", "Beta versions")
    val betaSummary = t("Proposer aussi les versions en test", "Also offer test versions")
    val check = t("Rechercher", "Check")
    val checking = t("Recherche de la dernière version…", "Looking for the latest version…")
    fun upToDate(latest: String) = t("L'app est à jour (dernière version : $latest).", "The app is up to date (latest: $latest).")
    fun available(version: String, beta: Boolean) = t("Version $version disponible" + if (beta) " (bêta)" else "", "Version $version available" + if (beta) " (beta)" else "")
    val update = t("Mettre à jour", "Update")
    fun downloading(percent: Int) = t("Téléchargement… $percent %", "Downloading… $percent%")
    val installing = t("Installation… L'app va se fermer : touchez la notification « est à jour » pour la rouvrir dans sa nouvelle version.", "Installing… The app will close: tap the \"is up to date\" notification to open it again in its new version.")
    val permission = t(
        "Android doit d'abord autoriser cette app à installer des apps (une seule fois) : activez « Autoriser cette source », puis revenez.",
        "Android must first allow this app to install apps (once): turn on \"Allow from this source\", then come back.",
    )
    val openSettings = t("Ouvrir le réglage", "Open the setting")
    fun failure(reason: UpdateFailure, detail: String) = when (reason) {
        UpdateFailure.Network -> t("Impossible de joindre GitHub. Vérifiez la connexion.", "Couldn't reach GitHub. Check the connection.")
        UpdateFailure.NoApk -> t("Aucune version publiée ne contient cette app.", "No published version contains this app.")
        UpdateFailure.Checksum -> t("Le fichier téléchargé ne correspond pas à la version publiée : il n'a pas été installé.", "The downloaded file doesn't match the published version: it wasn't installed.")
        UpdateFailure.Signature -> t(
            "Android a refusé la mise à jour : elle n'est pas signée avec la même clé que l'app installée. Désinstallez l'app, puis installez la nouvelle version à la main.",
            "Android refused the update: it isn't signed with the same key as the installed app. Uninstall the app, then install the new version by hand.",
        )
        UpdateFailure.Install -> t("L'installation a échoué. $detail", "The install failed. $detail")
    }
    val close = t("Fermer", "Close")
}

@Composable
@ReadOnlyComposable
fun updateStrings(): UpdateStrings = UpdateStrings(LocalConfiguration.current.locales[0]?.language == "fr")

private fun betaOn(context: Context) = context.getSharedPreferences("bundle_updates", Context.MODE_PRIVATE).getBoolean("beta", false)
private fun setBeta(context: Context, on: Boolean) = context.getSharedPreferences("bundle_updates", Context.MODE_PRIVATE).edit().putBoolean("beta", on).apply()

/** Checks GitHub for the latest release and offers to install it over this app. */
@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val s = updateStrings()
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsState()
    var beta by remember { mutableStateOf(betaOn(context)) }
    val installed = remember { Updater.installedVersion(context) }
    var askedNotifications by remember { mutableStateOf(false) }
    var pendingRelease by remember { mutableStateOf<Release?>(null) }
    // The update function, for the permission callback (a plain holder: not state, no recomposition).
    val resume = remember { arrayOfNulls<(Release) -> Unit>(1) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingRelease?.let { r -> pendingRelease = null; resume[0]?.invoke(r) }
    }

    fun check() {
        Updater.set(UpdateState.Checking)
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { Updater.fetchLatest(context, beta) } }
            Updater.set(
                result.fold(
                    onSuccess = { r ->
                        when {
                            r == null -> UpdateState.Failed(UpdateFailure.NoApk)
                            Updater.isNewer(r.version, installed) -> UpdateState.Available(r)
                            else -> UpdateState.UpToDate(r.version)
                        }
                    },
                    onFailure = { UpdateState.Failed(UpdateFailure.Network, it.message.orEmpty()) },
                ),
            )
        }
    }

    fun update(release: Release) {
        // Android 13+: the "open again" notification needs this permission; ask once, carry on either way.
        if (Build.VERSION.SDK_INT >= 33 && !askedNotifications &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedNotifications = true
            pendingRelease = release
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (!Updater.canInstall(context)) {
            Updater.set(UpdateState.NeedsPermission(release))
            return
        }
        Updater.set(UpdateState.Downloading(release, 0f))
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val apk = Updater.download(context, release) { Updater.set(UpdateState.Downloading(release, it)) }
                    Updater.install(context, apk)
                }
            }
            result.onFailure { e ->
                Updater.set(
                    when (e) {
                        is ChecksumException -> UpdateState.Failed(UpdateFailure.Checksum)
                        is IOException -> UpdateState.Failed(UpdateFailure.Network, e.message.orEmpty())
                        else -> UpdateState.Failed(UpdateFailure.Install, e.message.orEmpty())
                    },
                )
            }
        }
    }

    resume[0] = ::update
    LaunchedEffect(Unit) { if (state !is UpdateState.Downloading && state !is UpdateState.Installing) check() }
    // Back from the "install apps" setting: carry on with the update.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            (Updater.state.value as? UpdateState.NeedsPermission)?.let { if (Updater.canInstall(context)) update(it.release) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.installed(installed), style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.beta, style = MaterialTheme.typography.bodyMedium)
                        Text(s.betaSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = beta, onCheckedChange = { beta = it; setBeta(context, it); check() })
                }
                when (val st = state) {
                    UpdateState.Idle -> Unit
                    UpdateState.Checking -> { Text(s.checking); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    is UpdateState.UpToDate -> Text(s.upToDate(st.latest), color = MaterialTheme.colorScheme.primary)
                    is UpdateState.Available -> {
                        Text(s.available(st.release.version, st.release.prerelease), style = MaterialTheme.typography.titleMedium)
                        if (st.release.notes.isNotBlank()) {
                            Text(
                                st.release.notes.lines().take(14).joinToString("\n").replace("**", "").replace("### ", ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.heightIn(max = 240.dp),
                            )
                        }
                        Button(onClick = { update(st.release) }, modifier = Modifier.fillMaxWidth()) { Text(s.update) }
                    }
                    is UpdateState.Downloading -> {
                        Text(s.downloading((st.fraction * 100).toInt()))
                        LinearProgressIndicator(progress = { st.fraction }, modifier = Modifier.fillMaxWidth())
                    }
                    UpdateState.Installing -> { Text(s.installing); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    is UpdateState.NeedsPermission -> {
                        Text(s.permission)
                        Button(onClick = { Updater.openInstallPermission(context) }, modifier = Modifier.fillMaxWidth()) { Text(s.openSettings) }
                    }
                    is UpdateState.Failed -> Text(s.failure(st.reason, st.detail), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (state is UpdateState.Failed || state is UpdateState.UpToDate) TextButton(onClick = { check() }) { Text(s.check) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.close) } },
    )
}
