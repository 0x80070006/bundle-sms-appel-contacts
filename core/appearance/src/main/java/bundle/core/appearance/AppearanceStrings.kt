package bundle.core.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * The Appearance screen's text in French and English. Kept in code rather than resources because
 * Messages compiles these sources into its own build (with its own R class).
 */
class AppearanceStrings(val fr: Boolean) {
    private fun t(fr: String, en: String) = if (this.fr) fr else en

    val appearance = t("Apparence", "Appearance")
    val back = t("Retour", "Back")
    val sharedNote = t(
        "Ces réglages s'appliquent à Contacts, Téléphone et Messages.",
        "These settings apply to Contacts, Phone and Messages.",
    )
    val theme = t("Thème", "Theme")
    val themeSystem = t("Système", "System")
    val themeLight = t("Clair", "Light")
    val themeDark = t("Sombre", "Dark")
    val colors = t("Couleurs", "Colours")
    val colorsSummary = t("Celles du fond d'écran, une palette ou la vôtre", "From your wallpaper, a palette, or your own")
    val wallpaperColors = t("Fond d'écran", "Wallpaper")
    val customColor = t("Personnalisée", "Custom")
    val hue = t("Teinte", "Hue")
    val saturation = t("Intensité", "Intensity")
    val pureBlack = t("Noir pur", "Pure black")
    val pureBlackSummary = t("Fonds noirs en thème sombre, pour les écrans OLED", "Black backgrounds in the dark theme, for OLED screens")
    val avatars = t("Photos des contacts", "Contact pictures")
    val avatarCircle = t("Cercles", "Circles")
    val avatarExpressive = t("Formes", "Shapes")
    val language = t("Langue", "Language")
    val languageSystem = t("Téléphone", "Phone's")
    val languageNote = t("Les trois apps changent de langue ensemble.", "All three apps switch language together.")

    val appIcon = t("Icône de l'app", "App icon")
    val appIconNote = t(
        "Aperçu en direct. Après « Appliquer », l'écran d'accueil peut mettre quelques secondes à changer l'icône, et un raccourci déjà posé peut devoir être remis.",
        "Live preview. After Apply, the home screen can take a few seconds to change the icon, and a shortcut already placed may need adding again.",
    )
    val apply = t("Appliquer", "Apply")

    /** An icon colour's name; null is the app's own colour. */
    fun iconColor(c: IconColor?): String = when (c) {
        null -> t("D'origine", "Original")
        IconColor.Black -> t("Noir", "Black")
        IconColor.Blue -> t("Bleu", "Blue")
        IconColor.Green -> t("Vert", "Green")
        IconColor.Orange -> t("Orange", "Orange")
        IconColor.Red -> t("Rouge", "Red")
        IconColor.Pink -> t("Rose", "Pink")
        IconColor.Purple -> t("Violet", "Purple")
        IconColor.Indigo -> t("Indigo", "Indigo")
        IconColor.Teal -> t("Sarcelle", "Teal")
        IconColor.Graphite -> t("Graphite", "Graphite")
    }

    fun palette(p: Palette): String = when (p) {
        Palette.Wallpaper -> wallpaperColors
        Palette.Ocean -> t("Océan", "Ocean")
        Palette.Lagoon -> t("Lagon", "Lagoon")
        Palette.Forest -> t("Forêt", "Forest")
        Palette.Olive -> t("Olive", "Olive")
        Palette.Amber -> t("Ambre", "Amber")
        Palette.Sunset -> t("Couchant", "Sunset")
        Palette.Coral -> t("Corail", "Coral")
        Palette.Rose -> t("Rose", "Rose")
        Palette.Lavender -> t("Lavande", "Lavender")
        Palette.Indigo -> t("Indigo", "Indigo")
        Palette.Graphite -> t("Graphite", "Graphite")
        Palette.Custom -> customColor
    }
}

/** The strings in the app's current language (French for any French locale, else English). */
@Composable
@ReadOnlyComposable
fun appearanceStrings(): AppearanceStrings =
    AppearanceStrings(LocalConfiguration.current.locales[0]?.language == "fr")
