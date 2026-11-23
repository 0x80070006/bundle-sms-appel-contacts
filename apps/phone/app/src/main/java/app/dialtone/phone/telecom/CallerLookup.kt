package app.dialtone.phone.telecom

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import java.util.concurrent.ConcurrentHashMap

/** Resolves a phone number to a contact name/photo, cached for the process lifetime. */
object CallerLookup {

    /** Bundle: [contactId] picks the contact's cartoon avatar, the same as in Contacts. */
    data class Info(val name: String?, val photoUri: String?, val contactId: Long? = null)

    private val cache = ConcurrentHashMap<String, Info>()

    fun lookup(context: Context, number: String): Info {
        if (number.isBlank()) return Info(null, null)
        cache[number]?.let { return it }
        val info = runCatching {
            val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(
                uri, arrayOf(PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_THUMBNAIL_URI, PhoneLookup._ID), null, null, null,
            )?.use { c -> if (c.moveToFirst()) Info(c.getString(0), c.getString(1), c.getLong(2)) else null }
        }.getOrNull() ?: Info(null, null)
        cache[number] = info
        return info
    }
}
