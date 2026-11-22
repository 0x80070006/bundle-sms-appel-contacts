package app.dialtone.phone.util

import android.content.Context
import android.text.format.DateUtils
import bundle.core.telephony.formatDuration as coreFormatDuration
import bundle.core.telephony.formatNumber as coreFormatNumber
import bundle.core.telephony.hasPermission as coreHasPermission
import bundle.core.telephony.initialOf as coreInitialOf
import bundle.core.telephony.numberKey as coreNumberKey

// Bundle: the number helpers now live in core/telephony, shared with Contacts and Messages, so
// a number matches the same contact in every app. The names are kept from upstream.

fun Context.hasPermission(permission: String): Boolean = coreHasPermission(permission)

fun formatNumber(number: String): String = coreFormatNumber(number)

/** The key that says two numbers are the same line, across call log, contacts and voicemail (see core/telephony). */
fun numberKey(number: String): String = coreNumberKey(number)

fun formatDuration(seconds: Long): String = coreFormatDuration(seconds)

fun formatTime(context: Context, millis: Long): String =
    DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)

fun formatShortDate(context: Context, millis: Long): String =
    if (DateUtils.isToday(millis)) formatTime(context, millis)
    else DateUtils.formatDateTime(
        context, millis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME,
    )

enum class DayBucket(private val labelEn: String) {
    Today("Today"), Yesterday("Yesterday"), Older("Older");

    /** Bundle: translated when shown. */
    val label: String get() = tr(labelEn)
}

fun dayBucket(millis: Long): DayBucket = when {
    DateUtils.isToday(millis) -> DayBucket.Today
    DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> DayBucket.Yesterday
    else -> DayBucket.Older
}

fun initialOf(name: String?): String? = coreInitialOf(name)
