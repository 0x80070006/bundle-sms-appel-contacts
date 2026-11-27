package bundle.core.telephony

/**
 * Bundle: finds the one-time code in a text ("Votre code est 482913", "G-123456 is your Google
 * verification code"). Null when the text doesn't look like a code message, so a bank balance
 * or a phone number is never offered as a code.
 */
object Otp {
    private val KEYWORDS = Regex(
        "(code|otp|verification|vérification|validation|confirm|pin|mot de passe|password|passcode|authenti|sécurité|security|connexion|login|2fa)",
        RegexOption.IGNORE_CASE,
    )
    // 4 to 8 digits, optionally split once by a space or dash ("123 456", "G-123456").
    private val CODE = Regex("""(?<![\d+/.,:])(?:[A-Z]-)?(\d{3,4}[ -]?\d{3,4}|\d{4,8})(?!\d|[/,:]\d|\.\d|\s?(€|eur|\$|%|h\b))""", RegexOption.IGNORE_CASE)

    fun find(text: String?): String? {
        if (text.isNullOrBlank() || text.length > 400) return null
        if (!KEYWORDS.containsMatchIn(text)) return null
        val code = CODE.findAll(text).map { it.groupValues[1].filter(Char::isDigit) }
            .firstOrNull { it.length in 4..8 }
        return code
    }
}
