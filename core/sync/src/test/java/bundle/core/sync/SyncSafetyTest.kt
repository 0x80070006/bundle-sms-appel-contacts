package bundle.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Bundle: the sync safety changes (confirmed deletions, replays, backups kept, long absence). */
class SyncSafetyTest {
    private val day = 24L * 60 * 60 * 1000

    private fun syncOf(p: Phone, confirm: Boolean) = SyncEngine(
        p.dav, p.local, p.state, p.key, p.id, "phone ${p.id}", { p.now }, confirmDeletions = confirm,
    ).sync().also { p.now += 1_000 }

    @Test fun `mass deletion stops the sync until the user confirms it`() {
        val dav = FakeDav()
        val a = Phone("A", dav, TEST_KEY)
        repeat(30) { a.local.add(person("P$it", null, "06000000%02d".format(it))) }
        a.sync()
        a.local.contacts.keys.toList().take(15).forEach { a.local.contacts.remove(it) }
        try {
            a.sync()
            fail("expected the sync to stop")
        } catch (e: SyncAbortedException) {
            assertEquals(15, e.deletions)
        }
        val confirmed = syncOf(a, confirm = true)
        assertEquals(15, confirmed.total)
        // The next run is guarded again, and has nothing more to delete.
        a.sync()
    }

    @Test fun `a phone file put back older than one already seen is ignored`() {
        val dav = FakeDav()
        val a = Phone("A", dav, TEST_KEY)
        val b = Phone("B", dav, TEST_KEY)
        a.local.add(person("Léa", null, "0611223344"))
        a.sync()
        val old = dav.files.getValue("${Vault.ROOT}/devices/A${SyncEngine.EXT}").copyOf()
        a.local.add(person("Hugo", null, "0655443322"))
        a.sync()
        b.sync()
        dav.files["${Vault.ROOT}/devices/A${SyncEngine.EXT}"] = old
        val report = b.sync()
        assertEquals(1, report.replayed)
        assertEquals(2, b.local.contacts.size)
    }

    @Test fun `a file cannot be passed off as another phone's`() {
        val dav = FakeDav()
        val a = Phone("A", dav, TEST_KEY)
        val b = Phone("B", dav, TEST_KEY)
        a.local.add(person("Léa", null, "0611223344"))
        a.sync()
        dav.files["${Vault.ROOT}/devices/C${SyncEngine.EXT}"] = dav.files.getValue("${Vault.ROOT}/devices/A${SyncEngine.EXT}")
        val report = b.sync()
        assertTrue(report.unreadable.any { it.startsWith("C") })
    }

    @Test fun `files from the previous version are still read`() {
        val dav = FakeDav()
        val b = Phone("B", dav, TEST_KEY)
        val legacy = DeviceFile("A", "old", 500, listOf(Record("u1", person("Léa", null, "0611223344"), 500)), emptyMap())
        dav.ensureFolder("${Vault.ROOT}/devices")
        dav.files["${Vault.ROOT}/devices/A${SyncEngine.EXT}"] = Crypto.seal(TEST_KEY, legacy.toBytes(), DeviceFile.LABEL)
        assertEquals(1, b.sync().created)
    }

    @Test fun `backups keep ten recent copies and one per day for a week`() {
        val now = 30 * day
        val names = (0 until 40).map { i -> "${now - i * day / 4}${SyncEngine.EXT}" } // four a day for ten days
        val kept = SyncEngine.keptBackups(names, now)
        val days = kept.map { (it.removeSuffix(SyncEngine.EXT).toLong()) / day }.toSet()
        assertTrue(kept.size in 10..17)
        assertTrue(days.size >= 7)
        assertTrue(names.take(10).all { it in kept })
    }

    @Test fun `a phone back after more than ninety days is warned`() {
        val dav = FakeDav()
        val a = Phone("A", dav, TEST_KEY)
        a.local.add(person("Léa", null, "0611223344"))
        assertFalse(a.sync().longAbsence)
        a.now += 91 * day
        assertTrue(a.sync().longAbsence)
        assertFalse(a.sync().longAbsence)
    }
}
