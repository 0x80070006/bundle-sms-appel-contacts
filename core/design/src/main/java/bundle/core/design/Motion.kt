package bundle.core.design

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

/**
 * The bundle's motion language, the same in Contacts, Phone and Messages (0.7, "Interface
 * expressive"). Three springs: [fast] for small feedback (a press, a toggle), [settle] for things
 * that move into place, [bouncy] for the few moments meant to be felt (answer button, aura).
 * Everything falls back to [snap] when the system has animations turned off.
 */
object BundleMotion {
    fun <T> fast(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    fun <T> settle(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
}

/** True when the user turned animations off (Accessibility > Remove animations, or scale 0). */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}

/** Whether the expressive extras (aura, weight changes) are on: the Appearance switch, and motion allowed. */
@Composable
fun expressiveMotion(): Boolean = LocalAppearance.current.expressiveUi && !rememberReducedMotion()

/**
 * A font weight for a list row that is [unread] or not: bold when unread, thinning out smoothly
 * once read. Google Sans Flex is variable, so every step of 100 renders. Without the expressive
 * switch, it simply jumps between the two weights.
 */
@Composable
fun unreadWeight(unread: Boolean, read: FontWeight = FontWeight.Normal, bold: FontWeight = FontWeight.Bold): FontWeight {
    val target = if (unread) bold.weight else read.weight
    val spec = if (expressiveMotion()) BundleMotion.settle<Int>() else snap()
    val w by animateIntAsState(target, spec, label = "unreadWeight")
    return FontWeight(((w + 50) / 100 * 100).coerceIn(100, 900))
}
