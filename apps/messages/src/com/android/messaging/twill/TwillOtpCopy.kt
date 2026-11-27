package com.android.messaging.twill

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.widget.Toast
import androidx.core.app.NotificationCompat
import bundle.core.telephony.Otp
import java.util.Locale

/**
 * Bundle: a "Copy 482913" button on the notification of a text that carries a one-time code
 * (found by core/telephony's [Otp]). The code is copied as sensitive, so Android hides it from
 * the clipboard preview.
 */
internal object TwillOtpCopy {
    const val ACTION = "app.bundle.action.COPY_OTP"
    const val EXTRA_CODE = "app.bundle.extra.OTP"

    private fun fr() = Locale.getDefault().language == "fr"

    @JvmStatic
    fun action(context: Context, conversationId: String, text: CharSequence?): NotificationCompat.Action? {
        val code = Otp.find(text?.toString()) ?: return null
        val intent = Intent(context, TwillOtpCopyReceiver::class.java)
            .setAction(ACTION)
            .setIdentifier("otp-$conversationId")
            .putExtra(EXTRA_CODE, code)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val label = if (fr()) "Copier $code" else "Copy $code"
        return NotificationCompat.Action.Builder(0, label, pending).setShowsUserInterface(false).build()
    }

    fun copy(context: Context, code: String) {
        val clip = ClipData.newPlainText("OTP", code).apply {
            description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
        Toast.makeText(context, if (fr()) "Code copié" else "Code copied", Toast.LENGTH_SHORT).show()
    }
}

class TwillOtpCopyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TwillOtpCopy.ACTION) return
        intent.getStringExtra(TwillOtpCopy.EXTRA_CODE)?.let { TwillOtpCopy.copy(context, it) }
    }
}
