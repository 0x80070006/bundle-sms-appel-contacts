package app.people.contacts.ui.organize

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.people.contacts.R
import app.people.contacts.data.Fixes
import app.people.contacts.sync.ContactSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Bundle: "Keeping the book tidy": the sync's state (with deletions waiting for a yes) and the
 * fixes, each with its count, a tap away. The detailed screens are the ones below in Organize.
 */
@Composable
fun MaintenanceSection(duplicates: Int, fixes: Fixes?, onOpenMerge: () -> Unit, onOpenFix: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sync by ContactSync.status.collectAsState()
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.maintenance_title), style = MaterialTheme.typography.titleLarge)
        if (sync.configured) {
            val paused = sync.pendingDeletions > 0
            Surface(shape = RoundedCornerShape(24.dp), color = if (paused) scheme.inverseSurface else scheme.surfaceBright, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val onCard = if (paused) scheme.inverseOnSurface else scheme.onSurface
                    Text(
                        if (paused) stringResource(R.string.maintenance_sync_paused) else stringResource(R.string.maintenance_sync_ok),
                        style = MaterialTheme.typography.titleMedium, color = onCard,
                    )
                    Text(
                        if (paused) stringResource(R.string.sync_confirm_body, sync.pendingDeletions)
                        else sync.lastAt.takeIf { it > 0 }?.let {
                            stringResource(R.string.maintenance_sync_last, DateUtils.getRelativeTimeSpanString(it).toString())
                        } ?: stringResource(R.string.maintenance_sync_never),
                        style = MaterialTheme.typography.bodyMedium, color = onCard,
                    )
                    if (paused && !sync.running) {
                        Button(onClick = { scope.launch(Dispatchers.IO) { ContactSync.run(context, confirmDeletions = true) } }) {
                            Text(stringResource(R.string.sync_confirm_deletions, sync.pendingDeletions))
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.maintenance_fix_header), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(duplicates, stringResource(R.string.maintenance_duplicates), stringResource(R.string.maintenance_merge), onOpenMerge, Modifier.weight(1f))
            Tile(fixes?.countryCodes?.size ?: 0, stringResource(R.string.maintenance_no_code), stringResource(R.string.maintenance_add_code), onOpenFix, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(fixes?.unnamed?.size ?: 0, stringResource(R.string.maintenance_no_name), stringResource(R.string.maintenance_name), onOpenFix, Modifier.weight(1f))
            Tile(fixes?.empty?.size ?: 0, stringResource(R.string.maintenance_empty), stringResource(R.string.maintenance_trash), onOpenFix, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Tile(count: Int, title: String, action: String, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(onClick = onClick, enabled = count > 0, shape = RoundedCornerShape(22.dp), color = scheme.surfaceBright, modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium)
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(if (count > 0) action else stringResource(R.string.maintenance_nothing), style = MaterialTheme.typography.bodySmall, color = if (count > 0) scheme.primary else scheme.onSurfaceVariant)
        }
    }
}
