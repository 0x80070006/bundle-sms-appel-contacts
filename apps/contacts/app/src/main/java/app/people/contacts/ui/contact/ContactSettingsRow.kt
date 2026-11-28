package app.people.contacts.ui.contact

import android.text.format.DateUtils
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.people.contacts.R
import app.people.contacts.data.ContactDetails
import bundle.core.hub.Hub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * Bundle: settings of this person, under the quick actions: ringtone (Android's own field) and
 * "Silent" (calls ring silently until a time; kept in the hub and applied by Phone).
 */
@Composable
fun ContactSettingsRow(contact: ContactDetails, onRingtone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val number = contact.phones.firstOrNull()?.value
    var silentUntil by remember(contact.id) { mutableLongStateOf(0L) }
    var reload by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(contact.id, reload) {
        silentUntil = number?.let { n -> withContext(Dispatchers.IO) { Hub.line(context, n).silentUntil } } ?: 0L
    }
    fun silence(until: Long) {
        menu = false
        scope.launch {
            withContext(Dispatchers.IO) { contact.phones.forEach { Hub.setSilentUntil(context, it.value, until) } }
            reload++
        }
    }
    val now = System.currentTimeMillis()
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = onRingtone,
            label = { Text(if (contact.ringtone != null) stringResource(R.string.settings_ringtone_custom) else stringResource(R.string.settings_ringtone_default)) },
        )
        if (number != null) {
            Box {
                AssistChip(
                    onClick = { menu = true },
                    label = {
                        Text(
                            if (silentUntil > now) {
                                stringResource(R.string.silent_until, DateUtils.formatDateTime(context, silentUntil, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY))
                            } else stringResource(R.string.silent_off),
                        )
                    },
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.silent_hour)) }, onClick = { silence(System.currentTimeMillis() + 3_600_000L) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.silent_morning)) }, onClick = {
                        silence(LocalDate.now().plusDays(1).atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.silent_week)) }, onClick = { silence(System.currentTimeMillis() + 7 * 86_400_000L) })
                    if (silentUntil > now) DropdownMenuItem(text = { Text(stringResource(R.string.silent_stop)) }, onClick = { silence(0L) })
                }
            }
        }
    }
}

/** Bundle: how complete the card is, with the first missing field to add. Hidden when complete. */
@Composable
fun CompletenessBar(contact: ContactDetails, onEdit: () -> Unit) {
    val checks = listOf(
        contact.phones.isNotEmpty() to R.string.complete_phone,
        contact.emails.isNotEmpty() to R.string.complete_email,
        (contact.photoUri != null) to R.string.complete_photo,
        contact.events.any { it.type == 3 } to R.string.complete_birthday,
        contact.addresses.isNotEmpty() to R.string.complete_address,
    )
    val done = checks.count { it.first }
    if (done == checks.size) return
    val missing = checks.first { !it.first }.second
    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.complete_percent, done * 100 / checks.size), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(stringResource(missing), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { done.toFloat() / checks.size }, modifier = Modifier.fillMaxWidth())
        }
    }
}
