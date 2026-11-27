package app.people.contacts.data

import bundle.core.sync.UnsupportedFormatException
import bundle.core.sync.WrongPassphraseException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SuiteArchiveTest {
    private val fast = 1_000 // the real 600 000 rounds are too slow for a unit test

    @Test fun roundTrip() {
        val entries = mapOf("contacts.vcf" to "BEGIN:VCARD".toByteArray(), "sms.json" to "[]".toByteArray())
        val file = SuiteArchive.pack(entries, "phrase secrète".toCharArray(), fast)
        val back = SuiteArchive.unpack(file, "phrase secrète".toCharArray(), fast)
        assertEquals(entries.keys, back.keys)
        assertArrayEquals(entries["contacts.vcf"], back["contacts.vcf"])
    }

    @Test(expected = WrongPassphraseException::class)
    fun wrongPassphrase() {
        val file = SuiteArchive.pack(mapOf("a" to byteArrayOf(1)), "bonne".toCharArray(), fast)
        SuiteArchive.unpack(file, "mauvaise".toCharArray(), fast)
    }

    @Test(expected = UnsupportedFormatException::class)
    fun notABackup() {
        SuiteArchive.unpack("pas une sauvegarde du tout".toByteArray(), "x".toCharArray(), fast)
    }
}
