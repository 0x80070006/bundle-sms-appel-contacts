package app.people.contacts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ThreadTest {
    private val utc = ZoneOffset.UTC
    private fun at(date: LocalDate) = date.atTime(12, 0).toInstant(utc).toEpochMilli()

    @Test fun `birthdays last and next are added and everything is newest first`() {
        val today = LocalDate.of(2026, 11, 28)
        val call = TimelineEntry(at(LocalDate.of(2026, 11, 20)), TimelineKind.CallIncoming, "06")
        val note = TimelineEntry(at(LocalDate.of(2026, 11, 25)), TimelineKind.Note, "", text = "covoiturage")
        val thread = threadOf(listOf(call), listOf(note), 3 to 4, today, utc)

        val birthdays = thread.filter { it.kind == TimelineKind.Birthday }
        assertEquals(2, birthdays.size)
        assertEquals(thread.sortedByDescending { it.time }, thread)
        assertTrue(thread.first().kind == TimelineKind.Birthday) // the next one, in March 2027
    }

    @Test fun `filters keep what they say`() {
        val today = LocalDate.of(2026, 11, 28)
        val thread = threadOf(
            listOf(
                TimelineEntry(1, TimelineKind.CallMissed, "06"),
                TimelineEntry(2, TimelineKind.TextReceived, "06", text = "salut"),
            ),
            listOf(TimelineEntry(3, TimelineKind.Note, "", text = "n")),
            null, today, utc,
        )
        assertEquals(1, thread.count { it.matches(TimelineFilter.Calls) })
        assertEquals(1, thread.count { it.matches(TimelineFilter.Texts) })
        assertEquals(1, thread.count { it.matches(TimelineFilter.Notes) })
        assertEquals(3, thread.count { it.matches(TimelineFilter.All) })
    }
}
