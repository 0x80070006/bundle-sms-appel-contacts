package bundle.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import bundle.core.appearance.Appearance
import bundle.core.appearance.AppearanceStore
import bundle.core.appearance.bundleColorScheme
import bundle.core.appearance.isDark
import bundle.core.appearance.isDarkWith
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Google Sans Flex: one variable font file, one entry per weight (SIL OFL 1.1). */
val GoogleSansFlex: FontFamily = FontFamily(
    listOf(
        FontWeight.Thin, FontWeight.ExtraLight, FontWeight.Light, FontWeight.Normal, FontWeight.Medium,
        FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black,
    ).map { weight -> Font(R.font.google_sans_flex, weight) }
)

/** Every Material type style, re-set in [family]. */
fun Typography.withFontFamily(family: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

/**
 * The one theme of the bundle: wallpaper colours on Material 3 Expressive, Google Sans Flex.
 * Colour roles, the same in every app: pages use background/surface (surfaceContainerLow),
 * the bottom bar keeps its default surfaceContainer, cards use surfaceBright and search bars
 * surfaceContainerHighest.
 */
@Composable
fun BundleTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    // The look the user chose in Appearance (shared by the three apps): theme mode, colours,
    // pure black. [darkTheme] is the system's setting, used when the mode is System.
    val appearance by AppearanceStore.get(context).state.collectAsState()
    val dark = appearance.isDarkWith(darkTheme)
    val scheme = bundleColorScheme(context, appearance, dark)
    val colorScheme = scheme.copy(
        background = scheme.surfaceContainerLow,
        surface = scheme.surfaceContainerLow,
    )
    CompositionLocalProvider(LocalAppearance provides appearance) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = Typography().withFontFamily(GoogleSansFlex),
            content = content,
        )
    }
}

/** The bundle's look, for parts that depend on it beyond colours (avatar shapes). */
val LocalAppearance = staticCompositionLocalOf { Appearance() }

/**
 * Call-green for Call / Answer buttons. Not a Material colour role: a phone convention, shared
 * so a call button looks the same in Contacts, Phone and Messages.
 */
object CallColors {
    @Composable
    fun container(): Color = if (isSystemInDarkTheme()) Color(0xFF6DD58C) else Color(0xFF146C2E)

    @Composable
    fun content(): Color = if (isSystemInDarkTheme()) Color(0xFF00391A) else Color.White
}
