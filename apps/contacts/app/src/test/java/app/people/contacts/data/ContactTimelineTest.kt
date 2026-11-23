package app.people.contacts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactTimelineTest {
    private val keys = setOf("0655443322")

    @Test fun `a number matches however it is written`() {
        assertTrue(sameLine("0655443322", keys))
        assertTrue(sameLine("06 55 44 33 22", keys))
    }

    @Test fun `other numbers, senders with letters and nothing do not match`() {
        assertFalse(sameLine("0655443399", keys))
        assertFalse(sameLine("LIDL", keys))
        assertFalse(sameLine("", keys))
        assertFalse(sameLine(null, keys))
    }

    @Test fun `calls and texts are mixed newest first and cut to the limit`() {
        val calls = listOf(TimelineEntry(100, TimelineKind.CallMissed, "a"), TimelineEntry(300, TimelineKind.CallOutgoing, "a"))
        val texts = listOf(TimelineEntry(200, TimelineKind.TextReceived, "a", "hi"), TimelineEntry(50, TimelineKind.TextSent, "a", "yo"))
        assertEquals(listOf(300L, 200L, 100L), mergeTimeline(calls, texts, 3).map { it.time })
        assertEquals(4, mergeTimeline(calls, texts, 10).size)
    }

    @Test fun `only calls count as calls`() {
        assertTrue(TimelineEntry(1, TimelineKind.CallRejected, "a").isCall)
        assertFalse(TimelineEntry(1, TimelineKind.TextSent, "a", "x").isCall)
    }
}
