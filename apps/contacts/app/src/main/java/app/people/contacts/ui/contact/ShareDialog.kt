package app.people.contacts.ui.contact

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.core.content.FileProvider
import app.people.contacts.R
import app.people.contacts.data.ContactDetails
import app.people.contacts.data.ShareField
import app.people.contacts.data.shareFieldsOf
import app.people.contacts.data.shareVCard
import app.people.contacts.util.Reach
import java.io.File

/**
 * Bundle: "checked sharing". Shows every value of the card with a tick box (addresses, notes and
 * birthday start unticked), then shares a vCard of the name and the ticked values only.
 * "Whole card" keeps the earlier behaviour (Android's own vCard, photo included).
 */
@Composable
fun ShareDialog(contact: ContactDetails, isProfile: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val fields = remember(contact) { shareFieldsOf(contact) }
    var chosen by remember(contact) { mutableStateOf(fields.filter { it.defaultOn }.map { it.id }.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_checked_title, contact.name)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.share_checked_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                fields.forEach { f ->
                    val on = f.id in chosen
                    Row(
                        Modifier.fillMaxWidth().clickable { chosen = if (on) chosen - f.id else chosen + f.id }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = { chosen = if (it) chosen + f.id else chosen - f.id })
                        Column {
                            Text(f.value, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                            Text(kindLabel(f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { shareChecked(context, contact.name, shareVCard(contact.name, fields, chosen)); onDismiss() }) {
                Text(stringResource(R.string.share_checked_send))
            }
        },
        dismissButton = {
            TextButton(onClick = { Reach.share(context, contact.lookupKey, contact.name, profile = isProfile); onDismiss() }) {
                Text(stringResource(R.string.share_whole_card))
            }
        },
    )
}

@Composable
private fun kindLabel(f: ShareField): String {
    val kind = when (f.kind) {
        ShareField.Kind.Phone -> stringResource(R.string.share_kind_phone)
        ShareField.Kind.Email -> stringResource(R.string.share_kind_email)
        ShareField.Kind.Address -> stringResource(R.string.share_kind_address)
        ShareField.Kind.Company -> stringResource(R.string.share_kind_company)
        ShareField.Kind.Title -> stringResource(R.string.share_kind_title)
        ShareField.Kind.Birthday -> stringResource(R.string.share_kind_birthday)
        ShareField.Kind.Website -> stringResource(R.string.share_kind_website)
        ShareField.Kind.Note -> stringResource(R.string.share_kind_note)
    }
    return if (f.label.isBlank()) kind else "$kind · ${f.label}"
}

/** Writes the vCard to the cache (old ones removed) and opens the share sheet with it. */
private fun shareChecked(context: Context, name: String, vcard: String) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    val safe = name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifBlank { "contact" }
    val file = File(dir, "$safe.vcf").apply { writeText(vcard) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.shared", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/x-vcard")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
