package bundle.core.design

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb

/**
 * Bundle 0.8: the ambient call screen. Behind the caller, two soft colour fields (the colours of
 * their avatar) drift slowly, like light through water. Android 13+ draws it with a small AGSL
 * shader on the GPU; before that, or with animations turned off, it is a still gradient. Kept
 * faint (a fraction of the page colour) so the name, the timer and the buttons stay readable.
 */
@Composable
fun AmbientCallBackground(first: Color, second: Color, page: Color, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !reduced) {
        AmbientShader(first, second, page, modifier)
    } else {
        Canvas(modifier.fillMaxSize()) {
            drawRect(page)
            drawRect(Brush.radialGradient(listOf(first.copy(alpha = 0.35f), Color.Transparent), center = center.copy(y = size.height * 0.3f), radius = size.width))
            drawRect(Brush.radialGradient(listOf(second.copy(alpha = 0.25f), Color.Transparent), center = center.copy(y = size.height * 0.85f), radius = size.width))
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AmbientShader(first: Color, second: Color, page: Color, modifier: Modifier) {
    val shader = remember { RuntimeShader(AMBIENT_AGSL) }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { time = (it - start) / 1000f }
    }
    Canvas(modifier.fillMaxSize()) {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("time", time)
        shader.setColorUniform("first", first.toArgb())
        shader.setColorUniform("second", second.toArgb())
        shader.setColorUniform("page", page.toArgb())
        drawRect(ShaderBrush(shader))
    }
}

/** Two slow colour fields over the page colour; amplitude kept low for legibility. */
private const val AMBIENT_AGSL = """
uniform float2 size;
uniform float time;
layout(color) uniform half4 first;
layout(color) uniform half4 second;
layout(color) uniform half4 page;

half4 main(float2 xy) {
    float2 uv = xy / size;
    float t = time * 0.12;
    float2 a = float2(0.5 + 0.28 * sin(t * 1.3), 0.32 + 0.12 * cos(t * 0.9));
    float2 b = float2(0.5 + 0.30 * cos(t * 0.7 + 1.7), 0.80 + 0.10 * sin(t * 1.1));
    float wave = 0.04 * sin(uv.y * 9.0 + t * 3.0) * sin(uv.x * 7.0 - t * 2.0);
    float fa = smoothstep(0.75, 0.0, distance(uv + wave, a));
    float fb = smoothstep(0.70, 0.0, distance(uv - wave, b));
    half4 c = page;
    c = mix(c, first, fa * 0.22);
    c = mix(c, second, fb * 0.16);
    return c;
}
"""
