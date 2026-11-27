package app.people.contacts.data

import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.CallLog
import android.provider.Telephony
import app.people.contacts.util.numberKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What happened: a call (and how it ended) or a text. */
enum class TimelineKind { CallIncoming, CallOutgoing, CallMissed, CallRejected, TextReceived, TextSent }

/** One line of a contact's history: [text] is the message for a text, [seconds] the length of a call. */
data class TimelineEntry(
    val time: Long,
    val kind: TimelineKind,
    val number: String,
    val text: String? = null,
    val seconds: Long = 0,
) {
    val isCall: Boolean get() = kind != TimelineKind.TextReceived && kind != TimelineKind.TextSent
}

/**
 * The calls and texts with one contact in a single list, newest first: the contact's "history",
 * as Samsung's Contacts shows it. Reads the call log and the text store, so it needs both
 * permissions ([hasCallAndTextAccess]); without them it's empty.
 */
class ContactTimeline(private val context: Context) {
    /** The latest [limit] entries with any of [numbers]. */
    suspend fun load(numbers: List<String>, limit: Int = 200): List<TimelineEntry> = withContext(Dispatchers.IO) {
        val keys = numbers.map(::numberKey).filter { it.isNotEmpty() }.toSet()
        if (keys.isEmpty() || !hasCallAndTextAccess(context)) return@withContext emptyList()
        mergeTimeline(loadCalls(keys), loadTexts(keys) + loadMms(keys), limit)
    }

    private fun loadCalls(keys: Set<String>): List<TimelineEntry> {
        val out = ArrayList<TimelineEntry>()
        runCatching {
            query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE, CallLog.Calls.DURATION),
                CallLog.Calls.DATE,
            )?.use { c ->
                var seen = 0
                while (c.moveToNext() && seen++ < SCAN_LIMIT) {
                    val number = c.getString(0)
                    if (!sameLine(number, keys)) continue
                    val kind = when (c.getInt(2)) {
                        CallLog.Calls.INCOMING_TYPE, CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> TimelineKind.CallIncoming
                        CallLog.Calls.OUTGOING_TYPE -> TimelineKind.CallOutgoing
                        CallLog.Calls.MISSED_TYPE -> TimelineKind.CallMissed
                        CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> TimelineKind.CallRejected
                        else -> continue
                    }
                    out += TimelineEntry(c.getLong(1), kind, number.orEmpty(), seconds = c.getLong(3))
                }
            }
        }
        return out
    }

    /**
     * Bundle: MMS (group texts, photos) too. Each MMS keeps its numbers in a separate "addr"
     * table, so only the newest [MMS_SCAN_LIMIT] are looked at.
     */
    private fun loadMms(keys: Set<String>): List<TimelineEntry> {
        val out = ArrayList<TimelineEntry>()
        runCatching {
            query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX),
                Telephony.Mms.DATE,
            )?.use { c ->
                var seen = 0
                while (c.moveToNext() && seen++ < MMS_SCAN_LIMIT) {
                    val id = c.getLong(0)
                    val kind = when (c.getInt(2)) {
                        Telephony.Mms.MESSAGE_BOX_INBOX -> TimelineKind.TextReceived
                        Telephony.Mms.MESSAGE_BOX_SENT, Telephony.Mms.MESSAGE_BOX_OUTBOX -> TimelineKind.TextSent
                        else -> continue
                    }
                    val address = mmsAddresses(id).firstOrNull { sameLine(it, keys) } ?: continue
                    out += TimelineEntry(c.getLong(1) * 1000, kind, address, text = mmsText(id) ?: "MMS")
                }
            }
        }
        return out
    }

    private fun mmsAddresses(id: Long): List<String> = runCatching {
        context.contentResolver.query(
            android.net.Uri.parse("content://mms/$id/addr"), arrayOf(Telephony.Mms.Addr.ADDRESS), null, null, null,
        )?.use { c -> buildList { while (c.moveToNext()) c.getString(0)?.let(::add) } }
    }.getOrNull().orEmpty()

    private fun mmsText(id: Long): String? = runCatching {
        context.contentResolver.query(
            android.net.Uri.parse("content://mms/part"), arrayOf(Telephony.Mms.Part.TEXT),
            "${Telephony.Mms.Part.MSG_ID} = ? AND ${Telephony.Mms.Part.CONTENT_TYPE} = 'text/plain'", arrayOf(id.toString()), null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun loadTexts(keys: Set<String>): List<TimelineEntry> {
        val out = ArrayList<TimelineEntry>()
        runCatching {
            query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.BODY),
                Telephony.Sms.DATE,
            )?.use { c ->
                var seen = 0
                while (c.moveToNext() && seen++ < SCAN_LIMIT) {
                    val address = c.getString(0)
                    if (!sameLine(address, keys)) continue
                    val kind = when (c.getInt(2)) {
                        Telephony.Sms.MESSAGE_TYPE_INBOX -> TimelineKind.TextReceived
                        Telephony.Sms.MESSAGE_TYPE_SENT, Telephony.Sms.MESSAGE_TYPE_OUTBOX -> TimelineKind.TextSent
                        else -> continue
                    }
                    out += TimelineEntry(c.getLong(1), kind, address.orEmpty(), text = c.getString(3))
                }
            }
        }
        return out
    }

    /** Newest first, stopping at [SCAN_LIMIT] rows (the provider ignores a LIMIT in a sort order on recent Android). */
    private fun query(uri: android.net.Uri, projection: Array<String>, dateColumn: String) =
        context.contentResolver.query(
            uri, projection,
            Bundle().apply {
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(dateColumn))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                putInt(ContentResolver.QUERY_ARG_LIMIT, SCAN_LIMIT)
            },
            null,
        )

    private companion object {
        /** How many of the newest rows of each store are looked through. */
        const val SCAN_LIMIT = 3000
        const val MMS_SCAN_LIMIT = 300
    }
}

/** Whether [address] (a number as stored in the call log or on a text) is one of the contact's [keys]. */
internal fun sameLine(address: String?, keys: Set<String>): Boolean {
    val key = numberKey(address ?: return false)
    return key.isNotEmpty() && key in keys
}

/** Calls and texts together, newest first, at most [limit]. */
internal fun mergeTimeline(calls: List<TimelineEntry>, texts: List<TimelineEntry>, limit: Int): List<TimelineEntry> =
    (calls + texts).sortedByDescending { it.time }.take(limit)
