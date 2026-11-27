package app.people.contacts.data

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import app.people.contacts.util.hasPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Bundle: one encrypted file with everything the three apps know (see [SuiteArchive]): every
 * contact (vCard), the call log, the texts, the hub (notes, preferred SIMs, label colours) and
 * the emergency information.
 *
 * Restoring puts back contacts, hub and emergency information. Android only lets the default
 * phone app write the call log and the default SMS app write texts, so those are kept in the
 * file (readable on a computer, unzipped) but not written back by Contacts.
 */
object SuiteBackup {
    data class Summary(val contacts: Int, val calls: Int, val texts: Int)

    suspend fun export(context: Context, out: Uri, passphrase: CharArray): Summary = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val keys = lookupKeys(app)
        val vcf = ByteArrayOutputStream().use { VCardWriter.export(app, keys, it); it.toByteArray() }
        val calls = callLog(app)
        val texts = sms(app)
        val entries = linkedMapOf(
            "meta.json" to JSONObject()
                .put("format", 1).put("created", System.currentTimeMillis())
                .put("contacts", keys.size).put("calls", calls.length()).put("sms", texts.length())
                .toString(2).toByteArray(),
            "contacts.vcf" to vcf,
            "calls.json" to calls.toString().toByteArray(),
            "sms.json" to texts.toString().toByteArray(),
            "hub.json" to prefsJson(app, "bundle_hub").toString().toByteArray(),
            "emergency.json" to prefsJson(app, "emergency").toString().toByteArray(),
        )
        val sealed = SuiteArchive.pack(entries, passphrase)
        app.contentResolver.openOutputStream(out, "wt")?.use { it.write(sealed) } ?: error("cannot write")
        Summary(keys.size, calls.length(), texts.length())
    }

    /** Opens a backup; [addContacts] gets the vCard text and returns how many were added. */
    suspend fun restore(
        context: Context,
        from: Uri,
        passphrase: CharArray,
        addContacts: suspend (String) -> Int,
    ): Summary = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val bytes = app.contentResolver.openInputStream(from)?.use { it.readBytes() } ?: error("cannot read")
        val entries = SuiteArchive.unpack(bytes, passphrase)
        entries["hub.json"]?.let { restorePrefs(app, "bundle_hub", String(it)) }
        entries["emergency.json"]?.let { restorePrefs(app, "emergency", String(it)) }
        val added = entries["contacts.vcf"]?.let { addContacts(String(it)) } ?: 0
        val calls = entries["calls.json"]?.let { runCatching { JSONArray(String(it)).length() }.getOrNull() } ?: 0
        val texts = entries["sms.json"]?.let { runCatching { JSONArray(String(it)).length() }.getOrNull() } ?: 0
        Summary(added, calls, texts)
    }

    private fun lookupKeys(context: Context): List<String> = runCatching {
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts.LOOKUP_KEY), null, null, null,
        )?.use { c -> buildList { while (c.moveToNext()) c.getString(0)?.let(::add) } }
    }.getOrNull().orEmpty()

    private fun callLog(context: Context): JSONArray {
        val out = JSONArray()
        if (!context.hasPermission(Manifest.permission.READ_CALL_LOG)) return out
        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
                null, null, "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext()) out.put(
                    JSONObject().put("number", c.getString(0)).put("type", c.getInt(1)).put("date", c.getLong(2)).put("duration", c.getLong(3)),
                )
            }
        }
        return out
    }

    private fun sms(context: Context): JSONArray {
        val out = JSONArray()
        if (!context.hasPermission(Manifest.permission.READ_SMS)) return out
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.TYPE, Telephony.Sms.DATE, Telephony.Sms.BODY, Telephony.Sms.READ),
                null, null, "${Telephony.Sms.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext()) out.put(
                    JSONObject().put("address", c.getString(0)).put("type", c.getInt(1)).put("date", c.getLong(2))
                        .put("body", c.getString(3)).put("read", c.getInt(4)),
                )
            }
        }
        return out
    }

    private fun prefsJson(context: Context, name: String): JSONObject = JSONObject().apply {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) -> if (v is String) put(k, v) }
    }

    private fun restorePrefs(context: Context, name: String, json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        val edit = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
        o.keys().forEach { k -> edit.putString(k, o.optString(k)) }
        edit.apply()
    }
}
