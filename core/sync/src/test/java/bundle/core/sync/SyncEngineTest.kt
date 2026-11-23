package bundle.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncEngineTest {
    private fun phones() = FakeDav().let { dav -> Triple(dav, Phone("A", dav, TEST_KEY), Phone("B", dav, TEST_KEY)) }

    @Test fun `a contact reaches the other phone and an edit comes back`() {
        val (_, a, b) = phones()
        a.local.add(person("Léa", "Moreau", "0611223344"))
        a.sync()
        val first = b.sync()
        assertEquals(1, first.created)
        assertEquals("Léa", b.local.contacts.values.single().scalar("first"))

        val handle = b.local.contacts.keys.single()
        b.local.contacts[handle] = person("Léa", "Moreau", "0611223344", emails = listOf("lea@x.fr"))
        b.sync()
        val back = a.sync()
        assertEquals(1, back.updated)
        assertEquals(setOf("lea@x.fr"), a.local.contacts.values.single().items("emails").map { it.value }.toSet())
    }

    @Test fun `a second sync with nothing new does nothing and makes no backup`() {
        val (_, a, b) = phones()
        a.local.add(person("Léa", "Moreau", "0611223344"))
        a.sync(); b.sync(); a.sync()
        val backups = b.backups
        val again = b.sync()
        assertFalse(again.changedHere)
        assertEquals(backups, b.backups)
        assertEquals(1, b.local.contacts.size)
    }

    @Test fun `a backup is made once before the first change`() {
        val (_, a, b) = phones()
        a.local.add(person("Léa", null, "0611223344")); a.local.add(person("Hugo", null, "0655443322"))
        a.sync(); b.sync()
        assertEquals(1, b.backups)
    }

    @Test fun `the contact with a name is kept when the other side only has the number`() {
        val (_, a, b) = phones()
        a.local.add(person(null, null, "06 11 22 33 44"))          // saved by number only
        b.local.add(person("Léa", "Moreau", "+33 6 11 22 33 44"))  // the same line, with a name
        a.sync(); b.sync(); a.sync()
        assertEquals(1, a.local.contacts.size)
        assertEquals(1, b.local.contacts.size)
        assertEquals("Léa", a.local.contacts.values.single().scalar("first"))
        assertEquals("Léa", b.local.contacts.values.single().scalar("first"))
    }

    @Test fun `a named contact is not replaced by a number-only duplicate`() {
        val (_, a, b) = phones()
        a.local.add(person("Léa", "Moreau", "0611223344"))
        b.local.add(person(null, null, "0611223344"))
        a.sync(); b.sync(); a.sync()
        assertEquals(1, b.local.contacts.size)
        assertEquals("Léa", b.local.contacts.values.single().scalar("first"))
    }

    @Test fun `the same name and number on both phones become one contact`() {
        val (_, a, b) = phones()
        a.local.add(person("Léa", "Moreau", "0611223344"))
        b.local.add(person("lea", "moreau", "+33611223344"))
        a.sync(); b.sync(); a.sync()
        assertEquals(1, a.local.contacts.size)
        assertEquals(1, b.local.contacts.size)
    }

    @Test fun `the same number under two names stays two contacts and is reported`() {
        val (_, a, b) = phones()
        a.local.add(person("Maman", null, "0611223344"))
        b.local.add(person("Mom", null, "0611223344"))
        a.sync()
        val report = b.sync()
        assertEquals(2, b.local.contacts.size)
        assertEquals(1, report.suggestions)
    }

    @Test fun `a deletion reaches the other phone if it was not touched there`() {
        val (_, a, b) = phones()
        val h = a.local.add(person("Léa", null, "0611223344"))
        a.sync(); b.sync()
        a.local.delete(h)
        a.sync()
        val report = b.sync()
        assertEquals(1, report.deleted)
        assertTrue(b.local.contacts.isEmpty())
    }

    @Test fun `an edit wins over a deletion`() {
        val (_, a, b) = phones()
        val h = a.local.add(person("Léa", null, "0611223344"))
        a.sync(); b.sync()
        a.local.delete(h)
        a.sync()
        b.local.contacts[b.local.contacts.keys.single()] = person("Léa", null, "0611223344", note = "important")
        b.sync()
        assertEquals(1, b.local.contacts.size)
        a.sync()
        assertEquals("important", a.local.contacts.values.single().scalar("note"))
    }

    @Test fun `too many deletions at once are not applied`() {
        val (_, a, b) = phones()
        val handles = (1..30).map { a.local.add(person("C$it", null, "06" + "%08d".format(it))) }
        a.sync(); b.sync()
        // A hostile or buggy phone publishes 30 deletions.
        handles.forEach { a.local.delete(it) }
        try { a.sync(); fail("the sync that would delete everything must stop") } catch (e: SyncAbortedException) { }
        assertEquals(30, b.sync().let { b.local.contacts.size })
    }

    @Test fun `a forged deletion list is capped on the receiving phone`() {
        val (dav, a, b) = phones()
        (1..30).forEach { a.local.add(person("C$it", null, "06" + "%08d".format(it))) }
        a.sync(); b.sync()
        val uids = SyncState.fromBytes(b.state.saved!!.toBytes()).links.values
        val forged = DeviceFile("X", "forger", 99_999_999, emptyList(), uids.associateWith { 99_999_999L })
        dav.files["BundleSync/devices/X.bsync"] = Crypto.seal(TEST_KEY, forged.toBytes(), DeviceFile.LABEL)
        val report = b.sync()
        assertEquals(30, report.skippedDeletes)
        assertEquals(30, b.local.contacts.size)
    }

    @Test fun `contacts that cannot be read stop the sync instead of deleting everywhere`() {
        val (_, a, b) = phones()
        (1..30).forEach { a.local.add(person("C$it", null, "06" + "%08d".format(it))) }
        a.sync(); b.sync()
        a.local.readFails = true
        try { a.sync(); fail() } catch (e: SecurityException) { }
        a.local.readFails = false
        assertEquals(0, a.sync().deleted)
        assertEquals(30, a.local.contacts.size)
    }

    @Test fun `what is on the server is encrypted`() {
        val (dav, a, _) = phones()
        a.local.add(person("Léa", "Moreau", "0611223344", emails = listOf("lea@secret.fr")))
        a.sync()
        val stored = dav.files.getValue("BundleSync/devices/A.bsync")
        val text = String(stored, Charsets.ISO_8859_1)
        listOf("Moreau", "Léa", "0611223344", "secret.fr").forEach { assertFalse(it, text.contains(it)) }
        try { Crypto.open(ByteArray(32) { 7 }, stored, DeviceFile.LABEL); fail() } catch (e: WrongPassphraseException) { }
    }

    @Test fun `a damaged file from another phone is skipped and reported`() {
        val (dav, a, b) = phones()
        a.local.add(person("Léa", null, "0611223344"))
        a.sync()
        dav.files["BundleSync/devices/Z.bsync"] = ByteArray(80) { 3 }
        val report = b.sync()
        assertEquals(1, report.created)
        assertEquals(1, report.unreadable.size)
    }

    @Test fun `a failed creation is retried on the next sync, without duplicates`() {
        val (_, a, b) = phones()
        a.local.add(person("Léa", null, "0611223344"))
        a.sync()
        b.local.failCreate = true
        assertEquals(1, b.sync().failures)
        b.local.failCreate = false
        assertEquals(1, b.sync().created)
        assertEquals(1, b.sync().let { b.local.contacts.size })
    }

    @Test fun `three phones converge`() {
        val dav = FakeDav()
        val phones = listOf("A", "B", "C").map { Phone(it, dav, TEST_KEY) }
        phones[0].local.add(person("Léa", null, "0611111111"))
        phones[1].local.add(person("Hugo", null, "0622222222"))
        phones[2].local.add(person("Camille", null, "0633333333"))
        repeat(3) { phones.forEach { it.sync() } }
        phones.forEach { assertEquals(setOf("Léa", "Hugo", "Camille"), it.local.contacts.values.map { f -> f.scalar("first") }.toSet()) }
    }
}
