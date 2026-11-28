package app.dialtone.phone.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import app.dialtone.phone.vvm.VvmLog

/**
 * Telecom asks the default phone app's screening service about every incoming call before it
 * rings. Calls from numbers on the "Send to voicemail" list are rejected as if Decline were
 * tapped, so the carrier forwards them to voicemail; everything else is let through untouched.
 * The call still appears in the call log, without a missed-call notification.
 */
class SendToVoicemailScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        val number = details.handle?.schemeSpecificPart
        val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
        val send = incoming && !number.isNullOrBlank() &&
            runCatching { SendToVoicemail.contains(this, number) }.getOrDefault(false)

        // Bundle: telemarketing ranges and unknown callers (Settings › Spam).
        val spam = if (incoming && !send && !number.isNullOrBlank()) {
            runCatching {
                val isContact = app.dialtone.phone.telecom.CallerLookup.lookup(this, number).name != null
                SpamFilter.actionFor(this, number, isContact)
            }.getOrDefault(SpamFilter.Action.Ring)
        } else SpamFilter.Action.Ring
        // Bundle: "Silent" set on the contact (Contacts, kept in the hub): it rings silently.
        val silenced = incoming && !send && spam == SpamFilter.Action.Ring && !number.isNullOrBlank() &&
            runCatching { bundle.core.hub.Hub.line(this, number).isSilenced() }.getOrDefault(false)

        val response = if (spam == SpamFilter.Action.Reject) {
            CallResponse.Builder().setDisallowCall(true).setRejectCall(true)
                .setSkipNotification(true).setSkipCallLog(false).build()
        } else if (spam == SpamFilter.Action.Silence || silenced) {
            CallResponse.Builder().setSilenceCall(true).build()
        } else if (send) {
            VvmLog.i("SendToVoicemail", "sent an incoming call to voicemail")
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
                .setSkipCallLog(false)
                .build()
        } else {
            CallResponse.Builder().build()
        }
        respondToCall(details, response)
    }
}
