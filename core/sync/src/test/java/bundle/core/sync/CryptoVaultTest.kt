package bundle.core.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CryptoVaultTest {
    private val fast = 1_000

    @Test fun `sealed data opens with the same key and label`() {
        val sealed = Crypto.seal(TEST_KEY, "Léa Moreau 06 11 22 33 44".toByteArray(), "a")
        assertEquals("Léa Moreau 06 11 22 33 44", String(Crypto.open(TEST_KEY, sealed, "a")))
    }

    @Test fun `the sealed bytes do not contain the text, and two seals differ`() {
        val plain = "Léa Moreau".toByteArray()
        val one = Crypto.seal(TEST_KEY, plain, "a")
        val two = Crypto.seal(TEST_KEY, plain, "a")
        assertFalse(String(one, Charsets.ISO_8859_1).contains("Moreau"))
        assertFalse(one.contentEquals(two))
    }

    @Test fun `a wrong key, a wrong label or a changed byte is refused`() {
        val sealed = Crypto.seal(TEST_KEY, "secret".toByteArray(), "a")
        val other = ByteArray(32) { 9 }
        listOf<() -> Unit>(
            { Crypto.open(other, sealed, "a") },
            { Crypto.open(TEST_KEY, sealed, "b") },
            { Crypto.open(TEST_KEY, sealed.copyOf().also { it[sealed.size - 1] = (it[sealed.size - 1] + 1).toByte() }, "a") },
            { Crypto.open(TEST_KEY, sealed.copyOf().also { it[10] = (it[10] + 1).toByte() }, "a") },
        ).forEach { attempt ->
            try { attempt(); fail("should have been refused") } catch (e: WrongPassphraseException) { /* expected */ }
        }
    }

    @Test fun `files that are not ours or are newer are reported as such`() {
        try { Crypto.open(TEST_KEY, ByteArray(64), "a"); fail() } catch (e: UnsupportedFormatException) { }
        val newer = Crypto.seal(TEST_KEY, "x".toByteArray(), "a").also { it[5] = 9 }
        try { Crypto.open(TEST_KEY, newer, "a"); fail() } catch (e: UnsupportedFormatException) { }
    }

    @Test fun `the same passphrase and salt give the same key, another passphrase another key`() {
        val salt = ByteArray(16) { 1 }
        val a = Crypto.deriveKey("correct horse".toCharArray(), salt, fast)
        assertArrayEquals(a, Crypto.deriveKey("correct horse".toCharArray(), salt, fast))
        assertFalse(a.contentEquals(Crypto.deriveKey("correct horsf".toCharArray(), salt, fast)))
        assertEquals(32, a.size)
    }

    @Test fun `the first phone creates the vault and the others get the same key`() {
        val dav = FakeDav()
        val k1 = Vault.open(dav, "phrase secrète".toCharArray(), iterations = fast, minIterations = fast)
        val k2 = Vault.open(dav, "phrase secrète".toCharArray(), iterations = fast, minIterations = fast)
        assertArrayEquals(k1, k2)
        assertFalse(String(dav.files.getValue("BundleSync/vault.json")).contains("secrète"))
    }

    @Test fun `a wrong passphrase is caught before anything is written`() {
        val dav = FakeDav()
        Vault.open(dav, "right".toCharArray(), iterations = fast, minIterations = fast)
        val before = dav.files.toMap()
        try { Vault.open(dav, "wrong".toCharArray(), iterations = fast, minIterations = fast); fail() } catch (e: WrongPassphraseException) { }
        assertEquals(before.keys, dav.files.keys)
    }

    @Test fun `two phones creating the vault at once end up with one key`() {
        val dav = FakeDav()
        val other = FakeDav()
        val k2 = Vault.open(other, "same".toCharArray(), iterations = fast, minIterations = fast)
        // The other phone wins the race while this one is deriving its key.
        dav.beforePutIfAbsent = { dav.files["BundleSync/vault.json"] = other.files.getValue("BundleSync/vault.json") }
        val k1 = Vault.open(dav, "same".toCharArray(), iterations = fast, minIterations = fast)
        assertArrayEquals(k2, k1)
    }

    @Test fun `a server that lowers the key derivation rounds is refused`() {
        val dav = FakeDav()
        Vault.open(dav, "p".toCharArray(), iterations = fast, minIterations = fast)
        try { Vault.open(dav, "p".toCharArray()); fail() } catch (e: UnsupportedFormatException) { assertTrue(e.message!!.contains("unsafe")) }
    }
}
