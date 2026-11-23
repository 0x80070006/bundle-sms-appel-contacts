package bundle.core.sync

import org.json.JSONObject
import java.io.IOException
import java.util.Base64

/**
 * The shared secret of a sync group. `BundleSync/vault.json` on the server holds a random salt and a
 * small sealed test message: no data, but enough for a phone to tell at once whether its passphrase
 * is the right one, before it writes anything.
 */
object Vault {
    const val ROOT = "BundleSync"
    const val FILE = "vault.json"
    const val MIN_ITERATIONS = 300_000
    private const val MAX_ITERATIONS = 10_000_000
    private const val CHECK = "bundle-sync-ok"
    private const val LABEL = "bundle-sync vault check"

    /**
     * Opens the group's vault with [passphrase] and returns the key. The first phone creates it; the
     * others check their passphrase against it.
     *
     * @throws WrongPassphraseException the passphrase isn't the group's
     * @throws UnsupportedFormatException the vault is unreadable, or asks for fewer than [minIterations]
     *   rounds (a server that lowered it would make the passphrase easier to guess)
     */
    fun open(
        dav: WebDav,
        passphrase: CharArray,
        root: String = ROOT,
        iterations: Int = Crypto.ITERATIONS,
        minIterations: Int = MIN_ITERATIONS,
    ): ByteArray {
        dav.ensureFolder(root)
        dav.get("$root/$FILE")?.let { return unlock(it, passphrase, minIterations) }
        val salt = Crypto.randomBytes(16)
        val key = Crypto.deriveKey(passphrase, salt, iterations)
        val vault = JSONObject()
            .put("v", 1).put("kdf", "pbkdf2-hmac-sha256").put("iterations", iterations)
            .put("salt", b64(salt)).put("check", b64(Crypto.seal(key, CHECK.toByteArray(), LABEL)))
        if (dav.putIfAbsent("$root/$FILE", vault.toString().toByteArray())) return key
        // Another phone made it first: its vault is the one that counts.
        return unlock(dav.get("$root/$FILE") ?: throw IOException("the vault disappeared"), passphrase, minIterations)
    }

    private fun unlock(bytes: ByteArray, passphrase: CharArray, minIterations: Int): ByteArray {
        val vault = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw UnsupportedFormatException("vault.json is not readable")
        }
        if (vault.optInt("v") != 1) throw UnsupportedFormatException("vault format ${vault.optInt("v")}")
        val iterations = vault.optInt("iterations")
        if (iterations < minIterations || iterations > MAX_ITERATIONS) throw UnsupportedFormatException("unsafe key derivation settings")
        val key = Crypto.deriveKey(passphrase, unb64(vault.getString("salt")), iterations)
        if (!String(Crypto.open(key, unb64(vault.getString("check")), LABEL)).equals(CHECK)) throw WrongPassphraseException()
        return key
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)
}
