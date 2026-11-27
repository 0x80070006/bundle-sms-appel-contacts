package app.people.contacts.ui.contact

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import app.people.contacts.data.ContactDetails
import app.people.contacts.ui.components.SEGMENT_GAP
import app.people.contacts.ui.components.SectionHeader
import app.people.contacts.ui.components.segmentShape
import bundle.core.hub.Hub
import bundle.core.hub.HubNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bundle: notes about this person, kept in the shared hub, so a note taken after a call in
 * Phone shows up here, and one written here shows up in Phone. Stored per phone number.
 */
fun LazyListScope.notesSection(contact: ContactDetails) {
    if (contact.phones.isEmpty()) return
    item(key = "hub-notes") { Notes(contact) }
}

@Composable
private fun Notes(contact: ContactDetails) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val numbers = contact.phones.map { it.value }
    var notes by remember { mutableStateOf<List<Pair<String, HubNote>>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(contact.id, numbers, reload) {
        notes = withContext(Dispatchers.IO) {
            numbers.flatMap { n -> Hub.line(context, n).notes.map { n to it } }
                .distinctBy { it.second }.sortedByDescending { it.second.time }
        }
    }

    Column {
        SectionHeader(stringResource(R.string.notes_title))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP)) {
            notes.forEachIndexed { i, (number, note) ->
                Surface(shape = segmentShape(i, notes.size), color = MaterialTheme.colorScheme.surfaceBright, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(note.text, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                DateUtils.getRelativeTimeSpanString(note.time).toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { Hub.removeNote(context, number, note) }
                                reload++
                            }
                        }) { Icon(Icons.Rounded.Close, stringResource(R.string.notes_delete)) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(stringResource(R.string.notes_hint)) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = draft.isNotBlank(),
                    onClick = {
                        val text = draft
                        draft = ""
                        scope.launch {
                            withContext(Dispatchers.IO) { Hub.addNote(context, numbers.first(), text) }
                            reload++
                        }
                    },
                ) { Text(stringResource(R.string.notes_add)) }
            }
        }
    }
}
