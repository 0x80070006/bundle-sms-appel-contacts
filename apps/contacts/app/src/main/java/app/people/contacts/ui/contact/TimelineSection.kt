package app.people.contacts.ui.contact

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.people.contacts.R
import app.people.contacts.data.ContactDetails
import app.people.contacts.data.ContactTimeline
import app.people.contacts.data.TimelineEntry
import app.people.contacts.data.TimelineKind
import app.people.contacts.data.hasCallAndTextAccess
import app.people.contacts.ui.components.ListRow
import app.people.contacts.ui.components.SEGMENT_GAP
import app.people.contacts.ui.components.SectionHeader
import app.people.contacts.ui.components.segmentShape
import bundle.core.appearance.InstallHelpDialog
import bundle.core.telephony.formatDuration

/**
 * "History" on a contact's page: the calls and texts with them in one list, newest first. Asks
 * for call log and SMS access when it's missing, and points to the help if Android blocks it.
 */
fun LazyListScope.timelineSection(contact: ContactDetails) {
    if (contact.phones.isEmpty()) return
    item(key = "timeline") { Timeline(contact) }
}

private const val FIRST_PAGE = 6

@Composable
private fun Timeline(contact: ContactDetails) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(hasCallAndTextAccess(context)) }
    var asked by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        allowed = hasCallAndTextAccess(context)
    }
    if (showHelp) InstallHelpDialog(onDismiss = { showHelp = false })

    Column {
        SectionHeader(stringResource(R.string.timeline_title))
        if (!allowed) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceBright, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.timeline_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { ask.launch(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_SMS)) }) {
                        Text(stringResource(R.string.timeline_allow))
                    }
                    // Android blocks these two for apps installed from a file: say how to lift it.
                    if (asked) {
                        TextButton(onClick = { showHelp = true }) { Text(stringResource(R.string.timeline_blocked)) }
                    }
                }
            }
            return@Column
        }
        val numbers = contact.phones.map { it.value }
        val entries by produceState<List<TimelineEntry>?>(null, contact.id, numbers) {
            value = ContactTimeline(context).load(numbers)
        }
        var all by remember { mutableStateOf(false) }
        val list = entries
        when {
            list == null -> Unit
            list.isEmpty() -> Text(
                stringResource(R.string.timeline_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp, bottom = 8.dp),
            )
            else -> {
                val shown = if (all) list else list.take(FIRST_PAGE)
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP)) {
                    shown.forEachIndexed { index, entry ->
                        val last = index == shown.lastIndex && (all || list.size <= FIRST_PAGE)
                        TimelineRow(entry, segmentShape(index, if (last) shown.size else shown.size + 1))
                    }
                }
                if (!all && list.size > FIRST_PAGE) {
                    TextButton(onClick = { all = true }, modifier = Modifier.padding(start = 16.dp, top = 4.dp)) { Text(stringResource(R.string.timeline_more)) }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(entry: TimelineEntry, shape: androidx.compose.ui.graphics.Shape) {
    val context = LocalContext.current
    val title = when (entry.kind) {
        TimelineKind.CallIncoming -> stringResource(R.string.recent_incoming)
        TimelineKind.CallOutgoing -> stringResource(R.string.recent_outgoing)
        TimelineKind.CallMissed -> stringResource(R.string.recent_missed)
        TimelineKind.CallRejected -> stringResource(R.string.timeline_declined)
        TimelineKind.TextReceived -> entry.text.orEmpty().ifBlank { stringResource(R.string.recent_text_received) }
        TimelineKind.TextSent -> stringResource(R.string.recent_you, entry.text.orEmpty())
    }
    val timeText = DateUtils.formatDateTime(
        context, entry.time,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME,
    )
    val summary = if (entry.isCall && entry.seconds > 0) "$timeText · ${formatDuration(entry.seconds)}" else timeText
    val icon = when (entry.kind) {
        TimelineKind.CallIncoming -> Icons.AutoMirrored.Rounded.CallReceived
        TimelineKind.CallOutgoing -> Icons.AutoMirrored.Rounded.CallMade
        TimelineKind.CallMissed -> Icons.AutoMirrored.Rounded.CallMissed
        TimelineKind.CallRejected -> Icons.Rounded.PhoneDisabled
        TimelineKind.TextReceived, TimelineKind.TextSent -> Icons.Rounded.Sms
    }
    ListRow(
        icon = icon,
        title = title,
        summary = summary,
        shape = shape,
        summaryColor = if (entry.kind == TimelineKind.CallMissed) MaterialTheme.colorScheme.error else null,
        onClick = {
            val intent = if (entry.isCall) Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", entry.number, null))
            else Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", entry.number, null))
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
    )
}
