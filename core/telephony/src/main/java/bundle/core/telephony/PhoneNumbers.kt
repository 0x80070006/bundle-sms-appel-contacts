package bundle.core.telephony

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.util.Locale

/** Whether the app has been given [permission] (for example, access to contacts). */
fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** A phone number spaced and grouped the way the phone's region writes them, or as saved if it can't be. */
fun formatNumber(number: String): String {
    if (number.isBlank()) return number
    return PhoneNumberUtils.formatNumber(number, Locale.getDefault().country) ?: number
}

/**
 * The key that says two numbers are the same line, however each is written: "+33 6 12 34 56 78",
 * "06 12 34 56 78" and "0033612345678" share one. Used across the call log, contacts,
 * voicemail and conversations; every app of the bundle must use this one, or the same person
 * stops matching from one app to the next.
 *
 * Numbers are put in international (E.164) form for the phone's region, then reduced to their
 * digits. Upstream Contacts and Dialtone kept only the last 10 digits, which matches North
 * American numbers but not French ones: "+33 6…" has 11 digits and "06…" 10, so the last 10
 * differ. Short codes (under 7 digits) and numbers Android can't parse keep that old rule.
 */
fun numberKey(number: String): String = numberKey(number, ::deviceE164)

internal fun numberKey(number: String, toE164: (String) -> String?): String {
    val digits = number.filter { it.isDigit() }
    if (digits.length < 7) return digits
    toE164(number)?.let { e164 -> return e164.filter { it.isDigit() } }
    return if (digits.length > 10) digits.takeLast(10) else digits
}

private val e164Cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 4096
}

/** [number] in E.164 for the phone's region, or null if Android can't parse it. Cached: list matching calls this a lot. */
private fun deviceE164(number: String): String? {
    synchronized(e164Cache) { e164Cache[number]?.let { return it.ifEmpty { null } } }
    val region = Locale.getDefault().country.ifEmpty { return null }
    val e164 = runCatching { PhoneNumberUtils.formatNumberToE164(number, region) }.getOrNull()
    synchronized(e164Cache) { e164Cache[number] = e164.orEmpty() }
    return e164
}

/** The name's first letter, capitalised, for avatars and letter groups; null if it has no letters. */
fun initialOf(name: String?): String? =
    name?.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString()

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/** Lower case without accents, so "jose" finds "José". */
fun foldForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase(Locale.getDefault())

/** A call's length as m:ss, or h:mm:ss from an hour. */
fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
