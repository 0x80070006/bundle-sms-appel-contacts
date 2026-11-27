package bundle.core.telephony

/**
 * Bundle: the number ranges ARCEP reserves for telemarketing in metropolitan France (decision
 * 2022-1583): 0162, 0163, 0270, 0271, 0377, 0378, 0424, 0425, 0568, 0569, 0948, 0949.
 * Calls from them can be sent to voicemail or rejected (Phone › Settings › Spam).
 */
object SpamRules {
    val TELEMARKETING_PREFIXES = listOf("162", "163", "270", "271", "377", "378", "424", "425", "568", "569", "948", "949")

    /** Whether [number] ("01 62 …", "+33 1 62 …", "0033162…") is in a telemarketing range. */
    fun isFrenchTelemarketing(number: String): Boolean {
        val digits = number.filter { it.isDigit() }
        val national = when {
            number.trimStart().startsWith("+33") -> digits.removePrefix("33")
            digits.startsWith("0033") -> digits.removePrefix("0033")
            digits.startsWith("0") && digits.length == 10 -> digits.substring(1)
            else -> return false
        }
        return national.length == 9 && TELEMARKETING_PREFIXES.any { national.startsWith(it) }
    }
}
