package bundle.core.appearance

/**
 * Bundle: the themes. Each one is a whole look: colours (a seed for the Material scheme, and
 * whether it is a dark theme), the colours of its drawn background, the text colour that reads
 * on it, the message bubbles, and the texture behind conversations. The art itself is drawn in
 * code ([ThemeArt]), never a bundled photo, so it stays sharp at any size and weighs nothing.
 */
enum class ThemePack(
    /** Seed of the Material colour scheme. */
    val seed: Long,
    /** The theme is meant dark (space, city at night…): the app follows it whatever the system mode. */
    val dark: Boolean,
    /** Background gradient, top to bottom. */
    val sky: Long,
    val ground: Long,
    /** The two colours the art is drawn with. */
    val art: Long,
    val artSoft: Long,
    /** Text over the art (titles drawn straight on the background). */
    val ink: Long,
    /** Message bubbles: mine and theirs, with their text. */
    val bubbleMine: Long,
    val onBubbleMine: Long,
    val bubbleTheirs: Long,
    val onBubbleTheirs: Long,
    /** Texture behind a conversation. */
    val texture: Texture,
) {
    None(0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, Texture.None),
    Forest(0xFF2E7D4F, true, 0xFF0E2A1C, 0xFF07140D, 0xFF1F5A38, 0xFF2F7A4C, 0xFFE8F3EA, 0xFF2E7D4F, 0xFFFFFFFF, 0xFFF3E9DC, 0xFF2B1D12, Texture.Wood),
    Ocean(0xFF0F6FA8, false, 0xFFCDEBFA, 0xFF2C7FB8, 0xFF4A9CD0, 0xFF8CC8EA, 0xFF06283D, 0xFF0F6FA8, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF06283D, Texture.Waves),
    Space(0xFF5B5FD8, true, 0xFF0B0B22, 0xFF02020A, 0xFFB9BEFF, 0xFF6E5BD8, 0xFFEDEBFF, 0xFF5B5FD8, 0xFFFFFFFF, 0xFF1C1B3A, 0xFFEDEBFF, Texture.Stars),
    Desert(0xFFC2702A, false, 0xFFFBE7C6, 0xFFE3A866, 0xFFD08A45, 0xFFEBC08A, 0xFF3D2410, 0xFFB35F1F, 0xFFFFFFFF, 0xFFFFF6EA, 0xFF3D2410, Texture.Sand),
    Mountain(0xFF4E6A86, false, 0xFFE2ECF4, 0xFF9DB4C8, 0xFF5D7894, 0xFFB7C9D9, 0xFF14212E, 0xFF4E6A86, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF14212E, Texture.Stone),
    Aurora(0xFF1FA58A, true, 0xFF071A24, 0xFF030B10, 0xFF3BE0B0, 0xFF8A6BFF, 0xFFE4FFF8, 0xFF138A72, 0xFFFFFFFF, 0xFF11262F, 0xFFE4FFF8, Texture.Ice),
    Sakura(0xFFC9578A, false, 0xFFFFEEF4, 0xFFF7D3E1, 0xFFE58DB2, 0xFF8A5A44, 0xFF3A1626, 0xFFC9578A, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF3A1626, Texture.Paper),
    City(0xFFE0A030, true, 0xFF101528, 0xFF05070F, 0xFF242B45, 0xFFF2C14E, 0xFFF6F1E6, 0xFFB57E14, 0xFFFFFFFF, 0xFF1D2236, 0xFFF6F1E6, Texture.Brick),
    Autumn(0xFFC8501E, true, 0xFF1A120A, 0xFF0A0704, 0xFFD8582A, 0xFFE0A030, 0xFFFFF1E2, 0xFFB5441A, 0xFFFFFFFF, 0xFFF4E7D7, 0xFF2E1A0D, Texture.Wood),
    Zen(0xFF6F7F5A, false, 0xFFF1EFE6, 0xFFDAD6C6, 0xFF7E8A66, 0xFFB9B39C, 0xFF262A1E, 0xFF6F7F5A, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF262A1E, Texture.Raked),
    ;

    /** Texture of a conversation's background. */
    enum class Texture { None, Wood, Waves, Stars, Sand, Stone, Ice, Paper, Brick, Raked }
}

/** Where the theme shows: the app's home screen only, or everywhere (conversations, cards…). */
enum class ThemeScope { Home, Everywhere }

/**
 * Bundle: the colour of each element, chosen by the user over the theme or palette. 0 means
 * "the theme's own".
 */
data class ElementColors(
    val accent: Int = 0,
    val background: Int = 0,
    val text: Int = 0,
    val bubbleMine: Int = 0,
    val bubbleTheirs: Int = 0,
) {
    val isEmpty get() = accent == 0 && background == 0 && text == 0 && bubbleMine == 0 && bubbleTheirs == 0
}
