package com.android.messaging.ui.common.components.participant

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.PhoneLookup
import java.util.concurrent.ConcurrentHashMap

/**
 * Bundle: the contact a phone number or email belongs to, to show that contact's cartoon avatar
 * (the one Contacts and Phone show) when it has no photo. Cached; the cache empties whenever the
 * contacts change. Null when no contact matches or contacts can't be read.
 */
internal object ContactIdLookup {
    private const val NONE = -1L
    private val cache = ConcurrentHashMap<String, Long>()
    @Volatile private var watching = false

    fun contactId(context: Context, destination: String): Long? {
        watch(context)
        cache[destination]?.let { return it.takeIf { id -> id != NONE } }
        val id = runCatching {
            val email = destination.contains('@')
            val uri = Uri.withAppendedPath(
                if (email) Email.CONTENT_LOOKUP_URI else PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(destination),
            )
            context.contentResolver.query(
                uri, arrayOf(if (email) Email.CONTACT_ID else PhoneLookup._ID), null, null, null,
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        }.getOrNull()
        cache[destination] = id ?: NONE
        return id
    }

    private fun watch(context: Context) {
        if (watching) return
        synchronized(this) {
            if (watching) return
            runCatching {
                context.applicationContext.contentResolver.registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI, true,
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) = cache.clear()
                    },
                )
            }
            watching = true
        }
    }
}
