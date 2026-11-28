package bundle.core.appearance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Bundle: the themes' art, drawn in code: vector shapes (pines, waves, stars, dunes…) sized to
 * the screen, the same every time (a fixed random seed). A [veil] of the page colour goes over
 * it so text stays readable; cards and bubbles keep their opaque colours on top.
 */
@Composable
fun ThemeBackdrop(pack: ThemePack, veil: Color, modifier: Modifier = Modifier, conversation: Boolean = false) {
    if (pack == ThemePack.None) return
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            if (conversation) drawThemeTexture(pack) else drawThemeBackground(pack)
            drawRect(veil)
        }
    }
}

/** The veil over a theme's art: strong enough that body text keeps a 4.5:1 contrast. */
fun themeVeil(page: Color, conversation: Boolean): Color = page.copy(alpha = if (conversation) 0.35f else 0.55f)

private fun c(argb: Long, alpha: Float = 1f) = Color(argb).copy(alpha = alpha)

/** The theme's home-screen background. */
fun DrawScope.drawThemeBackground(pack: ThemePack) {
    drawRect(Brush.verticalGradient(listOf(c(pack.sky), c(pack.ground))))
    val r = Random(pack.ordinal * 7919)
    val w = size.width
    val h = size.height
    when (pack) {
        ThemePack.None -> Unit
        ThemePack.Forest -> {
            for (layer in 0 until 3) {
                val base = h * (0.55f + layer * 0.17f)
                val tone = if (layer == 2) c(pack.art) else c(pack.artSoft, 0.35f + layer * 0.2f)
                var x = -w * 0.05f
                while (x < w * 1.05f) {
                    val tall = h * (0.18f + r.nextFloat() * 0.14f) * (1f - layer * 0.15f)
                    pine(x, base, tall, tall * 0.42f, tone)
                    x += tall * (0.35f + r.nextFloat() * 0.25f)
                }
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, c(0xFFFFFFFF, 0.08f), Color.Transparent), startY = h * 0.5f, endY = h * 0.75f))
        }
        ThemePack.Ocean -> {
            drawCircle(c(0xFFFFF4D6, 0.9f), radius = w * 0.12f, center = Offset(w * 0.75f, h * 0.18f))
            for (i in 0 until 5) waves(h * (0.45f + i * 0.12f), h * 0.02f + i * 4f, 1.5f + i * 0.4f, c(if (i % 2 == 0) pack.art else pack.artSoft, 0.35f + i * 0.12f))
        }
        ThemePack.Space -> {
            drawRect(Brush.radialGradient(listOf(c(pack.artSoft, 0.45f), Color.Transparent), center = Offset(w * 0.2f, h * 0.3f), radius = w * 0.9f))
            repeat(220) { drawCircle(c(0xFFFFFFFF, 0.2f + r.nextFloat() * 0.8f), radius = 0.6f + r.nextFloat() * 2.2f, center = Offset(r.nextFloat() * w, r.nextFloat() * h)) }
            val p = Offset(w * 0.72f, h * 0.68f)
            val pr = w * 0.18f
            drawCircle(Brush.linearGradient(listOf(c(pack.art), c(pack.artSoft)), start = p - Offset(pr, pr), end = p + Offset(pr, pr)), radius = pr, center = p)
            rotate(-18f, p) { drawOval(c(pack.art, 0.7f), topLeft = Offset(p.x - pr * 1.7f, p.y - pr * 0.28f), size = Size(pr * 3.4f, pr * 0.56f), style = Stroke(width = 3f)) }
        }
        ThemePack.Desert -> {
            drawCircle(c(0xFFFFF8E6, 0.95f), radius = w * 0.14f, center = Offset(w * 0.3f, h * 0.2f))
            for (i in 0 until 4) dune(h * (0.5f + i * 0.13f), c(if (i % 2 == 0) pack.art else pack.artSoft, 0.5f + i * 0.15f), r)
        }
        ThemePack.Mountain -> {
            for (layer in 0 until 3) {
                val tone = c(if (layer == 2) pack.art else pack.artSoft, 0.45f + layer * 0.25f)
                ridge(h * (0.45f + layer * 0.15f), h * (0.25f - layer * 0.05f), tone, snow = layer == 0, r = r)
            }
        }
        ThemePack.Aurora -> {
            repeat(120) { drawCircle(c(0xFFFFFFFF, 0.2f + r.nextFloat() * 0.6f), radius = 0.6f + r.nextFloat() * 1.6f, center = Offset(r.nextFloat() * w, r.nextFloat() * h * 0.6f)) }
            for (i in 0 until 3) curtain(h * (0.15f + i * 0.08f), h * 0.35f, if (i == 1) c(pack.artSoft, 0.35f) else c(pack.art, 0.4f), i)
            ridge(h * 0.8f, h * 0.08f, c(0xFF020608), snow = false, r = r)
        }
        ThemePack.Sakura -> {
            val branch = c(pack.artSoft, 0.8f)
            drawLine(branch, Offset(-10f, h * 0.15f), Offset(w * 0.7f, h * 0.05f), strokeWidth = 10f)
            drawLine(branch, Offset(w * 0.35f, h * 0.11f), Offset(w * 0.55f, h * 0.22f), strokeWidth = 6f)
            drawLine(branch, Offset(w * 0.55f, h * 0.07f), Offset(w * 0.9f, h * 0.16f), strokeWidth = 5f)
            repeat(70) { petal(Offset(r.nextFloat() * w, r.nextFloat() * h), 6f + r.nextFloat() * 10f, r.nextFloat() * 360f, c(pack.art, 0.35f + r.nextFloat() * 0.5f)) }
        }
        ThemePack.City -> {
            drawCircle(c(0xFFFFF3D0, 0.9f), radius = w * 0.07f, center = Offset(w * 0.8f, h * 0.14f))
            var x = 0f
            while (x < w) {
                val bw = w * (0.06f + r.nextFloat() * 0.08f)
                val bh = h * (0.2f + r.nextFloat() * 0.35f)
                drawRect(c(pack.art), topLeft = Offset(x, h - bh), size = Size(bw, bh))
                var wy = h - bh + 10f
                while (wy < h - 10f) {
                    var wx = x + 6f
                    while (wx < x + bw - 10f) {
                        if (r.nextFloat() < 0.35f) drawRect(c(pack.artSoft, 0.4f + r.nextFloat() * 0.5f), topLeft = Offset(wx, wy), size = Size(5f, 7f))
                        wx += 11f
                    }
                    wy += 14f
                }
                x += bw + 2f
            }
        }
        ThemePack.Autumn -> {
            // Seen from above, like a forest in October: canopies of red, orange, gold and green.
            val tones = listOf(0xFFD8582A, 0xFFE0A030, 0xFFB5441A, 0xFF6E8B2A, 0xFF2F5A2E, 0xFFC9781E, 0xFF8E2A1E)
            repeat(140) {
                val center = Offset(r.nextFloat() * w, r.nextFloat() * h)
                val radius = w * (0.04f + r.nextFloat() * 0.09f)
                val tone = tones[r.nextInt(tones.size)]
                drawCircle(Brush.radialGradient(listOf(c(tone, 0.95f), c(tone, 0.55f), Color.Transparent), center = center, radius = radius), radius = radius, center = center)
            }
        }
        ThemePack.Zen -> {
            val center = Offset(w * 0.68f, h * 0.62f)
            for (i in 1..14) drawCircle(c(pack.artSoft, 0.55f), radius = w * 0.06f * i, center = center, style = Stroke(width = 2f))
            drawOval(c(pack.art), topLeft = center - Offset(w * 0.09f, w * 0.06f), size = Size(w * 0.18f, w * 0.12f))
            drawOval(c(pack.art, 0.85f), topLeft = Offset(w * 0.12f, h * 0.3f), size = Size(w * 0.1f, w * 0.07f))
            for (y in 0 until 8) waves(h * (0.15f + y * 0.03f), 3f, 3f, c(pack.artSoft, 0.4f), stroke = 1.5f)
        }
    }
}

/** The texture behind a conversation: quieter than the home art, so bubbles stand out. */
fun DrawScope.drawThemeTexture(pack: ThemePack) {
    val r = Random(pack.ordinal * 104729)
    val w = size.width
    val h = size.height
    when (pack.texture) {
        ThemePack.Texture.None -> Unit
        ThemePack.Texture.Wood -> {
            drawRect(c(0xFF3B2716))
            var y = 0f
            while (y < h) {
                val thick = 1f + r.nextFloat() * 2.5f
                val tone = if (r.nextBoolean()) c(0xFF5A3B22, 0.6f) else c(0xFF24160C, 0.55f)
                waves(y, 2f + r.nextFloat() * 6f, 0.6f + r.nextFloat(), tone, stroke = thick, fill = false)
                y += 5f + r.nextFloat() * 9f
            }
            repeat(4) {
                val k = Offset(r.nextFloat() * w, r.nextFloat() * h)
                for (ring in 1..5) drawOval(c(0xFF24160C, 0.5f), topLeft = k - Offset(ring * 9f, ring * 4f), size = Size(ring * 18f, ring * 8f), style = Stroke(1.5f))
            }
        }
        ThemePack.Texture.Waves -> {
            drawRect(c(0xFFE3F3FC))
            var y = 0f
            while (y < h) { waves(y, 4f, 3f, c(pack.artSoft, 0.35f), stroke = 1.5f, fill = false); y += 18f }
        }
        ThemePack.Texture.Stars -> {
            drawRect(Brush.verticalGradient(listOf(c(0xFF0B0B22), c(0xFF05050F))))
            repeat(160) { drawCircle(c(0xFFFFFFFF, 0.15f + r.nextFloat() * 0.5f), radius = 0.5f + r.nextFloat() * 1.5f, center = Offset(r.nextFloat() * w, r.nextFloat() * h)) }
        }
        ThemePack.Texture.Sand -> {
            drawRect(c(0xFFF3DFC0))
            repeat(2500) { drawCircle(c(if (r.nextBoolean()) 0xFFC9A070 else 0xFFFFF4E2, 0.35f), radius = 0.7f + r.nextFloat(), center = Offset(r.nextFloat() * w, r.nextFloat() * h)) }
        }
        ThemePack.Texture.Stone -> {
            drawRect(c(0xFFDCE4EA))
            var y = 0f
            while (y < h) { waves(y, 6f, 0.7f, c(0xFF9DB0C0, 0.4f), stroke = 1f + r.nextFloat() * 3f, fill = false); y += 10f + r.nextFloat() * 22f }
        }
        ThemePack.Texture.Ice -> {
            drawRect(Brush.verticalGradient(listOf(c(0xFF0E2830), c(0xFF061418))))
            repeat(40) {
                var p = Offset(r.nextFloat() * w, r.nextFloat() * h)
                repeat(4) { val n = p + Offset((r.nextFloat() - 0.5f) * 120f, (r.nextFloat() - 0.5f) * 120f); drawLine(c(0xFF3BE0B0, 0.18f), p, n, 1.2f); p = n }
            }
        }
        ThemePack.Texture.Paper -> {
            drawRect(c(0xFFFFF6F9))
            repeat(500) {
                val p = Offset(r.nextFloat() * w, r.nextFloat() * h)
                val a = r.nextFloat() * 2 * PI.toFloat()
                drawLine(c(0xFFE58DB2, 0.18f), p, p + Offset(kotlin.math.cos(a) * 14f, sin(a) * 14f), 1f)
            }
        }
        ThemePack.Texture.Brick -> {
            drawRect(c(0xFF151A2C))
            val bw = 64f
            val bh = 26f
            var row = 0
            var y = 0f
            while (y < h) {
                var x = if (row % 2 == 0) 0f else -bw / 2
                while (x < w) { drawRect(c(0xFF20263D), topLeft = Offset(x + 2f, y + 2f), size = Size(bw - 4f, bh - 4f)); x += bw }
                y += bh; row++
            }
        }
        ThemePack.Texture.Raked -> {
            drawRect(c(0xFFEDEAE0))
            var y = 0f
            while (y < h) { waves(y, 3f, 2f, c(0xFFB9B39C, 0.5f), stroke = 1.5f, fill = false); y += 9f }
        }
    }
}

private fun DrawScope.pine(x: Float, base: Float, tall: Float, wide: Float, color: Color) {
    val path = Path()
    for (tier in 0 until 3) {
        val top = base - tall + tier * tall * 0.28f
        val bottom = base - tall * 0.35f + tier * tall * 0.32f
        val half = wide * (0.5f + tier * 0.25f)
        path.moveTo(x, top)
        path.lineTo(x + half, bottom)
        path.lineTo(x - half, bottom)
        path.close()
    }
    drawPath(path, color)
    drawRect(color, topLeft = Offset(x - wide * 0.06f, base - tall * 0.05f), size = Size(wide * 0.12f, tall * 0.1f))
}

/** A band of waves at [y]; filled down to the bottom unless [fill] is false. */
private fun DrawScope.waves(y: Float, amplitude: Float, cycles: Float, color: Color, stroke: Float = 0f, fill: Boolean = stroke == 0f) {
    val w = size.width
    val path = Path()
    path.moveTo(0f, y)
    var x = 0f
    while (x <= w) {
        path.lineTo(x, y + sin(x / w * cycles * 2 * PI).toFloat() * amplitude)
        x += max(4f, w / 120f)
    }
    if (fill) {
        path.lineTo(w, size.height)
        path.lineTo(0f, size.height)
        path.close()
        drawPath(path, color)
    } else {
        drawPath(path, color, style = Stroke(stroke))
    }
}

private fun DrawScope.dune(y: Float, color: Color, r: Random) {
    val w = size.width
    val path = Path()
    path.moveTo(0f, y + r.nextFloat() * 40f)
    path.quadraticTo(w * (0.25f + r.nextFloat() * 0.2f), y - 80f - r.nextFloat() * 60f, w * 0.6f, y)
    path.quadraticTo(w * 0.85f, y + 40f, w, y - 30f)
    path.lineTo(w, size.height)
    path.lineTo(0f, size.height)
    path.close()
    drawPath(path, color)
}

private fun DrawScope.ridge(base: Float, height: Float, color: Color, snow: Boolean, r: Random) {
    val w = size.width
    val path = Path()
    path.moveTo(0f, base)
    val peaks = mutableListOf<Offset>()
    var x = 0f
    while (x < w) {
        val px = x + w * (0.08f + r.nextFloat() * 0.12f)
        val py = base - height * (0.5f + r.nextFloat() * 0.5f)
        path.lineTo(px, py)
        peaks += Offset(px, py)
        x = px + w * (0.06f + r.nextFloat() * 0.1f)
        path.lineTo(min(x, w), base - height * 0.15f)
    }
    path.lineTo(w, size.height)
    path.lineTo(0f, size.height)
    path.close()
    drawPath(path, color)
    if (snow) peaks.forEach { p ->
        val cap = Path().apply { moveTo(p.x, p.y); lineTo(p.x + 18f, p.y + 22f); lineTo(p.x - 18f, p.y + 22f); close() }
        drawPath(cap, c(0xFFFFFFFF, 0.85f))
    }
}

private fun DrawScope.curtain(top: Float, height: Float, color: Color, phase: Int) {
    val w = size.width
    var x = 0f
    while (x < w) {
        val shift = sin((x / w * 3 + phase) * PI).toFloat() * 40f
        drawLine(Brush.verticalGradient(listOf(Color.Transparent, color, Color.Transparent), startY = top + shift, endY = top + shift + height), Offset(x, top + shift), Offset(x, top + shift + height), strokeWidth = 3f)
        x += 4f
    }
}

private fun DrawScope.petal(at: Offset, size: Float, angle: Float, color: Color) {
    rotate(angle, at) { drawOval(color, topLeft = at - Offset(size / 2, size / 4), size = Size(size, size / 2)) }
}

/**
 * Bundle: a page under the current theme. [home] for an app's home screen, false for the rest
 * (shown only when the theme is set for the whole app); [conversation] draws the conversation
 * texture instead of the home art. [content] gets the colour its own page container should use:
 * transparent over a theme, the usual background otherwise.
 */
@Composable
fun ThemedPage(home: Boolean, conversation: Boolean = false, content: @Composable (pageColor: Color) -> Unit) {
    val appearance by AppearanceStore.get(androidx.compose.ui.platform.LocalContext.current).state.collectAsState()
    val pack = appearance.themePack
    val shown = pack != ThemePack.None && (home || appearance.themeScope == ThemeScope.Everywhere)
    val page = androidx.compose.material3.MaterialTheme.colorScheme.background
    if (!shown) {
        content(page)
        return
    }
    Box(Modifier.fillMaxSize()) {
        ThemeBackdrop(pack, themeVeil(page, conversation), conversation = conversation)
        content(Color.Transparent)
    }
}

/** Bundle: whether a theme shows on this page ([home]: an app's home screen). */
@Composable
fun themeActive(home: Boolean): Boolean {
    val appearance by AppearanceStore.get(androidx.compose.ui.platform.LocalContext.current).state.collectAsState()
    return appearance.themePack != ThemePack.None && (home || appearance.themeScope == ThemeScope.Everywhere)
}
