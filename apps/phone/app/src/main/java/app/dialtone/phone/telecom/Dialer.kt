package app.dialtone.phone.telecom

import app.dialtone.phone.util.tr

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.widget.Toast
import app.dialtone.phone.util.hasPermission

/** Small helpers for actions started from the UI. */
object Dialer {

    fun isDefaultDialer(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true

    fun call(context: Context, number: String) =
        place(context, Uri.fromParts("tel", number, null))

    /** "voicemail:" lets Telecom pick the right voicemail number for the SIM. */
    fun callVoicemail(context: Context) =
        place(context, Uri.fromParts("voicemail", "", null))

    @SuppressLint("MissingPermission")
    private fun place(context: Context, uri: Uri) {
        if (!context.hasPermission(Manifest.permission.CALL_PHONE)) {
            Toast.makeText(context, tr("Phone permission is needed to place calls"), Toast.LENGTH_SHORT).show()
            return
        }
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return
        // Bundle: the SIM chosen for this number (hub), if it can still make calls. The hub
        // lookup crosses processes, so it runs on a thread before placing the call.
        val number = uri.schemeSpecificPart
        Thread {
            val extras = Bundle()
            if (uri.scheme == "tel" && !number.isNullOrBlank()) {
                val simId = runCatching { bundle.core.hub.Hub.line(context, number).simId }.getOrNull()
                val handle = simId?.let { id -> runCatching { telecom.callCapablePhoneAccounts.firstOrNull { it.id == id } }.getOrNull() }
                if (handle != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            }
            runCatching { telecom.placeCall(uri, extras) }
        }.start()
    }

    /**
     * Opens Dialtone's keypad to place a second call during a call ("Add call"); Telecom puts
     * the current call on hold when the new one is placed.
     */
    fun addCallIntent(context: Context): Intent =
        Intent(Intent.ACTION_DIAL).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun sms(context: Context, number: String) = safeStart(
        context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")),
    )

    fun addContact(context: Context, number: String) = safeStart(
        context,
        Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
        },
    )

    fun newContact(context: Context) = safeStart(
        context, Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI),
    )

    fun copy(context: Context, number: String) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(tr("Phone number"), number))
    }

    private fun safeStart(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, tr("No app can handle that"), Toast.LENGTH_SHORT).show() }
    }
}
