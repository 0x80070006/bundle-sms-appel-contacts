package app.people.contacts.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ListLayoutTest {
    private fun label(id: Long, title: String, vararg members: Long, account: String = "a") =
        Label(id, title, listOf(LabelGroup(id, "type", account)), members.toSet())

    @Test fun `saved order comes first and new labels follow alphabetically`() {
        assertEquals(
            listOf("Work", "Family", "Friends", "gym"),
            orderLabelTitles(listOf("Family", "gym", "Work", "Friends"), listOf("Work", "Gone", "Family")),
        )
    }

    @Test fun `moving a label stays inside the list`() {
        val order = listOf("A", "B", "C")
        assertEquals(listOf("B", "A", "C"), moveLabelTitle(order, "B", -1))
        assertEquals(listOf("A", "C", "B"), moveLabelTitle(order, "B", 1))
        assertEquals(order, moveLabelTitle(order, "A", -1))
        assertEquals(order, moveLabelTitle(order, "Missing", 1))
    }

    @Test fun `sections follow the label order then list everyone else`() {
        val contacts = listOf(testContact(1, "Ann"), testContact(2, "Bob"), testContact(3, "Cid"), testContact(4, "Dee"))
        val labels = listOf(label(10, "Family", 1, 2), label(11, "Work", 2, 3), label(12, "Empty"))
        val sections = sectionsByLabel(contacts, labels, listOf("Work", "Family"))
        assertEquals(listOf("Work", "Family", null), sections.map { it.first })
        assertEquals(listOf(2L, 3L), sections[0].second.map { it.id })
        assertEquals(listOf(1L, 2L), sections[1].second.map { it.id })
        assertEquals(listOf(4L), sections[2].second.map { it.id })
    }

    @Test fun `labels with the same name in two accounts make one section`() {
        val contacts = listOf(testContact(1, "Ann"), testContact(2, "Bob"))
        val labels = listOf(label(10, "Family", 1, account = "a"), label(20, "Family", 2, account = "b"))
        val sections = sectionsByLabel(contacts, labels, emptyList())
        assertEquals(listOf<String?>("Family"), sections.map { it.first })
        assertEquals(listOf(1L, 2L), sections[0].second.map { it.id })
    }
}
