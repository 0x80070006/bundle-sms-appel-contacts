package bundle.core.sync

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The key is wrong, or the data was changed or damaged: AES-GCM can't tell these apart. */
class WrongPassphraseException : Exception("wrong passphrase, or the data is damaged")

/** A file that isn't ours, or was written by a newer version of the format. */
class UnsupportedFormatException(message: String) : Exception(message)

/**
 * The encryption of everything that leaves the phone, and of the app's working copies.
 *
 * A passphrase becomes a 256-bit key with PBKDF2-HMAC-SHA256 (600 000 rounds, a random salt), and
 * every file is sealed with AES-256-GCM under a fresh random nonce. A sealed file is
 * `"BSYNC" | version | nonce (12) | ciphertext + tag (16)`; the header and a caller-chosen label
 * are authenticated too, so a file can't be passed off as another kind of file.
 */
object Crypto {
    const val ITERATIONS = 600_000
    const val KEY_BYTES = 32
    private const val VERSION = 1
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private val MAGIC = "BSYNC".toByteArray(Charsets.US_ASCII)
    private val HEADER_BYTES = MAGIC.size + 1
    private val random = SecureRandom()

    fun randomBytes(count: Int): ByteArray = ByteArray(count).also(random::nextBytes)

    /** The key for [passphrase]. Slow on purpose: do it once, not for every file. */
    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        require(passphrase.isNotEmpty()) { "empty passphrase" }
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BYTES * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Encrypts [plain]; [label] must be the same to [open] it. */
    fun seal(key: ByteArray, plain: ByteArray, label: String): ByteArray {
        require(key.size == KEY_BYTES) { "key must be 32 bytes" }
        val nonce = randomBytes(NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad(label))
        return MAGIC + byteArrayOf(VERSION.toByte()) + nonce + cipher.doFinal(plain)
    }

    /** @throws UnsupportedFormatException not one of our files @throws WrongPassphraseException bad key or damaged. */
    fun open(key: ByteArray, sealed: ByteArray, label: String): ByteArray {
        require(key.size == KEY_BYTES) { "key must be 32 bytes" }
        if (sealed.size < HEADER_BYTES + NONCE_BYTES + TAG_BITS / 8 || !sealed.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw UnsupportedFormatException("not a Bundle sync file")
        }
        val version = sealed[MAGIC.size].toInt()
        if (version > VERSION) throw UnsupportedFormatException("written by a newer version (format $version)")
        val nonce = sealed.copyOfRange(HEADER_BYTES, HEADER_BYTES + NONCE_BYTES)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(aad(label))
            cipher.doFinal(sealed, HEADER_BYTES + NONCE_BYTES, sealed.size - HEADER_BYTES - NONCE_BYTES)
        } catch (e: AEADBadTagException) {
            throw WrongPassphraseException()
        } catch (e: GeneralSecurityException) {
            throw WrongPassphraseException()
        }
    }

    private fun aad(label: String): ByteArray = MAGIC + byteArrayOf(VERSION.toByte()) + label.toByteArray(Charsets.UTF_8)
}
