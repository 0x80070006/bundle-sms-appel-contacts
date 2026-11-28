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
 * apps of the bundle (all but the language, which is this app's own): every change is sent to each sibling's [AppearanceSyncProvider], and on
 * first use the newest copy among the siblings is taken. Only apps signed with the same key can
 * send or receive it.
 */
class AppearanceStore private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val shared = MutableStateFlow(Appearance.fromJson(prefs.getString(KEY, null)) ?: Appearance())
    /** Bundle: this app's own look, when it doesn't follow the bundle's ([ownLook]). */
    private val own = MutableStateFlow(Appearance.fromJson(prefs.getString(KEY_OWN, null)))
    private val _state = MutableStateFlow(own.value ?: shared.value)
    private val _ownLook = MutableStateFlow(own.value != null)

    /** The current look; Compose screens collect it so a change shows at once. */
    val state: StateFlow<Appearance> = _state

    /** Bundle: this app has its own look (true) or follows the one shared by the three apps. */
    val ownLook: StateFlow<Boolean> = _ownLook

    init {
        // A fresh install takes the look the other apps already have.
        if (shared.value.revision == 0L) thread(name = "appearance-pull") { pullFromSiblings() }
    }

    /**
     * Changes the look: of this app only when it has its own look ([ownLook]), else here and in
     * the other apps of the bundle. The language always stays this app's.
     */
    fun update(change: (Appearance) -> Appearance) {
        val before = _state.value
        val after = change(before).copy(revision = System.currentTimeMillis())
        if (after.language != before.language) applyLanguage(after.language)
        if (own.value != null) {
            saveOwn(after)
            // The language is kept in the shared copy too, so it survives going back to it.
            if (after.language != shared.value.language) saveShared(shared.value.copy(language = after.language))
            return
        }
        saveShared(after)
        thread(name = "appearance-push") { pushToSiblings(after) }
    }

    /**
     * Bundle: gives this app its own look (starting from the current one), or makes it follow
     * the bundle's again (its own look is forgotten).
     */
    fun setOwnLook(on: Boolean) {
        if (on) saveOwn(_state.value) else {
            prefs.edit().remove(KEY_OWN).apply()
            own.value = null
            _state.value = shared.value.copy(language = _state.value.language)
        }
        _ownLook.value = on
    }

    /** Bundle: the look shared by the bundle (not this app's own), for the other apps. */
    internal fun sharedJson(): String = shared.value.toJson()

    /** Bundle: "Apply to all apps": this app's look becomes the bundle's, sent to the other two. */
    fun applyToAllApps() {
        val look = _state.value.copy(revision = System.currentTimeMillis())
        prefs.edit().remove(KEY_OWN).apply()
        own.value = null
        _ownLook.value = false
        saveShared(look)
        thread(name = "appearance-push") { pushToSiblings(look) }
    }

    /**
     * A copy sent by a sibling app: kept if it's newer than ours, except the language, which
     * each app chooses for itself.
     */
    internal fun receive(json: String?) {
        val incoming = Appearance.fromJson(json) ?: return
        val current = shared.value
        if (incoming.revision <= current.revision) return
        saveShared(incoming.copy(language = current.language))
    }

    private fun saveShared(appearance: Appearance) {
        prefs.edit().putString(KEY, appearance.toJson()).apply()
        shared.value = appearance
        if (own.value == null) _state.value = appearance
    }

    private fun saveOwn(appearance: Appearance) {
        prefs.edit().putString(KEY_OWN, appearance.toJson()).apply()
        own.value = appearance
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
        private const val KEY_OWN = "appearance_own"

        @Volatile private var instance: AppearanceStore? = null

        fun get(context: Context): AppearanceStore = instance ?: synchronized(this) {
            instance ?: AppearanceStore(context.applicationContext).also { instance = it }
        }
    }
}
