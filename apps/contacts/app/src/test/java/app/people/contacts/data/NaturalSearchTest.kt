package app.people.contacts.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalSearchTest {
    private fun c(id: Long, name: String, vararg details: String) =
        ContactSummary(id, "k$id", name, name, null, false, listOf("06000000$id"), details = details.toList())

    private val contacts = listOf(
        c(1, "Adrien Dubois", "12 rue Sainte-Catherine, Bordeaux"),
        c(2, "Alice Martin", "Lyon"),
        c(3, "Hugo Bernard", "Bordeaux"),
    )
    private val labels = mapOf(1L to listOf("Travail"), 2L to listOf("Famille"), 3L to listOf("Amis"))
    private val months = mapOf(1L to 12, 2L to 3, 3L to 12)
    private fun search(q: String) = searchNatural(contacts, q, { labels[it].orEmpty() }, { months[it] }).map { it.contact.id }

    @Test fun `every word must match a name, a label or a detail`() {
        assertEquals(listOf(1L), search("travail bordeaux"))
        assertEquals(listOf(1L, 3L), search("bordeaux"))
    }

    @Test fun `birthday month`() {
        assertEquals(listOf(1L, 3L), search("anniversaire en décembre"))
        assertEquals(listOf(2L), search("anniversaire mars"))
    }

    @Test fun `plain searches still work`() {
        assertEquals(listOf(2L), search("alice"))
    }
}
