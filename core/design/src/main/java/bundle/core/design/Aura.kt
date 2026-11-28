package bundle.core.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * What a contact's avatar outline says, the same in the three apps: a message waiting, a call
 * missed recently, or nothing. Shown only with Appearance's "Interface expressive".
 */
enum class Aura(internal val periodMs: Int) {
    None(0),
    /** An unread message (Messages). */
    Unread(7000),
    /** A missed call in the last 24 hours (Phone, Contacts): faster, in the error colour. */
    Missed(2400),
}

/**
 * Draws the [aura] as a ring just outside [shape], slowly turning. With animations off, the ring
 * stays still; without "Interface expressive", nothing is drawn. TalkBack hears the state.
 */
@Composable
internal fun Modifier.aura(aura: Aura, shape: Shape): Modifier {
    if (aura == Aura.None || !LocalAppearance.current.expressiveUi) return this
    val color: Color = when (aura) {
        Aura.Missed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val moving = !rememberReducedMotion()
    val angle = if (moving) {
        rememberInfiniteTransition(label = "aura").animateFloat(
            0f, 360f, infiniteRepeatable(tween(aura.periodMs, easing = LinearEasing), RepeatMode.Restart), label = "auraAngle",
        ).value
    } else 0f
    val fr = java.util.Locale.getDefault().language == "fr"
    val label = when (aura) {
        Aura.Missed -> if (fr) "appel manqué" else "missed call"
        else -> if (fr) "non lu" else "unread"
    }
    return this
        .semantics { stateDescription = label }
        .drawBehind {
            val gap = 3.dp.toPx()
            val stroke = 2.5.dp.toPx()
            val grow = (size.minDimension + 2 * (gap + stroke / 2)) / size.minDimension
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = when (outline) {
                is Outline.Generic -> outline.path
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            }
            rotate(angle) {
                scale(grow, pivot = Offset(size.width / 2, size.height / 2)) {
                    drawPath(path, color.copy(alpha = 0.7f), style = Stroke(stroke / grow))
                }
            }
        }
}
