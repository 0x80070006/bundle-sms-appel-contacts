package app.dialtone.phone.util

import java.util.Locale

/**
 * Bundle: the app's text in the language in use. Dialtone writes its English text in the code
 * rather than in string resources; [tr] looks each one up in [FRENCH] when the app runs in
 * French (the phone's language, or the one chosen in Appearance), and returns it unchanged
 * otherwise. Text not in the table stays English.
 */
fun tr(en: String): String =
    if (Locale.getDefault().language == "fr") FRENCH[en] ?: en else en

/** [tr] for text with values: "Delete %d calls". */
fun trf(en: String, vararg args: Any?): String = String.format(Locale.getDefault(), tr(en), *args)
