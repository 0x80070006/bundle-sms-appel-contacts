package bundle.core.appearance

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Help for the most common problem with an app installed from an APK file: Android keeps the
 * SMS and call-log permissions (and a few others) switched off for apps that didn't come from a
 * store, and the Allow button does nothing or says "Restricted setting". Nothing inside the app can
 * lift this, so the app explains it and opens the page where the user can.
 */
object InstallHelp {
    private val FILE_INSTALLERS = setOf(
        "com.google.android.packageinstaller", "com.android.packageinstaller",
        "com.google.android.documentsui", "com.android.documentsui", "com.sec.android.app.myfiles",
        "com.mi.android.globalFileexplorer", "com.android.chrome", "org.mozilla.firefox",
    )

    /**
     * Whether Android most likely installed this app from a file (so "restricted settings" apply):
     * the install source says local or downloaded file, or the installer is a package installer,
     * a file manager or a browser. Installed with adb or from a store: false.
     */
    fun installedFromFile(context: Context): Boolean = runCatching {
        val info = context.packageManager.getInstallSourceInfo(context.packageName)
        val source = if (Build.VERSION.SDK_INT >= 33) info.packageSource else PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED
        source == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE ||
            source == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE ||
            info.installingPackageName in FILE_INSTALLERS
    }.getOrDefault(false)

    /** Opens this app's page in Android's settings (permissions, and its ⋮ menu). */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}

/** The help's text in French and English. */
class InstallStrings(private val fr: Boolean) {
    private fun t(fr: String, en: String) = if (this.fr) fr else en
    val title = t("Android bloque une autorisation ?", "Android blocks a permission?")
    val why = t(
        "Android protège les autorisations SMS et journal d'appels des applications installées depuis un fichier APK : le bouton Autoriser est grisé ou répond « Paramètre restreint ». L'app est légitime, mais Android ne le sait que pour celles d'un magasin d'applications, et rien dans l'app ne peut lever cette protection : c'est à vous de le faire, une seule fois.",
        "Android protects the SMS and call-log permissions of apps installed from an APK file: the Allow button is greyed out or says \"Restricted setting\". The app is legitimate, but Android only knows that for store apps, and nothing inside the app can lift this protection: you do it yourself, once.",
    )
    val fromFile = t("Cette app a bien été installée depuis un fichier : c'est la cause probable.", "This app was installed from a file: that is the likely cause.")
    val steps = t(
        "1. Touchez « Ouvrir les réglages de l'app » ci-dessous.\n2. Touchez ⋮ (en haut à droite), puis « Autoriser les paramètres restreints » (le menu n'apparaît qu'après un premier refus d'autorisation). Confirmez avec votre code ou empreinte.\n3. Touchez Autorisations et accordez-les.",
        "1. Tap \"Open the app's settings\" below.\n2. Tap ⋮ (top right), then \"Allow restricted settings\" (the menu only appears after a first refused permission). Confirm with your PIN or fingerprint.\n3. Tap Permissions and grant them.",
    )
    val tip = t(
        "Pour éviter ce blocage à la prochaine installation : passez par Obtainium ou F-Droid, ou installez avec adb. Définir l'app comme application par défaut (Appel ou SMS) lui accorde aussi directement ses autorisations.",
        "To avoid this next time: install with Obtainium or F-Droid, or with adb. Making the app your default (Phone or SMS) app also grants its permissions directly.",
    )
    val open = t("Ouvrir les réglages de l'app", "Open the app's settings")
    val close = t("Fermer", "Close")
    val rowTitle = t("Autorisations bloquées ?", "Permissions blocked?")
    val rowSummary = t("Aide si Android refuse d'accorder l'accès", "Help if Android won't grant access")
}

@Composable
@ReadOnlyComposable
fun installStrings(): InstallStrings = InstallStrings(LocalConfiguration.current.locales[0]?.language == "fr")

/** The explanation, with the button that opens the app's settings. */
@Composable
fun InstallHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val s = installStrings()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.why, style = MaterialTheme.typography.bodyMedium)
                if (InstallHelp.installedFromFile(context)) {
                    Text(s.fromFile, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(s.steps, style = MaterialTheme.typography.bodyMedium)
                Text(s.tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { InstallHelp.openAppSettings(context) }) { Text(s.open) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.close) } },
    )
}
