package app.dialtone.phone.screening

import android.content.Context
import bundle.core.telephony.SpamRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bundle: what Phone does with calls from telemarketing ranges (ARCEP, see [SpamRules]) and
 * with callers that aren't in the contacts. Applied by [SendToVoicemailScreeningService]
 * before the phone rings.
 */
object SpamFilter {
    enum class Action { Ring, Silence, Reject }

    data class Settings(
        val telemarketing: Action = Action.Silence,
        val unknown: Action = Action.Ring,
    )

    private const val PREFS = "spam_filter"
    private val state = MutableStateFlow<Settings?>(null)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun settings(context: Context): StateFlow<Settings?> {
        load(context)
        return state.asStateFlow()
    }

    fun load(context: Context): Settings = state.value ?: prefs(context).let { p ->
        Settings(
            telemarketing = p.getString("telemarketing", null)?.let { runCatching { Action.valueOf(it) }.getOrNull() } ?: Action.Silence,
            unknown = p.getString("unknown", null)?.let { runCatching { Action.valueOf(it) }.getOrNull() } ?: Action.Ring,
        )
    }.also { state.value = it }

    fun update(context: Context, settings: Settings) {
        prefs(context).edit()
            .putString("telemarketing", settings.telemarketing.name)
            .putString("unknown", settings.unknown.name)
            .apply()
        state.value = settings
    }

    /** What to do with an incoming call from [number]; [isContact] says whether it's saved. */
    fun actionFor(context: Context, number: String, isContact: Boolean): Action {
        if (isContact) return Action.Ring
        val s = load(context)
        val spam = if (SpamRules.isFrenchTelemarketing(number)) s.telemarketing else Action.Ring
        return maxOf(spam, s.unknown)
    }
}
