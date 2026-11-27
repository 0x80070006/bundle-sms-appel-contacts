package app.people.contacts.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.people.contacts.R
import app.people.contacts.data.SuiteBackup
import app.people.contacts.data.VCardReader
import app.people.contacts.ui.ContactsViewModel
import bundle.core.sync.WrongPassphraseException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bundle: the full backup. The passphrase is typed by the user each time and never stored:
 * without it the file can't be opened, by anyone.
 */
@Composable
fun SuiteBackupDialog(viewModel: ContactsViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val strDone = stringResource(R.string.backup_done)
    val strRestored = stringResource(R.string.backup_restored)
    val strWrong = stringResource(R.string.backup_wrong)
    val strFailed = stringResource(R.string.backup_failed)

    fun run(block: suspend () -> String) {
        busy = true
        status = null
        scope.launch {
            status = runCatching { block() }.getOrElse { e -> if (e is WrongPassphraseException) strWrong else strFailed }
            busy = false
        }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) run {
            val s = SuiteBackup.export(context, uri, passphrase.toCharArray())
            strDone.format(s.contacts, s.calls, s.texts)
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) run {
            val s = SuiteBackup.restore(context, uri, passphrase.toCharArray()) { vcf ->
                viewModel.importVCardText(vcf)
            }
            strRestored.format(s.contacts, s.calls, s.texts)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.backup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    passphrase, { passphrase = it },
                    label = { Text(stringResource(R.string.backup_passphrase)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.backup_passphrase_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                if (busy) Text(stringResource(R.string.backup_working), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && passphrase.length >= 8, onClick = {
                val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                exporter.launch("sauvegarde-$day.bndlbak")
            }) { Text(stringResource(R.string.backup_export)) }
        },
        dismissButton = {
            TextButton(enabled = !busy && passphrase.isNotEmpty(), onClick = { importer.launch(arrayOf("*/*")) }) {
                Text(stringResource(R.string.backup_restore))
            }
        },
    )
}

@Suppress("unused") private val keepReader = VCardReader
