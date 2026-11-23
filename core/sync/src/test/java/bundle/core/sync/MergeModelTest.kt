package bundle.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeModelTest {
    @Test fun `one number written two ways is one item`() {
        assertEquals(Fields.itemKey("phones", "+33 6 11 22 33 44"), Fields.itemKey("phones", "06 11 22 33 44"))
        assertEquals(Fields.itemKey("emails", " Lea@Example.com "), Fields.itemKey("emails", "lea@example.com"))
    }

    @Test fun `a contact saved by number has no name`() {
        assertFalse(person(null, null, "0611223344").hasName)
        assertFalse(person("06 11 22 33 44", null, "0611223344").hasName)
        assertTrue(person("Léa", null, "0611223344").hasName)
    }

    @Test fun `names compare without case, accents or order`() {
        assertEquals(person("Léa", "Moreau").nameKey, person("MOREAU", "lea").nameKey)
    }

    @Test fun `fields survive the trip through json`() {
        val f = person("Léa", "Moreau", "+33 6 11 22 33 44", emails = listOf("l@x.fr"), note = "à rappeler", starred = true)
        assertEquals(f, fieldsFromJson(f.toJson()))
        val file = DeviceFile("d", "Pixel", 5, listOf(Record("u1", f, 7)), mapOf("u2" to 9L))
        val back = DeviceFile.fromBytes(file.toBytes())
        assertEquals(file, back)
    }

    @Test fun `a number-only contact and a named one with the same number are the same person`() {
        assertEquals(Match.Same, Merge.match(person(null, null, "0611223344"), person("Léa", "Moreau", "+33 6 11 22 33 44")))
    }

    @Test fun `same name and same number are the same person, another name is only similar`() {
        assertEquals(Match.Same, Merge.match(person("Léa", "Moreau", "0611223344"), person("lea", "MOREAU", "0611223344")))
        assertEquals(Match.Similar, Merge.match(person("Maman", null, "0611223344"), person("Mom", null, "0611223344")))
        assertEquals(Match.None, Merge.match(person("Léa", "Moreau", "0611223344"), person("Léa", "Moreau", "0699999999")))
    }

    @Test fun `a change made on one side only is taken`() {
        val base = person("Léa", "Moreau", "0611223344", note = "a")
        val local = base
        val remote = person("Léa", "Moreau", "0611223344", note = "b")
        assertEquals("b", Merge.threeWay(base, local, remote, 10, 5).scalar("note"))
        assertEquals("b", Merge.threeWay(base, remote, local, 5, 10).scalar("note"))
    }

    @Test fun `changed on both sides, the more recent wins`() {
        val base = person("Léa", null, note = "a")
        val local = person("Léa", null, note = "L")
        val remote = person("Léa", null, note = "R")
        assertEquals("R", Merge.threeWay(base, local, remote, 1, 2).scalar("note"))
        assertEquals("L", Merge.threeWay(base, local, remote, 2, 1).scalar("note"))
    }

    @Test fun `a value is never replaced by nothing when the other side still has it`() {
        val base = person("Léa", null)
        assertEquals("Léa", Merge.threeWay(null, person("Léa", null), person(null, null), 1, 9).scalar("first"))
        // Edited here, cleared there: the edit is kept even though the clear is newer.
        val edited = Merge.threeWay(person("Léa", null, note = "a"), person("Léa", null, note = "b"), person("Léa", null), 1, 9)
        assertEquals("b", edited.scalar("note"))
    }

    @Test fun `numbers added on either side are kept and numbers removed on either side go`() {
        val base = person("Léa", null, "0611111111", "0622222222")
        val local = person("Léa", null, "0611111111", "0633333333")        // removed 22, added 33
        val remote = person("Léa", null, "0622222222", "0611111111", "0644444444") // added 44
        val merged = Merge.threeWay(base, local, remote, 1, 2)
        assertEquals(setOf(Fields.phoneKey("0611111111"), Fields.phoneKey("0633333333"), Fields.phoneKey("0644444444")), merged.keys("phones"))
    }

    @Test fun `with no common base everything is joined`() {
        val merged = Merge.threeWay(null, person(null, null, "0611223344"), person("Léa", "Moreau", "0611223344", emails = listOf("l@x.fr")), 9, 1)
        assertEquals("Léa", merged.scalar("first"))
        assertEquals(1, merged.keys("phones").size)
        assertEquals(1, merged.keys("emails").size)
    }
}
