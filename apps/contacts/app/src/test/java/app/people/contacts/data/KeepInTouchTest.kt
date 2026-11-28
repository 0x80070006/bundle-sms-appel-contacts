package app.people.contacts.data

import org.junit.Assert.assertEquals
import org.junit.Test

class KeepInTouchTest {
    private val day = 86_400_000L
    private fun c(id: Long, vararg numbers: String) = ContactSummary(id, "k$id", "P$id", "P$id", null, true, numbers.toList())

    @Test fun `favourites without news for three weeks come first, longest first`() {
        val now = 100 * day
        val list = keepInTouch(
            listOf(c(1, "0611"), c(2, "0622"), c(3, "0633"), c(4)),
            mapOf(1L to now - 2 * day, 2L to now - 40 * day),
            now,
        )
        // 1: talked two days ago. 4: no number. 3: never, so first; then 2.
        assertEquals(listOf(3L, 2L), list.map { it.contact.id })
    }
}
