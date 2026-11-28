package app.people.contacts.ui.highlights

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.people.contacts.R
import app.people.contacts.data.KeepInTouchEntry
import app.people.contacts.ui.components.Avatar
import app.people.contacts.util.Reach

/** Bundle: "Keep in touch": favourites without news for a while, to call or write in one tap. */
@Composable
fun KeepInTouchCard(entries: List<KeepInTouchEntry>, onOpenContact: (Long) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = scheme.tertiaryContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.keep_in_touch), style = MaterialTheme.typography.titleMedium, color = scheme.onTertiaryContainer, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.keep_in_touch_local), style = MaterialTheme.typography.bodySmall, color = scheme.onTertiaryContainer)
            }
            entries.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(onClick = { onOpenContact(e.contact.id) }, color = scheme.tertiaryContainer, modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Avatar(e.contact.name, e.contact.photoUri, e.contact.id, 48.dp)
                            Column {
                                Text(e.contact.name, style = MaterialTheme.typography.bodyLarge, color = scheme.onTertiaryContainer)
                                Text(
                                    e.lastContact?.let {
                                        stringResource(R.string.keep_in_touch_last, DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString())
                                    } ?: stringResource(R.string.keep_in_touch_never),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onTertiaryContainer,
                                )
                            }
                        }
                    }
                    e.number?.let { number ->
                        FilledTonalIconButton(onClick = { Reach.dial(context, number) }) {
                            Icon(Icons.Rounded.Call, stringResource(R.string.keep_in_touch_call, e.contact.name))
                        }
                        FilledTonalIconButton(onClick = { Reach.message(context, number) }) {
                            Icon(Icons.Rounded.Sms, stringResource(R.string.keep_in_touch_write, e.contact.name))
                        }
                    }
                }
            }
        }
    }
}
