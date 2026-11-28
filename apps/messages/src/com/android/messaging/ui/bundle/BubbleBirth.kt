package com.android.messaging.ui.bundle

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import bundle.core.appearance.AppearanceStore

/**
 * Bundle 0.8: "the bubble that is born". A message you just sent rises from the field where it
 * was typed, grows from a smaller shape and settles in the thread with a light bounce, instead of
 * simply appearing. Only for a message sent in the last few seconds (not when scrolling back),
 * only with "Expressive interface" on, and never when animations are off.
 */
@Composable
fun Modifier.bubbleBirth(key: String, outgoing: Boolean, sentAt: Long): Modifier {
    val appearance by AppearanceStore.get(LocalContext.current).state.collectAsState()
    val resolver = LocalContext.current.contentResolver
    val reduced = remember(resolver) {
        runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    // Decided once, when the bubble first shows: fresh and mine.
    val born = remember(key) {
        appearance.expressiveUi && !reduced && outgoing && System.currentTimeMillis() - sentAt in 0..FRESH_MS
    }
    if (!born) return this
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 300f)) }
    val rise = with(LocalDensity.current) { 72.dp.toPx() }
    return graphicsLayer {
        val p = progress.value
        transformOrigin = TransformOrigin(1f, 1f)
        translationY = (1f - p) * rise
        scaleX = 0.6f + 0.4f * p
        scaleY = 0.6f + 0.4f * p
        alpha = p.coerceIn(0f, 1f)
    }
}

/** How recent a sent message must be to be "born" on screen. */
private const val FRESH_MS = 5_000L
