package app.people.contacts.data

import bundle.core.sync.Crypto
import bundle.core.sync.UnsupportedFormatException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Bundle: the file format of the full backup ("suite backup"). A zip of named entries
 * (contacts.vcf, calls.json, sms.json, hub.json, emergency.json, meta.json), sealed with
 * AES-256-GCM under a key derived from the user's passphrase (core/sync's [Crypto], the same
 * as the Nextcloud sync). Layout: "BNDLBAK1" + 16-byte salt + sealed zip.
 * Kept free of Android so it is tested on the JVM.
 */
object SuiteArchive {
    private val MAGIC = "BNDLBAK1".toByteArray(Charsets.US_ASCII)
    private const val LABEL = "suite-backup"
    private const val SALT = 16

    fun pack(entries: Map<String, ByteArray>, passphrase: CharArray, iterations: Int = Crypto.ITERATIONS): ByteArray {
        val zip = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { z ->
                entries.forEach { (name, data) ->
                    z.putNextEntry(ZipEntry(name))
                    z.write(data)
                    z.closeEntry()
                }
            }
            bytes.toByteArray()
        }
        val salt = Crypto.randomBytes(SALT)
        val key = Crypto.deriveKey(passphrase, salt, iterations)
        return MAGIC + salt + Crypto.seal(key, zip, LABEL)
    }

    /** Throws [bundle.core.sync.WrongPassphraseException] for a wrong passphrase. */
    fun unpack(file: ByteArray, passphrase: CharArray, iterations: Int = Crypto.ITERATIONS): Map<String, ByteArray> {
        if (file.size < MAGIC.size + SALT || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw UnsupportedFormatException("not a bundle backup")
        }
        val salt = file.copyOfRange(MAGIC.size, MAGIC.size + SALT)
        val key = Crypto.deriveKey(passphrase, salt, iterations)
        val zip = Crypto.open(key, file.copyOfRange(MAGIC.size + SALT, file.size), LABEL)
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val entry = z.nextEntry ?: break
                out[entry.name] = z.readBytes()
            }
        }
        return out
    }
}
