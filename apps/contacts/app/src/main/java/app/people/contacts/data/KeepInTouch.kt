package app.people.contacts.data

import android.content.Context
import android.provider.CallLog
import android.provider.Telephony
import app.people.contacts.util.numberKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bundle: "Keep in touch". The people the user chose (favourites) whom they haven't called or
 * texted for a while, worked out on the phone from the call log and the texts; nothing leaves it.
 */
data class KeepInTouchEntry(val contact: ContactSummary, val lastContact: Long?) {
    val number: String? get() = contact.numbers.firstOrNull()
}

/** How long without news before a favourite shows up. */
const val KEEP_IN_TOUCH_DAYS = 21

/**
 * The favourites to get in touch with: no call or text for [days] days (or never), longest
 * first, at most [max]. [lastContact] maps a contact id to its last call or text.
 */
internal fun keepInTouch(
    favourites: List<ContactSummary>,
    lastContact: Map<Long, Long>,
    now: Long,
    days: Int = KEEP_IN_TOUCH_DAYS,
    max: Int = 3,
): List<KeepInTouchEntry> {
    val limit = now - days * 86_400_000L
    return favourites.filter { it.numbers.isNotEmpty() }
        .map { KeepInTouchEntry(it, lastContact[it.id]) }
        .filter { (it.lastContact ?: 0L) < limit }
        .sortedBy { it.lastContact ?: 0L }
        .take(max)
}

/** Reads the last call or text with each favourite. Empty without call log and SMS access. */
suspend fun loadKeepInTouch(context: Context, contacts: List<ContactSummary>): List<KeepInTouchEntry> = withContext(Dispatchers.IO) {
    val favourites = contacts.filter { it.starred }
    if (favourites.isEmpty() || !hasCallAndTextAccess(context)) return@withContext emptyList()
    val byNumber = HashMap<String, Long>()
    favourites.forEach { c -> c.numbers.forEach { n -> numberKey(n).takeIf { it.length >= 7 }?.let { byNumber[it] = c.id } } }
    val last = HashMap<Long, Long>()
    fun see(number: String?, time: Long) {
        val id = number?.let { byNumber[numberKey(it)] } ?: return
        if (time > (last[id] ?: 0L)) last[id] = time
    }
    runCatching {
        context.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE), null, null, null)
            ?.use { c -> while (c.moveToNext()) see(c.getString(0), c.getLong(1)) }
    }
    runCatching {
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE), null, null, null)
            ?.use { c -> while (c.moveToNext()) see(c.getString(0), c.getLong(1)) }
    }
    keepInTouch(favourites, last, System.currentTimeMillis())
}
