package app.people.contacts.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bundle: "In case of emergency" (ICE). The people to call, and optional medical information,
 * kept only on this phone (never synced). Shown at the top of Highlights and as a QR code that
 * a first responder can scan. Android doesn't let an app put this on the lock screen: Settings ›
 * Safety & emergency does that, and the Emergency screen links there.
 */
object Emergency {
    data class Info(
        val contactIds: List<Long> = emptyList(),
        val bloodType: String = "",
        val allergies: String = "",
        val medications: String = "",
        val conditions: String = "",
    ) {
        val hasMedical get() = listOf(bloodType, allergies, medications, conditions).any { it.isNotBlank() }
    }

    private const val PREFS = "emergency"
    private val state = MutableStateFlow<Info?>(null)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun info(context: Context): StateFlow<Info?> {
        if (state.value == null) state.value = load(context)
        return state.asStateFlow()
    }

    fun load(context: Context): Info = prefs(context).let { p ->
        Info(
            contactIds = p.getString("ids", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() },
            bloodType = p.getString("blood", "").orEmpty(),
            allergies = p.getString("allergies", "").orEmpty(),
            medications = p.getString("medications", "").orEmpty(),
            conditions = p.getString("conditions", "").orEmpty(),
        )
    }

    fun save(context: Context, info: Info) {
        prefs(context).edit()
            .putString("ids", info.contactIds.distinct().joinToString(","))
            .putString("blood", info.bloodType.trim())
            .putString("allergies", info.allergies.trim())
            .putString("medications", info.medications.trim())
            .putString("conditions", info.conditions.trim())
            .apply()
        state.value = info
    }

    fun isEmergency(context: Context, contactId: Long) = contactId in load(context).contactIds

    fun toggle(context: Context, contactId: Long) {
        val info = load(context)
        val ids = if (contactId in info.contactIds) info.contactIds - contactId else info.contactIds + contactId
        save(context, info.copy(contactIds = ids))
    }

    /** Plain text for the QR code: medical info, then each emergency contact and number. */
    fun qrText(info: Info, contacts: List<Pair<String, String?>>, fr: Boolean): String = buildString {
        appendLine(if (fr) "EN CAS D'URGENCE" else "IN CASE OF EMERGENCY")
        if (info.bloodType.isNotBlank()) appendLine((if (fr) "Groupe sanguin : " else "Blood type: ") + info.bloodType)
        if (info.allergies.isNotBlank()) appendLine((if (fr) "Allergies : " else "Allergies: ") + info.allergies)
        if (info.medications.isNotBlank()) appendLine((if (fr) "Traitements : " else "Medications: ") + info.medications)
        if (info.conditions.isNotBlank()) appendLine((if (fr) "Antécédents : " else "Conditions: ") + info.conditions)
        contacts.forEach { (name, number) -> appendLine("$name${number?.let { " : $it" }.orEmpty()}") }
    }.trim()
}
