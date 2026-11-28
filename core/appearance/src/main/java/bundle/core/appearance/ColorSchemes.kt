package bundle.core.appearance

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min

/** Whether the theme is dark for [mode], given the system's setting. */
fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.System -> systemDark
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/**
 * The colour scheme for [appearance]: wallpaper colours (Android 12+), or a full Material scheme
 * built from the palette's seed colour, the way Material You builds one from a wallpaper: tinted
 * neutrals, a primary in the seed's hue, a calmer secondary and a tertiary a third of the way
 * round the colour wheel. Pure black darkens the dark theme's pages to black, keeping a few steps
 * just above it so cards still read as cards.
 */
fun bundleColorScheme(context: Context, appearance: Appearance, dark: Boolean): ColorScheme {
    val seed = appearance.seed
    val base = when {
        seed == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> schemeFromSeed(seed ?: Palette.Ocean.seed!!.toInt(), dark)
    }
    val black = if (dark && appearance.pureBlack) base.pureBlack() else base
    return black.withElements(appearance.elements)
}

/**
 * Bundle: whether the app is dark: a dark theme (space, city at night…) is dark whatever the
 * mode, so its art and text always match.
 */
fun Appearance.isDarkWith(systemDark: Boolean): Boolean =
    if (themePack != ThemePack.None && themePack.dark) true else themeMode.isDark(systemDark)

/** Bundle: the colours the user set for single elements, put over the scheme. */
private fun ColorScheme.withElements(e: ElementColors): ColorScheme {
    if (e.isEmpty) return this
    var s = this
    if (e.accent != 0) {
        val a = Color(e.accent)
        s = s.copy(primary = a, onPrimary = contentColorFor(a), primaryContainer = a.copy(alpha = 0.85f).compositeOverOpaque(s.surface), onPrimaryContainer = contentColorFor(a))
    }
    if (e.background != 0) {
        val b = Color(e.background)
        s = s.copy(background = b, surface = b, surfaceContainerLow = b, onBackground = contentColorFor(b), onSurface = contentColorFor(b))
    }
    if (e.text != 0) {
        val t = Color(e.text)
        s = s.copy(onBackground = t, onSurface = t)
    }
    return s
}

private fun Color.compositeOverOpaque(under: Color): Color {
    val a = alpha
    return Color(red * a + under.red * (1 - a), green * a + under.green * (1 - a), blue * a + under.blue * (1 - a), 1f)
}

/** A colour's hue (0–360) and saturation (0–1), from HSL. */
private fun hueSat(argb: Int): Pair<Float, Float> {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2
    val d = mx - mn
    if (d == 0f) return 0f to 0f
    val s = d / (1 - kotlin.math.abs(2 * l - 1))
    val h = when (mx) {
        r -> 60 * (((g - b) / d) % 6)
        g -> 60 * ((b - r) / d + 2)
        else -> 60 * ((r - g) / d + 4)
    }
    return ((h + 360) % 360) to s.coerceIn(0f, 1f)
}

/**
 * One tone of a hue: [tone] 0 (black) to 100 (white), like Material's tonal palettes. Very light
 * and very dark tones lose saturation so they don't look neon.
 */
private fun tone(hue: Float, sat: Float, tone: Int): Color {
    val l = tone / 100f
    val edge = 1f - kotlin.math.abs(l - 0.5f) * 1.2f
    return Color.hsl(hue, (sat * edge).coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

private fun schemeFromSeed(seed: Int, dark: Boolean): ColorScheme {
    val (h, s0) = hueSat(seed)
    val s = s0.coerceIn(0.38f, 0.78f)
    val p = { t: Int -> tone(h, s, t) }
    val sec = { t: Int -> tone(h, s * 0.35f, t) }
    val ter = { t: Int -> tone((h + 60) % 360, s * 0.55f, t) }
    val n = { t: Int -> tone(h, 0.06f, t) }
    val nv = { t: Int -> tone(h, 0.12f, t) }
    return if (!dark) {
        lightColorScheme(
            primary = p(40), onPrimary = p(100), primaryContainer = p(90), onPrimaryContainer = p(10),
            inversePrimary = p(80),
            secondary = sec(40), onSecondary = sec(100), secondaryContainer = sec(90), onSecondaryContainer = sec(10),
            tertiary = ter(40), onTertiary = ter(100), tertiaryContainer = ter(90), onTertiaryContainer = ter(10),
            background = n(98), onBackground = n(10), surface = n(98), onSurface = n(10),
            surfaceVariant = nv(90), onSurfaceVariant = nv(30), surfaceTint = p(40),
            inverseSurface = n(20), inverseOnSurface = n(95),
            outline = nv(50), outlineVariant = nv(80), scrim = Color.Black,
            surfaceBright = n(98), surfaceDim = n(87), surfaceContainerLowest = n(100),
            surfaceContainerLow = n(96), surfaceContainer = n(94), surfaceContainerHigh = n(92),
            surfaceContainerHighest = n(90),
        )
    } else {
        darkColorScheme(
            primary = p(80), onPrimary = p(20), primaryContainer = p(30), onPrimaryContainer = p(90),
            inversePrimary = p(40),
            secondary = sec(80), onSecondary = sec(20), secondaryContainer = sec(30), onSecondaryContainer = sec(90),
            tertiary = ter(80), onTertiary = ter(20), tertiaryContainer = ter(30), onTertiaryContainer = ter(90),
            background = n(6), onBackground = n(90), surface = n(6), onSurface = n(90),
            surfaceVariant = nv(30), onSurfaceVariant = nv(80), surfaceTint = p(80),
            inverseSurface = n(90), inverseOnSurface = n(20),
            outline = nv(60), outlineVariant = nv(30), scrim = Color.Black,
            surfaceBright = n(24), surfaceDim = n(6), surfaceContainerLowest = n(4),
            surfaceContainerLow = n(10), surfaceContainer = n(12), surfaceContainerHigh = n(17),
            surfaceContainerHighest = n(22),
        )
    }
}

/** Dark pages in true black, with near-black steps for cards and bars. */
private fun ColorScheme.pureBlack(): ColorScheme {
    fun lift(amount: Float) = Color(
        red = primary.red * amount * 0.25f + amount * 0.75f,
        green = primary.green * amount * 0.25f + amount * 0.75f,
        blue = primary.blue * amount * 0.25f + amount * 0.75f,
    )
    return copy(
        background = Color.Black, surface = Color.Black, surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black, surfaceContainerLow = Color.Black,
        surfaceContainer = lift(0.05f), surfaceContainerHigh = lift(0.09f),
        surfaceContainerHighest = lift(0.13f), surfaceBright = lift(0.11f),
    )
}

/** Readable text colour on [background]: near-black or white. */
fun contentColorFor(background: Color): Color {
    val c = background.toArgb()
    val lum = (0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)) / 255
    return if (lum > 0.6) Color(0xFF1B1B1F) else Color.White
}
