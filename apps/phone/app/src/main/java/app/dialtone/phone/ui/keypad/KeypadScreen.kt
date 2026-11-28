package app.dialtone.phone.ui.keypad

import app.dialtone.phone.util.tr
import app.dialtone.phone.util.trf

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dialtone.phone.data.Contact
import app.dialtone.phone.data.PhoneNumber
import app.dialtone.phone.data.SpeedDial
import app.dialtone.phone.telecom.Dialer
import app.dialtone.phone.ui.components.Avatar
import app.dialtone.phone.ui.components.DialPad
import app.dialtone.phone.ui.components.EmptyState
import app.dialtone.phone.ui.components.MorphButton
import app.dialtone.phone.ui.components.SectionHeader
import app.dialtone.phone.ui.components.T9
import app.dialtone.phone.ui.components.rememberDtmfTones
import app.dialtone.phone.ui.components.segmentShape
import app.dialtone.phone.ui.theme.CallColors
import app.dialtone.phone.util.formatNumber

/** Contacts whose number contains the digits, or whose name spells them in T9. */
private fun matchContacts(contacts: List<Contact>, query: String): List<Pair<Contact, PhoneNumber>> {
    val digits = query.filter { it.isDigit() }
    if (digits.length < 2) return emptyList()
    return contacts.asSequence().mapNotNull { c ->
        val byNumber = c.numbers.firstOrNull { it.number.filter(Char::isDigit).contains(digits) }
        val byName = c.name.lowercase().split(' ').any { word ->
            word.map { T9[it] ?: ' ' }.joinToString("").startsWith(digits)
        }
        when {
            byNumber != null -> c to byNumber
            byName -> c.numbers.firstOrNull()?.let { c to it }
            else -> null
        }
    }.take(20).toList()
}

/**
 * The Keypad tab: the dial pad slides up when the tab opens, over a scrollable list of
 * suggested contacts (people you call most, then favorites). Scrolling the list lowers the
 * pad; tapping the Keypad tab again raises it. While typing, the list shows contacts
 * matching the digits instead.
 */
@Composable
fun KeypadTab(
    /** The tab is on screen (not mid-swipe elsewhere, not searching): slides the pad up. */
    active: Boolean,
    /** The pad is raised (false after the list was scrolled). */
    padShown: Boolean,
    onPadShownChange: (Boolean) -> Unit,
    number: String,
    onNumberChange: (String) -> Unit,
    contacts: List<Contact>,
    suggested: List<Pair<Contact, PhoneNumber>>,
    lastDialed: String?,
    onCall: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val density = LocalDensity.current
    var padHeightPx by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    // Scrolling the suggestions lowers the pad out of the way.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && padShown) onPadShownChange(false)
    }

    val matches = remember(number, contacts) { matchContacts(contacts, number) }
    val typing = number.filter { it.isDigit() }.length >= 2
    val rows = if (typing) matches else suggested
    // The pad reaches down behind the bottom bar, so its height already includes the bar.
    val padUp = active && padShown
    val padSpace = if (padUp) with(density) { padHeightPx.toDp() } else contentPadding.calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = padSpace + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { SectionHeader(if (typing) tr("Contacts") else tr("Suggested")) }
            if (rows.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        Icons.Rounded.PersonSearch,
                        if (typing) tr("No matching contacts") else tr("No suggestions yet"),
                        if (typing) tr("Press call to dial the number.") else tr("People you call often show up here."),
                        Modifier.padding(top = 8.dp),
                    )
                }
            }
            itemsIndexed(rows, key = { _, (c, pn) -> "${c.id}-${pn.number}" }) { i, (contact, pn) ->
                // Bundle: a long press copies the number.
                Surface(
                    shape = segmentShape(i, rows.size),
                    color = scheme.surfaceBright,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().animateItem()
                        .clip(segmentShape(i, rows.size))
                        .combinedClickable(
                            onClick = { onCall(pn.number) },
                            onLongClick = {
                                Dialer.copy(context, pn.number)
                                android.widget.Toast.makeText(context, trf("%s copied", formatNumber(pn.number)), android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onLongClickLabel = tr("Copy number"),
                        ),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(contact.name, contact.photoUri, contact.id, 48.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(contact.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${pn.label} • ${formatNumber(pn.number)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // The springy slide overshoots a little. The pad's background runs down behind the
        // (opaque) bottom bar, so the overshoot never opens a gap above the bar.
        AnimatedVisibility(
            visible = padUp,
            enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it },
            exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            DialPanel(
                number = number,
                onNumberChange = onNumberChange,
                lastDialed = lastDialed,
                onCall = onCall,
                bottomInset = contentPadding.calculateBottomPadding(),
                modifier = Modifier.onSizeChanged { padHeightPx = it.height },
            )
        }
    }
}

@Composable
private fun DialPanel(
    number: String,
    onNumberChange: (String) -> Unit,
    lastDialed: String?,
    onCall: (String) -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val tones = rememberDtmfTones()
    val scheme = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = scheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = bottomInset + 12.dp)) {
            // Bundle: a long press on the number copies it, or pastes a number copied elsewhere.
            var numberMenu by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth()) {
                BasicText(
                    text = formatNumber(number),
                    style = MaterialTheme.typography.displaySmall.copy(color = scheme.onSurface, textAlign = TextAlign.Center),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 36.sp),
                    modifier = Modifier.fillMaxWidth()
                        .combinedClickable(
                            interactionSource = null,
                            indication = null,
                            onClick = {},
                            onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                numberMenu = true
                            },
                            onLongClickLabel = tr("Copy or paste"),
                        )
                        .padding(horizontal = 8.dp, vertical = 12.dp).height(48.dp),
                )
                androidx.compose.material3.DropdownMenu(expanded = numberMenu, onDismissRequest = { numberMenu = false }) {
                    if (number.isNotEmpty()) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(tr("Copy")) },
                            onClick = { numberMenu = false; Dialer.copy(context, number) },
                        )
                    }
                    val pasted = remember(numberMenu) { clipboardNumber(context) }
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(if (pasted != null) trf("Paste %s", pasted) else tr("Nothing to paste")) },
                        enabled = pasted != null,
                        onClick = { numberMenu = false; pasted?.let(onNumberChange) },
                    )
                }
            }

            DialPad(
                keyHeight = 60.dp,
                onKey = { c ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    tones.play(c)
                    onNumberChange(number + c)
                },
                onLongKey = { c ->
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    when {
                        c == '0' -> onNumberChange(number + '+')
                        c == '1' && number.isEmpty() -> Dialer.callVoicemail(context)
                        // Bundle: speed dial (see SpeedDial).
                        c in SpeedDial.KEYS && number.isEmpty() -> {
                            val target = SpeedDial.get(context, c)
                            if (target != null) Dialer.call(context, target)
                            else android.widget.Toast.makeText(context, trf("Type a number, then hold %s to save it as speed dial", c), android.widget.Toast.LENGTH_LONG).show()
                        }
                        c in SpeedDial.KEYS -> {
                            SpeedDial.set(context, c, number)
                            android.widget.Toast.makeText(context, trf("Speed dial %s: %s", c, formatNumber(number)), android.widget.Toast.LENGTH_SHORT).show()
                        }
                        else -> onNumberChange(number + c)
                    }
                },
            )

            Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                MorphButton(
                    onClick = {
                        if (number.isBlank()) lastDialed?.let(onNumberChange) else onCall(number)
                    },
                    container = CallColors.container(),
                    content = CallColors.content(),
                    modifier = Modifier.align(Alignment.Center).width(120.dp).height(68.dp),
                ) { Icon(Icons.Rounded.Call, tr("Call"), Modifier.size(30.dp)) }

                if (number.isNotEmpty()) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .size(56.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                interactionSource = null,
                                indication = ripple(),
                                onLongClick = { onNumberChange("") },
                                onClick = { onNumberChange(number.dropLast(1)) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Backspace, tr("Delete"), tint = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}


/**
 * Bundle: the phone number on the clipboard, if there is one. Only the characters a number is
 * dialled with are kept ("+33 6 12-34-56-78" gives "+33612345678"), so a number copied from a
 * message or a web page pastes cleanly. Null when nothing number-like is there.
 */
internal fun clipboardNumber(context: android.content.Context): String? {
    val text = runCatching {
        context.getSystemService(android.content.ClipboardManager::class.java)
            ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    }.getOrNull() ?: return null
    return dialableOf(text)
}

/** Keeps the dialable characters of [text]; null if what is left is neither a number nor a service code. */
internal fun dialableOf(text: String): String? {
    val kept = text.trim().filterIndexed { i, ch -> ch.isDigit() || ch == '*' || ch == '#' || (ch == '+' && text.trim().indexOf('+') == i) }
    // A number (3 digits or more), or a service code such as *#06#.
    return kept.takeIf { s -> s.count { it.isDigit() } >= 3 || (s.any { it == '*' || it == '#' } && s.any { it.isDigit() }) }
}
