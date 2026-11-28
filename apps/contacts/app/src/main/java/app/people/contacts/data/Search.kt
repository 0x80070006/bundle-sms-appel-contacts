package app.people.contacts.data

import app.people.contacts.util.foldForSearch
import app.people.contacts.util.formatNumber

/** A search hit, with the detail that matched (a number, email, company, note…) when it wasn't the name. */
data class SearchResult(val contact: ContactSummary, val matchedDetail: String?)

/**
 * The contacts matching what was typed, in list order. Names match first; a query that's mostly
 * digits searches phone numbers (ignoring spaces and dashes); anything else also searches
 * emails and then the other details (company, job title, nickname, notes, addresses). Accents
 * and case don't matter, so "jose" finds "José".
 */
fun searchContacts(contacts: List<ContactSummary>, rawQuery: String): List<SearchResult> {
    val query = foldForSearch(rawQuery.trim())
    if (query.isEmpty()) return emptyList()
    val digits = rawQuery.filter { it.isDigit() }
    // A query that's mostly digits is a phone number search.
    val numberSearch = digits.length >= 2 && digits.length * 2 >= rawQuery.count { !it.isWhitespace() }
    return contacts.mapNotNull { c ->
        when {
            foldForSearch(c.name).contains(query) || foldForSearch(c.nameAlternative).contains(query) ->
                SearchResult(c, null)
            numberSearch -> c.numbers.firstOrNull { it.filter(Char::isDigit).contains(digits) }
                ?.let { SearchResult(c, formatNumber(it)) }
            else -> (c.emails + c.details).firstOrNull { foldForSearch(it).contains(query) }
                ?.let { SearchResult(c, it.replace('\n', ' ')) }
        }
    }
}


/** Bundle: month names in French and English, folded (no accents), for "anniversaire en décembre". */
private val MONTHS = mapOf(
    "janvier" to 1, "fevrier" to 2, "mars" to 3, "avril" to 4, "mai" to 5, "juin" to 6, "juillet" to 7,
    "aout" to 8, "septembre" to 9, "octobre" to 10, "novembre" to 11, "decembre" to 12,
    "january" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5, "june" to 6, "july" to 7,
    "august" to 8, "september" to 9, "october" to 10, "november" to 11, "december" to 12,
)
private val BIRTHDAY_WORDS = setOf("anniversaire", "anniv", "ne", "nee", "birthday", "born")
private val FILLER = setOf("en", "a", "au", "aux", "de", "du", "des", "le", "la", "les", "in", "at", "of", "the", "et", "and")

/**
 * Bundle: search in everyday words. Every word of the query must match the contact somewhere:
 * name, a label ("travail"), a detail such as the company or a city in an address ("bordeaux"),
 * an email or a number. A month with "anniversaire"/"birthday" keeps the contacts born that
 * month. Queries that the plain search already understands (one word, a number) go to it.
 */
fun searchNatural(
    contacts: List<ContactSummary>,
    rawQuery: String,
    labelsOf: (Long) -> List<String>,
    birthdayMonthOf: (Long) -> Int?,
): List<SearchResult> {
    val words = foldForSearch(rawQuery.trim()).split(Regex("\\s+")).filter { it.isNotEmpty() }
    val month = words.firstNotNullOfOrNull { MONTHS[it] }
    val wantsBirthday = words.any { it in BIRTHDAY_WORDS } || (month != null && words.size <= 2)
    val terms = words.filter { it !in FILLER && it !in BIRTHDAY_WORDS && !(wantsBirthday && it in MONTHS) }
    if (terms.size <= 1 && !(wantsBirthday && month != null)) return searchContacts(contacts, rawQuery)
    return contacts.mapNotNull { c ->
        if (wantsBirthday && month != null && birthdayMonthOf(c.id) != month) return@mapNotNull null
        val labels = labelsOf(c.id)
        val haystack = listOf(c.name, c.nameAlternative) + labels + c.details + c.emails
        val folded = haystack.map(::foldForSearch)
        if (terms.all { t -> folded.any { it.contains(t) } }) {
            val detail = labels.firstOrNull { l -> terms.any { foldForSearch(l).contains(it) } }
                ?: c.details.firstOrNull { d -> terms.any { foldForSearch(d).contains(it) } }?.replace('\n', ' ')
            SearchResult(c, detail)
        } else null
    }
}
