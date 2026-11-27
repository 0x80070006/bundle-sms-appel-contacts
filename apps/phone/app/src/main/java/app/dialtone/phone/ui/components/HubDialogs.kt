package app.dialtone.phone.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dialtone.phone.util.tr
import bundle.core.hub.Hub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bundle: the SIMs that can place calls, as (id, label). Empty without phone-state access. */
@SuppressLint("MissingPermission")
fun callingSims(context: Context): List<Pair<String, String>> = runCatching {
    val telecom = context.getSystemService(TelecomManager::class.java) ?: return emptyList()
    telecom.callCapablePhoneAccounts.map { h: PhoneAccountHandle ->
        h.id to (telecom.getPhoneAccount(h)?.label?.toString() ?: h.id)
    }
}.getOrDefault(emptyList())

/** Bundle: write a note about [number]; it's kept in the hub and shows on the Contacts card. */
@Composable
fun AddNoteDialog(number: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Add a note")) },
        text = { OutlinedTextField(text, { text = it }, placeholder = { Text(tr("What was it about?")) }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                val app = context.applicationContext
                val note = text
                Thread {
                    val ok = Hub.addNote(app, number, note)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(app, if (ok) tr("Note saved in Contacts") else tr("Install Contacts to keep notes"), Toast.LENGTH_SHORT).show()
                    }
                }.start()
                onDismiss()
            }) { Text(tr("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

/** Bundle: which SIM calls to [number] go out on; saved in the hub. */
@Composable
fun PreferredSimDialog(number: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sims = remember { callingSims(context) }
    val current by produceState<String?>(null, number) {
        value = withContext(Dispatchers.IO) { Hub.line(context, number).simId } ?: ""
    }
    fun choose(id: String?) {
        val app = context.applicationContext
        Thread { Hub.setSim(app, number, id) }.start()
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("SIM for this number")) },
        text = {
            Column {
                if (sims.size < 2) Text(tr("Only one SIM can make calls on this phone."), Modifier.padding(bottom = 8.dp))
                val options = listOf<Pair<String?, String>>(null to tr("Phone default")) + sims
                options.forEach { (id, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { choose(id) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = (current ?: "") == (id ?: ""), onClick = { choose(id) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } },
    )
}
