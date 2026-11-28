package com.android.messaging.ui.bundle

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import bundle.core.appearance.AppearanceStore
import java.util.Locale

/**
 * Bundle 0.8: the unread aura on a conversation's avatar, like Contacts' and Phone's (they draw
 * it through core/design, which this build doesn't compile): a ring turning slowly around the
 * picture. Only with "Expressive interface" on; still when animations are off; TalkBack says
 * "unread".
 */
@Composable
fun Modifier.unreadAura(unread: Boolean): Modifier {
    val appearance by AppearanceStore.get(LocalContext.current).state.collectAsState()
    if (!unread || !appearance.expressiveUi) return this
    val resolver = LocalContext.current.contentResolver
    val reduced = remember(resolver) {
        runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val angle = if (reduced) 0f else {
        val turn by rememberInfiniteTransition(label = "unreadAura").animateFloat(
            0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), label = "turn",
        )
        turn
    }
    val color = MaterialTheme.colorScheme.primary
    val label = if (Locale.getDefault().language == "fr") "non lu" else "unread"
    return this
        .semantics { stateDescription = label }
        .drawBehind {
            rotate(angle) {
                drawCircle(
                    Brush.sweepGradient(listOf(color, color.copy(alpha = 0.25f), color)),
                    radius = size.minDimension / 2 + 4.dp.toPx(),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
}

