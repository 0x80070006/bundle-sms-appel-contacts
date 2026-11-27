package bundle.core.hub

import org.json.JSONArray
import org.json.JSONObject

/** A note written about a call or a person, at [time] (milliseconds). */
data class HubNote(val time: Long, val text: String)

/**
 * What the bundle remembers about one phone line (keyed by core/telephony's numberKey, so
 * "06 12…" and "+33 6 12…" are the same): the SIM to call it from, and notes.
 */
data class LinePrefs(
    /** The PhoneAccountHandle id of the preferred SIM, or null for the phone's default. */
    val simId: String? = null,
    val notes: List<HubNote> = emptyList(),
) {
    val isEmpty get() = simId == null && notes.isEmpty()

    fun toJson(): JSONObject = JSONObject().apply {
        simId?.let { put("sim", it) }
        put("notes", JSONArray().apply { notes.forEach { put(JSONObject().put("t", it.time).put("x", it.text)) } })
    }

    companion object {
        fun fromJson(o: JSONObject?): LinePrefs {
            if (o == null) return LinePrefs()
            val arr = o.optJSONArray("notes") ?: JSONArray()
            return LinePrefs(
                simId = o.optString("sim").ifEmpty { null },
                notes = (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { HubNote(it.optLong("t"), it.optString("x")) }
                }.filter { it.text.isNotBlank() }.sortedByDescending { it.time },
            )
        }
    }
}

/** Colours for labels ("Family", "Work"…): a profile is a label with a colour. ARGB ints. */
object LabelColors {
    val PALETTE = listOf(
        0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(),
        0xFF00897B.toInt(), 0xFF1E88E5.toInt(), 0xFF5E35B1.toInt(), 0xFFD81B60.toInt(),
    )

    fun toJson(colors: Map<String, Int>): String = JSONObject().apply { colors.forEach { (k, v) -> put(k, v) } }.toString()

    fun fromJson(json: String?): Map<String, Int> {
        val o = runCatching { JSONObject(json ?: return emptyMap()) }.getOrNull() ?: return emptyMap()
        return o.keys().asSequence().associateWith { o.optInt(it) }
    }
}
