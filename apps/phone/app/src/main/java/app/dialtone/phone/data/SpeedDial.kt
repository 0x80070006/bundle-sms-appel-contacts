package app.dialtone.phone.data

import android.content.Context

/**
 * Bundle: speed dial. Keys 2 to 9 of the keypad each hold one number (1 is voicemail).
 * On the keypad: type a number and hold a key to assign it; hold the key with nothing typed
 * to call it.
 */
object SpeedDial {
    private const val PREFS = "speed_dial"
    val KEYS = '2'..'9'

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, key: Char): String? =
        if (key in KEYS) prefs(context).getString(key.toString(), null)?.takeIf { it.isNotBlank() } else null

    fun set(context: Context, key: Char, number: String?) {
        if (key !in KEYS) return
        prefs(context).edit().apply {
            if (number.isNullOrBlank()) remove(key.toString()) else putString(key.toString(), number.trim())
        }.apply()
    }

    fun all(context: Context): Map<Char, String> = KEYS.mapNotNull { k -> get(context, k)?.let { k to it } }.toMap()
}
