package bundle.core.appearance

import org.json.JSONObject

/** Light, dark, or as the system says. */
enum class ThemeMode { System, Light, Dark }

/**
 * Where the colours come from: the wallpaper (Material You), one of the built-in palettes, or
 * a colour the user picked ([Appearance.customSeed]).
 */
enum class Palette(val seed: Long?) {
    Wallpaper(null),
    Ocean(0xFF2F6FDB),
    Lagoon(0xFF00859B),
    Forest(0xFF2E7D4F),
    Olive(0xFF6B7F1F),
    Amber(0xFFC98400),
    Sunset(0xFFE0612B),
    Coral(0xFFE0485A),
    Rose(0xFFD03E86),
    Lavender(0xFF7A5BD3),
    Indigo(0xFF4652C9),
    Graphite(0xFF5D6774),
    Custom(null),
}

/** Contact pictures: circles everywhere, or Material 3 Expressive shapes everywhere. */
enum class AvatarStyle { Circle, Expressive }

/** This app's language (each app has its own): the phone's, or French or English whatever the phone uses. */
enum class AppLanguage(val tag: String) { System(""), French("fr"), English("en") }

/**
 * The bundle's look, the same in Contacts, Phone and Messages: changed in one app, it is sent to
 * the other two (see [AppearanceStore]), except [language], which stays this app's. [revision] is when it was last changed, so the newest
 * copy wins.
 */
data class Appearance(
    val themeMode: ThemeMode = ThemeMode.System,
    val palette: Palette = Palette.Wallpaper,
    /** The colour picked for [Palette.Custom], as ARGB. */
    val customSeed: Int = 0xFF2F6FDB.toInt(),
    /** In the dark theme, pages are true black (OLED screens). */
    val pureBlack: Boolean = false,
    val avatarStyle: AvatarStyle = AvatarStyle.Circle,
    val language: AppLanguage = AppLanguage.System,
    /** "Interface expressive": living avatar outlines, weights that change when read, springier motion. */
    val expressiveUi: Boolean = false,
    val revision: Long = 0,
) {
    /** The seed colour in use, or null for wallpaper colours. */
    val seed: Int?
        get() = when (palette) {
            Palette.Wallpaper -> null
            Palette.Custom -> customSeed
            else -> palette.seed!!.toInt()
        }

    fun toJson(): String = JSONObject()
        .put("themeMode", themeMode.name)
        .put("palette", palette.name)
        .put("customSeed", customSeed)
        .put("pureBlack", pureBlack)
        .put("avatarStyle", avatarStyle.name)
        .put("language", language.name)
        .put("expressiveUi", expressiveUi)
        .put("revision", revision)
        .toString()

    companion object {
        /** Reads [toJson]'s output; unknown or missing values fall back to the defaults. */
        fun fromJson(json: String?): Appearance? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(json)
                val d = Appearance()
                Appearance(
                    themeMode = enumOr(o.optString("themeMode"), d.themeMode),
                    palette = enumOr(o.optString("palette"), d.palette),
                    customSeed = o.optInt("customSeed", d.customSeed),
                    pureBlack = o.optBoolean("pureBlack", d.pureBlack),
                    avatarStyle = enumOr(o.optString("avatarStyle"), d.avatarStyle),
                    language = enumOr(o.optString("language"), d.language),
                    expressiveUi = o.optBoolean("expressiveUi", d.expressiveUi),
                    revision = o.optLong("revision", 0),
                )
            }.getOrNull()
        }

        private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: fallback
    }
}
