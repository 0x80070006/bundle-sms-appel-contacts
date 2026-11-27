package app.people.contacts.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.people.contacts.R
import app.people.contacts.data.Trash
import app.people.contacts.ui.ContactsViewModel
import kotlinx.coroutines.launch

/** Bundle: the deleted contacts kept for 30 days, each with Restore. */
@Composable
fun TrashDialog(viewModel: ContactsViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(Trash.list(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trash_title)) },
        text = {
            if (items.isEmpty()) {
                Text(stringResource(R.string.trash_empty))
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(items, key = { it.file.name }) { item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.trash_days_left, Trash.daysLeft(item)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    val ok = viewModel.restoreFromTrash(item)
                                    Toast.makeText(context, if (ok) R.string.trash_restored else R.string.delete_failed, Toast.LENGTH_SHORT).show()
                                    items = Trash.list(context)
                                }
                            }) { Text(stringResource(R.string.trash_restore)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.trash_close)) } },
        dismissButton = if (items.isNotEmpty()) {
            { TextButton(onClick = { Trash.empty(context); items = emptyList() }) { Text(stringResource(R.string.trash_empty_now)) } }
        } else null,
    )
}
