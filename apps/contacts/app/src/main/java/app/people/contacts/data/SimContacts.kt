package app.people.contacts.data

import android.Manifest
import android.content.Context
import android.net.Uri
import app.people.contacts.util.hasPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bundle: the contacts stored on the SIM card (Android's "icc" phone book). They are read only;
 * importing copies them into the phone's contacts, the SIM is left as it is.
 */
object SimContacts {
    data class Entry(val name: String, val number: String)

    private val URIS = listOf("content://icc/adn", "content://icc/adn/subId/1", "content://icc/adn/subId/2")

    suspend fun load(context: Context): List<Entry> = withContext(Dispatchers.IO) {
        if (!context.hasPermission(Manifest.permission.READ_CONTACTS)) return@withContext emptyList()
        val out = LinkedHashSet<Entry>()
        for (uri in URIS) {
            runCatching {
                context.contentResolver.query(Uri.parse(uri), null, null, null, null)?.use { c ->
                    val nameCol = c.getColumnIndex("name")
                    val numberCol = c.getColumnIndex("number")
                    if (numberCol < 0) return@use
                    while (c.moveToNext()) {
                        val number = c.getString(numberCol)?.trim().orEmpty()
                        if (number.isEmpty()) continue
                        val name = (if (nameCol >= 0) c.getString(nameCol) else null)?.trim().orEmpty().ifEmpty { number }
                        out += Entry(name, number)
                    }
                }
            }
        }
        out.toList()
    }

    /** One vCard for the [entries], to import with the usual vCard import. */
    fun toVCards(entries: List<Entry>): String = entries.joinToString("\n") { e ->
        "BEGIN:VCARD\nVERSION:3.0\nFN:${e.name}\nN:;${e.name};;;\nTEL:${e.number}\nEND:VCARD"
    }
}
