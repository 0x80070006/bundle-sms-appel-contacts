package bundle.core.appearance

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Bundle: the three apps, in the link pastille's order. */
enum class BundleApp(val key: String) { Contacts("contacts"), Phone("phone"), Messages("messages") }

/**
 * Bundle: the link pastille, "Contacts · Phone · Messages", the same in the three apps, above
 * their bottom bar. The current app is marked; the others open with one tap. The siblings are the
 * apps found through the appearance sync (same signing key), so nothing else can sit in it; an
 * app that isn't installed is left out. Hidden when no sibling is installed.
 */
@Composable
fun LinkChip(current: BundleApp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val siblings = remember { siblingApps(context) }
    if (siblings.isEmpty()) return
    val fr = LocalConfiguration.current.locales[0].language == "fr"
    val names = mapOf(
        BundleApp.Contacts to "Contacts",
        BundleApp.Phone to if (fr) "Appel" else "Phone",
        BundleApp.Messages to if (fr) "SMS" else "Messages",
    )
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainerHigh) {
            Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BundleApp.entries.filter { it == current || it in siblings }.forEach { app ->
                    val here = app == current
                    Surface(
                        onClick = { if (!here) siblings[app]?.let { open(context, it) } },
                        shape = RoundedCornerShape(16.dp),
                        color = if (here) scheme.surfaceBright else scheme.surfaceContainerHigh,
                        modifier = Modifier.height(36.dp).semantics { role = Role.Tab; selected = here },
                    ) {
                        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text(
                                names.getValue(app),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (here) scheme.onSurface else scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The installed sibling apps (same key, found through the appearance sync), by role. */
private fun siblingApps(context: Context): Map<BundleApp, String> {
    val pm = context.packageManager
    return runCatching {
        pm.queryIntentContentProviders(Intent(AppearanceStore.ACTION_SYNC), 0)
            .mapNotNull { it.providerInfo?.packageName }
            .filter { it != context.packageName }
            .filter { pm.checkSignatures(it, context.packageName) == PackageManager.SIGNATURE_MATCH }
            .mapNotNull { pkg -> BundleApp.entries.firstOrNull { ".${it.key}" in pkg }?.let { it to pkg } }
            .toMap()
    }.getOrDefault(emptyMap())
}

private fun open(context: Context, pkg: String) {
    val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
    runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
