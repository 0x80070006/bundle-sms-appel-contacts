package bundle.core.hub

import android.content.Context
import bundle.core.telephony.numberKey
import org.json.JSONObject

/**
 * The hub's own storage, in the app that hosts it (Contacts). Other apps never touch it
 * directly: they go through [HubProvider] via [Hub].
 */
internal class HubStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bundle_hub", Context.MODE_PRIVATE)

    @Synchronized
    fun line(number: String): LinePrefs {
        val key = numberKey(number).ifEmpty { return LinePrefs() }
        return LinePrefs.fromJson(prefs.getString("line:$key", null)?.let { runCatching { JSONObject(it) }.getOrNull() })
    }

    @Synchronized
    fun putLine(number: String, value: LinePrefs) {
        val key = numberKey(number).ifEmpty { return }
        prefs.edit().apply {
            if (value.isEmpty) remove("line:$key") else putString("line:$key", value.toJson().toString())
        }.apply()
    }

    @Synchronized
    fun labelColors(): Map<String, Int> = LabelColors.fromJson(prefs.getString("label_colors", null))

    @Synchronized
    fun putLabelColor(label: String, color: Int?) {
        val next = labelColors().toMutableMap().apply { if (color == null) remove(label) else put(label, color) }
        prefs.edit().putString("label_colors", LabelColors.toJson(next)).apply()
    }
}
