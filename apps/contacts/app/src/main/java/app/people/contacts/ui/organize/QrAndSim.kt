package app.people.contacts.ui.organize

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.people.contacts.R
import app.people.contacts.data.QrImport
import app.people.contacts.data.SimContacts
import app.people.contacts.ui.ContactsViewModel
import kotlinx.coroutines.launch

/** Bundle: picks a photo or screenshot of a contact QR code and adds the contact. */
@Composable
fun rememberQrImport(viewModel: ContactsViewModel): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val vcard = QrImport.read(context, uri)
            val added = vcard?.let { viewModel.importVCardText(it) } ?: 0
            Toast.makeText(
                context,
                if (added > 0) context.getString(R.string.qr_added) else context.getString(R.string.qr_none),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    return { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** Bundle: the SIM card's contacts, ticked ones imported into the phone's contacts. */
@Composable
fun SimContactsDialog(viewModel: ContactsViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<SimContacts.Entry>?>(null) }
    var chosen by remember { mutableStateOf(emptySet<SimContacts.Entry>()) }
    LaunchedEffect(Unit) {
        entries = SimContacts.load(context).also { chosen = it.toSet() }
    }
    val list = entries
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sim_title)) },
        text = {
            when {
                list == null -> Text(stringResource(R.string.sim_loading))
                list.isEmpty() -> Text(stringResource(R.string.sim_empty))
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list) { e ->
                        val on = e in chosen
                        Row(
                            Modifier.fillMaxWidth().clickable { chosen = if (on) chosen - e else chosen + e },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = { chosen = if (it) chosen + e else chosen - e })
                            Column {
                                Text(e.name, style = MaterialTheme.typography.bodyLarge)
                                Text(e.number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty(), onClick = {
                val picked = list.orEmpty().filter { it in chosen }
                scope.launch {
                    val added = viewModel.importVCardText(SimContacts.toVCards(picked))
                    Toast.makeText(context, context.resources.getQuantityString(R.plurals.sim_imported, added, added), Toast.LENGTH_LONG).show()
                }
                onDismiss()
            }) { Text(stringResource(R.string.sim_import)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.trash_close)) } },
    )
}
