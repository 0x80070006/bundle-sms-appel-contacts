package bundle.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Bundle 0.8: the morphing answer button. Drag it up: it turns from a cookie into a pill as it
 * goes, and the phone ticks once at the point where letting go answers. Let go past that point
 * to answer; before it, it springs back. A tap still answers (and TalkBack's double tap too), so
 * nobody depends on the gesture.
 */
@Composable
fun MorphAnswerButton(
    label: String,
    container: Color,
    onAnswer: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    reach: Dp = 110.dp,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    val reachPx = with(LocalDensity.current) { reach.toPx() }
    val drag = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    val morph = remember { Morph(MaterialShapes.Cookie9Sided.normalized(), MaterialShapes.Pill.normalized()) }
    val progress = (-drag.value / reachPx).coerceIn(0f, 1f)

    Box(
        modifier
            .offset { IntOffset(0, drag.value.roundToInt()) }
            .size(size)
            .clip(MorphShape(morph, progress))
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick(label) { onAnswer(); true }
            }
            .clickable(onClick = onAnswer)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    scope.launch {
                        drag.snapTo((drag.value + delta).coerceIn(-reachPx, 0f))
                        val nowArmed = -drag.value >= reachPx * 0.8f
                        if (nowArmed && !armed) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        armed = nowArmed
                    }
                },
                onDragStopped = {
                    if (armed) onAnswer()
                    armed = false
                    drag.animateTo(0f, if (reduced) snap() else BundleMotion.bouncy())
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size).background(container), contentAlignment = Alignment.Center) { content() }
    }
}

/** A [Morph] drawn at [progress] (0 = start shape, 1 = end shape), scaled to the box. */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress).asComposePath()
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}
