package bundle.core.hub

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import org.json.JSONObject

/**
 * Reads and writes the bundle's shared data from any of the three apps. Every app calls the
 * provider hosted by Contacts (Contacts too, so there is one path). When Contacts isn't
 * installed, reads return empty values and writes do nothing.
 * Every call may cross processes: run off the main thread.
 */
object Hub {
    const val ACTION = "bundle.action.HUB"
    internal const val GET_LINE = "getLine"
    internal const val PUT_LINE = "putLine"
    internal const val GET_LABEL_COLORS = "getLabelColors"
    internal const val EXTRA_JSON = "json"

    /** The hub's authority: ours if this app hosts it, else the sibling's (same key). */
    private fun authority(context: Context): String? {
        val pm = context.packageManager
        return runCatching {
            pm.queryIntentContentProviders(Intent(ACTION), 0)
                .mapNotNull { it.providerInfo }
                .firstOrNull {
                    it.packageName == context.packageName ||
                        pm.checkSignatures(it.packageName, context.packageName) == PackageManager.SIGNATURE_MATCH
                }?.authority
        }.getOrNull()
    }

    private fun call(context: Context, method: String, arg: String?, extras: Bundle? = null): Bundle? {
        val auth = authority(context) ?: return null
        return runCatching { context.contentResolver.call(Uri.parse("content://$auth"), method, arg, extras) }.getOrNull()
    }

    fun line(context: Context, number: String): LinePrefs =
        LinePrefs.fromJson(call(context, GET_LINE, number)?.getString(EXTRA_JSON)?.let { runCatching { JSONObject(it) }.getOrNull() })

    fun putLine(context: Context, number: String, prefs: LinePrefs): Boolean =
        call(context, PUT_LINE, number, Bundle().apply { putString(EXTRA_JSON, prefs.toJson().toString()) }) != null

    fun setSim(context: Context, number: String, simId: String?) =
        putLine(context, number, line(context, number).copy(simId = simId))

    /** Bundle: silences calls from [number] until [until] (ms); 0 lifts it. */
    fun setSilentUntil(context: Context, number: String, until: Long) =
        putLine(context, number, line(context, number).copy(silentUntil = until))

    fun addNote(context: Context, number: String, text: String, time: Long = System.currentTimeMillis()): Boolean {
        if (text.isBlank()) return false
        val current = line(context, number)
        return putLine(context, number, current.copy(notes = listOf(HubNote(time, text.trim())) + current.notes))
    }

    fun removeNote(context: Context, number: String, note: HubNote) {
        val current = line(context, number)
        putLine(context, number, current.copy(notes = current.notes - note))
    }

    fun labelColors(context: Context): Map<String, Int> =
        LabelColors.fromJson(call(context, GET_LABEL_COLORS, null)?.getString(EXTRA_JSON))

    /** Only Contacts (the host) changes label colours: they belong to its labels. */
    fun setLabelColor(context: Context, label: String, color: Int?) = HubStore(context).putLabelColor(label, color)

    /** In Contacts (the host): a label's colour, read straight from the store. */
    fun localLabelColor(context: Context, label: String): Int? = HubStore(context).labelColors()[label]

    /** Whether this app can reach the hub (Contacts installed, signed with the same key). */
    fun available(context: Context): Boolean = authority(context) != null
}
