package bundle.core.appearance

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * Receives the bundle's [Appearance] from the other apps and hands out ours. Exported so the
 * siblings can reach it, but it only answers apps signed with this app's key: anything else
 * gets nothing and changes nothing.
 */
class AppearanceSyncProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        val caller = callingPackage ?: return null
        if (context.packageManager.checkSignatures(caller, context.packageName) != PackageManager.SIGNATURE_MATCH) return null
        val store = AppearanceStore.get(context)
        return when (method) {
            AppearanceStore.METHOD_APPLY -> { store.receive(arg); Bundle() }
            AppearanceStore.METHOD_GET -> Bundle().apply { putString(AppearanceStore.EXTRA_JSON, store.state.value.toJson()) }
            else -> null
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
