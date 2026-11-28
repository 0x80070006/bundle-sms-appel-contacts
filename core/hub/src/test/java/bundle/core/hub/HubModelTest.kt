package bundle.core.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubModelTest {
    @Test fun linePrefsRoundTrip() {
        val prefs = LinePrefs("sim-2", listOf(HubNote(1, "old"), HubNote(5, "new")))
        val back = LinePrefs.fromJson(prefs.toJson())
        assertEquals("sim-2", back.simId)
        assertEquals(listOf("new", "old"), back.notes.map { it.text })
    }

    @Test fun silenceRoundTrip() {
        val back = LinePrefs.fromJson(LinePrefs(silentUntil = 5_000).toJson())
        assertEquals(5_000L, back.silentUntil)
        assertTrue(back.isSilenced(now = 4_000))
        assertTrue(!back.isSilenced(now = 6_000))
    }

    @Test fun emptyAndBlank() {
        assertTrue(LinePrefs.fromJson(null).isEmpty)
        assertTrue(LinePrefs.fromJson(LinePrefs(null, listOf(HubNote(1, "  "))).toJson()).notes.isEmpty())
    }

    @Test fun labelColorsRoundTrip() {
        val map = mapOf("Famille" to LabelColors.PALETTE[0], "Travail" to LabelColors.PALETTE[5])
        assertEquals(map, LabelColors.fromJson(LabelColors.toJson(map)))
        assertEquals(emptyMap<String, Int>(), LabelColors.fromJson("pas du json"))
    }
}
