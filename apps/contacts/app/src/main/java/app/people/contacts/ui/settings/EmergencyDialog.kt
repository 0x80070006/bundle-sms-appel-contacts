package app.people.contacts.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.people.contacts.R
import app.people.contacts.data.Emergency
import app.people.contacts.ui.ContactsViewModel
import app.people.contacts.util.qrCode
import java.util.Locale

/**
 * Bundle: emergency information (ICE): medical details, the people marked as emergency contacts
 * (from a contact's menu), and a QR code with all of it for a first responder to scan.
 */
@Composable
fun EmergencyDialog(viewModel: ContactsViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val saved = remember { Emergency.load(context) }
    var blood by remember { mutableStateOf(saved.bloodType) }
    var allergies by remember { mutableStateOf(saved.allergies) }
    var meds by remember { mutableStateOf(saved.medications) }
    var conditions by remember { mutableStateOf(saved.conditions) }
    var showQr by remember { mutableStateOf(false) }
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val people = contacts.orEmpty().filter { it.id in saved.contactIds }

    fun current() = saved.copy(bloodType = blood, allergies = allergies, medications = meds, conditions = conditions)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ice_title)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showQr) {
                    val text = Emergency.qrText(current(), people.map { it.name to it.numbers.firstOrNull() }, Locale.getDefault().language == "fr")
                    qrCode(text)?.let { Image(it, null, Modifier.size(260.dp).align(Alignment.CenterHorizontally)) }
                    Text(text, style = MaterialTheme.typography.bodySmall)
                    return@Column
                }
                Text(stringResource(R.string.ice_contacts), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (people.isEmpty()) stringResource(R.string.ice_contacts_none) else people.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.ice_medical), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(blood, { blood = it }, label = { Text(stringResource(R.string.ice_blood)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(allergies, { allergies = it }, label = { Text(stringResource(R.string.ice_allergies)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(meds, { meds = it }, label = { Text(stringResource(R.string.ice_meds)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(conditions, { conditions = it }, label = { Text(stringResource(R.string.ice_conditions)) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.ice_private), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text(stringResource(R.string.ice_lockscreen)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { Emergency.save(context, current()); onDismiss() }) { Text(stringResource(R.string.ice_save)) }
        },
        dismissButton = {
            TextButton(onClick = { Emergency.save(context, current()); showQr = !showQr }) {
                Text(stringResource(if (showQr) R.string.ice_edit else R.string.ice_qr))
            }
        },
    )
}
