package app.people.contacts.util

import android.content.Context
import bundle.core.telephony.foldForSearch as coreFoldForSearch
import bundle.core.telephony.formatNumber as coreFormatNumber
import bundle.core.telephony.hasPermission as coreHasPermission
import bundle.core.telephony.initialOf as coreInitialOf
import bundle.core.telephony.numberKey as coreNumberKey

// Bundle: these helpers now live in core/telephony, shared with Phone and Messages, so a
// number matches the same contact in every app. The names are kept from upstream.

/** Whether the app has been given [permission] (for example, access to contacts). */
fun Context.hasPermission(permission: String): Boolean = coreHasPermission(permission)

/** A phone number spaced and grouped the way the phone's region writes them, or as saved if it can't be. */
fun formatNumber(number: String): String = coreFormatNumber(number)

/** The key that says two numbers are the same line, however each is written (see core/telephony). */
fun numberKey(number: String): String = coreNumberKey(number)

/** The name's first letter, capitalised, for avatars and letter groups; null if it has no letters. */
fun initialOf(name: String?): String? = coreInitialOf(name)

/** Lower case without accents, so "jose" finds "José". */
fun foldForSearch(text: String): String = coreFoldForSearch(text)
