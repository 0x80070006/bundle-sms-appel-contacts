package bundle.core.appearance

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.thread

/**
 * The bundle's [Appearance] for this app, kept in its own preferences and shared with the other
 * apps of the bundle: every change is sent to each sibling's [AppearanceSyncProvider], and on
 * first use the newest copy among the siblings is taken. Only apps signed with the same key can
 * send or receive it.
 */
class AppearanceStore private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(Appearance.fromJson(prefs.getString(KEY, null)) ?: Appearance())

    /** The current look; Compose screens collect it so a change shows at once. */
    val state: StateFlow<Appearance> = _state

    init {
        // A fresh install takes the look the other apps already have.
        if (_state.value.revision == 0L) thread(name = "appearance-pull") { pullFromSiblings() }
    }

    /** Changes the look here and in the other apps of the bundle. */
    fun update(change: (Appearance) -> Appearance) {
        val before = _state.value
        val after = change(before).copy(revision = System.currentTimeMillis())
        save(after)
        if (after.language != before.language) applyLanguage(after.language)
        thread(name = "appearance-push") { pushToSiblings(after) }
    }

    /** A copy sent by a sibling app: kept if it's newer than ours. */
    internal fun receive(json: String?) {
        val incoming = Appearance.fromJson(json) ?: return
        val current = _state.value
        if (incoming.revision <= current.revision) return
        save(incoming)
        if (incoming.language != current.language) applyLanguage(incoming.language)
    }

    private fun save(appearance: Appearance) {
        prefs.edit().putString(KEY, appearance.toJson()).apply()
        _state.value = appearance
    }

    /** Sets this app's language through Android's per-app languages (Android 13+). */
    private fun applyLanguage(language: AppLanguage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        runCatching {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == AppLanguage.System) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
        }
    }

    /** Authorities of the other bundle apps signed with our key. */
    private fun siblingAuthorities(): List<String> {
        val pm = context.packageManager
        return runCatching {
            pm.queryIntentContentProviders(Intent(ACTION_SYNC), 0)
                .mapNotNull { it.providerInfo }
                .filter { it.packageName != context.packageName }
                .filter { pm.checkSignatures(it.packageName, context.packageName) == PackageManager.SIGNATURE_MATCH }
                .map { it.authority }
        }.getOrDefault(emptyList())
    }

    private fun pushToSiblings(appearance: Appearance) {
        val json = appearance.toJson()
        siblingAuthorities().forEach { authority ->
            runCatching { context.contentResolver.call(Uri.parse("content://$authority"), METHOD_APPLY, json, null) }
        }
    }

    private fun pullFromSiblings() {
        val newest = siblingAuthorities().mapNotNull { authority ->
            runCatching {
                context.contentResolver.call(Uri.parse("content://$authority"), METHOD_GET, null, null)
                    ?.getString(EXTRA_JSON)
            }.getOrNull()?.let(Appearance::fromJson)
        }.maxByOrNull { it.revision }
        if (newest != null) receive(newest.toJson())
    }

    companion object {
        /** Declared by every bundle app's [AppearanceSyncProvider], so they find each other. */
        const val ACTION_SYNC = "bundle.action.APPEARANCE_SYNC"
        internal const val METHOD_APPLY = "apply"
        internal const val METHOD_GET = "get"
        internal const val EXTRA_JSON = "json"
        private const val PREFS = "bundle_appearance"
        private const val KEY = "appearance"

        @Volatile private var instance: AppearanceStore? = null

        fun get(context: Context): AppearanceStore = instance ?: synchronized(this) {
            instance ?: AppearanceStore(context.applicationContext).also { instance = it }
        }
    }
}
