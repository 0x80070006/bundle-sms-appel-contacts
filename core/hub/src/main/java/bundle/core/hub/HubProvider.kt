package bundle.core.hub

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import org.json.JSONObject

/**
 * The hub, hosted by Contacts. Exported so Phone and Messages can reach it, but it answers only
 * apps signed with the same key as this one (like the appearance provider): anything else gets
 * nothing and changes nothing.
 */
class HubProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        val caller = callingPackage ?: return null
        if (caller != context.packageName &&
            context.packageManager.checkSignatures(caller, context.packageName) != PackageManager.SIGNATURE_MATCH
        ) return null
        val store = HubStore(context)
        return when (method) {
            Hub.GET_LINE -> Bundle().apply { putString(Hub.EXTRA_JSON, store.line(arg ?: return null).toJson().toString()) }
            Hub.PUT_LINE -> {
                val json = extras?.getString(Hub.EXTRA_JSON) ?: return null
                store.putLine(arg ?: return null, LinePrefs.fromJson(runCatching { JSONObject(json) }.getOrNull()))
                Bundle()
            }
            Hub.GET_LABEL_COLORS -> Bundle().apply { putString(Hub.EXTRA_JSON, LabelColors.toJson(store.labelColors())) }
            else -> null
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
