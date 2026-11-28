package bundle.core.sync

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** One value of a repeating field (a number, an email…). [key] says when two values are the same one. */
data class Item(val key: String, val value: String, val type: Int = 0, val label: String? = null)

/**
 * What the sync knows about a contact, in two shapes so that one generic merge handles every
 * field: single values ([scalars], blank ones left out) and sets of values ([sets], by kind and
 * then by [Item.key]).
 */
data class Fields(
    val scalars: Map<String, String> = emptyMap(),
    val sets: Map<String, Map<String, Item>> = emptyMap(),
) {
    fun scalar(name: String): String = scalars[name].orEmpty()
    fun items(kind: String): Collection<Item> = sets[kind]?.values.orEmpty()
    fun keys(kind: String): Set<String> = sets[kind]?.keys.orEmpty()

    /** Whether it has a real name: not just a number, which is what a contact saved by number looks like. */
    val hasName: Boolean
        get() = NAME_PARTS.any { name -> scalar(name).let { it.isNotBlank() && !looksLikeNumber(it) } }

    /** Worth syncing: something other than an empty shell. */
    val hasContent: Boolean
        get() = NAME_PARTS.any { scalar(it).isNotBlank() } || SETS.any { sets[it].orEmpty().isNotEmpty() && it != "labels" }

    /** The name in a form that ignores case, accents, order and spacing. */
    val nameKey: String
        get() = listOf("first", "middle", "last").map { scalar(it) }.filter { it.isNotBlank() }.ifEmpty { listOf(scalar("nickname"), scalar("company")) }
            .flatMap { fold(it).split(' ') }.filter { it.isNotEmpty() }.sorted().joinToString(" ")

    companion object {
        val SCALARS = listOf(
            "prefix", "first", "middle", "last", "suffix", "phoneticFirst", "phoneticMiddle", "phoneticLast",
            "nickname", "company", "title", "note", "starred",
        )
        val SETS = listOf("phones", "emails", "addresses", "events", "websites", "relations", "sips", "chats", "labels")
        val NAME_PARTS = listOf("prefix", "first", "middle", "last", "suffix", "nickname", "company")

        /** Builds fields, trimming values and dropping blank ones, so equal contacts are equal. */
        fun of(scalars: Map<String, String> = emptyMap(), sets: Map<String, List<Item>> = emptyMap()): Fields =
            Fields(
                scalars.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() },
                sets.mapValues { (kind, items) ->
                    items.filter { it.value.isNotBlank() && it.key.isNotEmpty() }.associateBy { it.key }
                }.filterValues { it.isNotEmpty() },
            )

        fun itemKey(kind: String, value: String): String = when (kind) {
            "phones" -> phoneKey(value)
            "emails" -> value.trim().lowercase(Locale.ROOT)
            else -> fold(value)
        }

        fun item(kind: String, value: String, type: Int = 0, label: String? = null) =
            Item(itemKey(kind, value), value.trim(), type, label?.trim()?.ifEmpty { null })

        /** The digits of a number, last 9 only: "+33 6 11 22 33 44" and "06 11 22 33 44" are one number. */
        fun phoneKey(number: String): String {
            val digits = number.filter { it.isDigit() }
            return if (digits.length > 9) digits.takeLast(9) else digits
        }

        fun fold(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
                .lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

        fun looksLikeNumber(text: String): Boolean =
            text.count { it.isDigit() } >= 3 && text.all { it.isDigit() || it in "+-.()  " }
    }
}

/** A contact in a device's file: [uid] is the same on every phone; [updatedAt] when this phone noticed the last change. */
data class Record(val uid: String, val fields: Fields, val updatedAt: Long)

/** Everything one phone publishes: its contacts and the contacts it deleted (uid → when). */
data class DeviceFile(
    val deviceId: String,
    val deviceName: String,
    val writtenAt: Long,
    val records: List<Record>,
    val tombstones: Map<String, Long>,
) {
    fun toBytes(): ByteArray = JSONObject()
        .put("v", 1).put("device", deviceId).put("name", deviceName).put("at", writtenAt)
        .put("records", JSONArray(records.map { it.toJson() }))
        .put("tombstones", JSONObject(tombstones.mapValues { it.value }))
        .toString().toByteArray(Charsets.UTF_8)

    companion object {
        const val LABEL = "bundle-sync device file"

        /** Bundle: the label a phone's own file is sealed with, so it only opens as that phone's file. */
        fun labelFor(deviceId: String) = "$LABEL:$deviceId"

        fun fromBytes(bytes: ByteArray): DeviceFile {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            if (o.optInt("v", 1) > 1) throw UnsupportedFormatException("device file format ${o.optInt("v")}")
            val records = o.optJSONArray("records") ?: JSONArray()
            val tombs = o.optJSONObject("tombstones") ?: JSONObject()
            return DeviceFile(
                o.getString("device"), o.optString("name"), o.optLong("at"),
                (0 until records.length()).map { recordFromJson(records.getJSONObject(it)) },
                tombs.keys().asSequence().associateWith { tombs.getLong(it) },
            )
        }
    }
}

internal fun Record.toJson(): JSONObject = JSONObject().put("uid", uid).put("at", updatedAt).put("f", fields.toJson())

internal fun recordFromJson(o: JSONObject): Record = Record(o.getString("uid"), fieldsFromJson(o.getJSONObject("f")), o.optLong("at"))

internal fun Fields.toJson(): JSONObject {
    val sets = JSONObject()
    this.sets.forEach { (kind, items) ->
        sets.put(
            kind,
            JSONArray(
                items.values.sortedBy { it.key }.map {
                    JSONObject().put("k", it.key).put("v", it.value).put("t", it.type).also { j -> it.label?.let { l -> j.put("l", l) } }
                },
            ),
        )
    }
    return JSONObject().put("s", JSONObject(scalars.toSortedMap())).put("c", sets)
}

internal fun fieldsFromJson(o: JSONObject): Fields {
    val s = o.optJSONObject("s") ?: JSONObject()
    val c = o.optJSONObject("c") ?: JSONObject()
    return Fields.of(
        s.keys().asSequence().associateWith { s.getString(it) },
        c.keys().asSequence().associateWith { kind ->
            val array = c.getJSONArray(kind)
            (0 until array.length()).map {
                val j = array.getJSONObject(it)
                Item(j.getString("k"), j.getString("v"), j.optInt("t"), if (j.has("l")) j.getString("l") else null)
            }
        },
    )
}
