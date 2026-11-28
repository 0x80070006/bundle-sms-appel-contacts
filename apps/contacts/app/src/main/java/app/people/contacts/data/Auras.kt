package app.people.contacts.data

import android.content.Context
import android.provider.CallLog
import android.provider.Telephony
import androidx.compose.runtime.staticCompositionLocalOf
import app.people.contacts.util.numberKey
import bundle.core.design.Aura
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bundle: which contacts get an aura on their avatar ("Expressive interface"): a missed call in
 * the last 24 hours, or a text not read yet. Read on the phone from the call log and the texts;
 * empty without access. A missed call wins over an unread text.
 */
suspend fun loadAuras(context: Context, contacts: List<ContactSummary>): Map<Long, Aura> = withContext(Dispatchers.IO) {
    if (contacts.isEmpty() || !hasCallAndTextAccess(context)) return@withContext emptyMap()
    val byNumber = HashMap<String, Long>()
    contacts.forEach { c -> c.numbers.forEach { n -> numberKey(n).takeIf { it.length >= 7 }?.let { byNumber.putIfAbsent(it, c.id) } } }
    val out = HashMap<Long, Aura>()
    runCatching {
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS), "${Telephony.Sms.READ} = 0", null, null,
        )?.use { c -> while (c.moveToNext()) byNumber[numberKey(c.getString(0) ?: continue)]?.let { out[it] = Aura.Unread } }
    }
    val since = System.currentTimeMillis() - 86_400_000L
    runCatching {
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER),
            "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} > ?", arrayOf(CallLog.Calls.MISSED_TYPE.toString(), since.toString()), null,
        )?.use { c -> while (c.moveToNext()) byNumber[numberKey(c.getString(0) ?: continue)]?.let { out[it] = Aura.Missed } }
    }
    out
}

/** The auras of the list's contacts, provided by the app's root. */
val LocalContactAuras = staticCompositionLocalOf<Map<Long, Aura>> { emptyMap() }
