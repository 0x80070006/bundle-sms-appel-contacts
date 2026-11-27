package app.dialtone.phone.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.CallLog.Calls
import android.provider.ContactsContract
import android.view.View
import android.widget.RemoteViews
import app.dialtone.phone.MainActivity
import app.dialtone.phone.R
import app.dialtone.phone.telecom.CallActionReceiver
import app.dialtone.phone.util.hasPermission
import app.dialtone.phone.util.tr
import app.dialtone.phone.util.trf

/**
 * Bundle: home-screen widget with up to four favourites (one tap calls them) and the number of
 * missed calls not seen yet (tap opens the call log). Refreshed when it's added, when missed
 * calls change ([MissedCalls][app.dialtone.phone.telecom.MissedCalls]) and when the app opens.
 */
class FavoritesWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try { render(app, manager, ids) } finally { pending.finish() }
        }.start()
    }

    companion object {
        private val SLOTS = intArrayOf(R.id.widget_fav0, R.id.widget_fav1, R.id.widget_fav2, R.id.widget_fav3)

        /** Redraws every widget; call off the main thread. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, FavoritesWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val favorites = favorites(context)
            val missed = missedCount(context)
            val views = RemoteViews(context.packageName, R.layout.bundle_widget_favorites)

            views.setTextViewText(
                R.id.widget_missed,
                when (missed) {
                    0 -> tr("No missed calls")
                    1 -> tr("1 missed call")
                    else -> trf("%d missed calls", missed)
                },
            )
            views.setOnClickPendingIntent(
                R.id.widget_missed,
                PendingIntent.getActivity(
                    context, 10,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )

            SLOTS.forEachIndexed { i, slot ->
                val fav = favorites.getOrNull(i)
                if (fav == null) {
                    views.setViewVisibility(slot, View.INVISIBLE)
                } else {
                    views.setViewVisibility(slot, View.VISIBLE)
                    views.setTextViewText(slot, fav.first)
                    views.setOnClickPendingIntent(
                        slot,
                        PendingIntent.getBroadcast(
                            context, 20 + i,
                            Intent(context, CallActionReceiver::class.java)
                                .setAction(CallActionReceiver.ACTION_CALL_BACK)
                                .putExtra(CallActionReceiver.EXTRA_NUMBER, fav.second),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        ),
                    )
                }
            }
            views.setTextViewText(R.id.widget_empty, if (favorites.isEmpty()) tr("Star contacts to see them here") else "")
            views.setViewVisibility(R.id.widget_empty, if (favorites.isEmpty()) View.VISIBLE else View.GONE)
            manager.updateAppWidget(ids, views)
        }

        /** Starred contacts with a number, as (first name, number). */
        private fun favorites(context: Context): List<Pair<String, String>> {
            if (!context.hasPermission(Manifest.permission.READ_CONTACTS)) return emptyList()
            return runCatching {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.CONTACT_ID),
                    "${ContactsContract.CommonDataKinds.Phone.STARRED} = 1", null,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
                )?.use { c ->
                    val seen = HashSet<Long>()
                    buildList {
                        while (c.moveToNext() && size < 4) {
                            if (!seen.add(c.getLong(2))) continue
                            val name = c.getString(0)?.substringBefore(' ')?.ifBlank { null } ?: c.getString(1)
                            add(name to c.getString(1))
                        }
                    }
                }
            }.getOrNull().orEmpty()
        }

        private fun missedCount(context: Context): Int {
            if (!context.hasPermission(Manifest.permission.READ_CALL_LOG)) return 0
            return runCatching {
                context.contentResolver.query(
                    Calls.CONTENT_URI, arrayOf(Calls._ID),
                    "${Calls.TYPE} = ? AND ${Calls.NEW} = 1", arrayOf(Calls.MISSED_TYPE.toString()), null,
                )?.use { it.count }
            }.getOrNull() ?: 0
        }
    }
}
