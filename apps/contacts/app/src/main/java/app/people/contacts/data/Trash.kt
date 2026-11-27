package app.people.contacts.data

import android.content.Context
import java.io.File

/**
 * Bundle: deleted contacts are kept for 30 days, as a vCard in the app's private storage (never
 * synced, never shared), and can be restored from Settings › Trash. Copied just before the
 * contact is deleted; older copies are removed whenever the trash is opened or written.
 */
object Trash {
    const val KEEP_DAYS = 30
    private const val KEEP_MS = KEEP_DAYS * 86_400_000L

    data class Item(val file: File, val name: String, val deletedAt: Long)

    private fun dir(context: Context) = File(context.filesDir, "trash").apply { mkdirs() }

    /** Saves a copy of the contact before it's deleted. Call off the main thread. */
    suspend fun keep(context: Context, contactId: Long, lookupKey: String, name: String?) {
        purge(context)
        val safe = (name ?: "contact").replace(Regex("[^\\p{L}\\p{N} _-]"), "").take(40).ifBlank { "contact" }
        val file = File(dir(context), "${System.currentTimeMillis()}_${contactId}_$safe.vcf")
        val ok = file.outputStream().use { VCardWriter.export(context, listOf(lookupKey), it) }
        if (!ok || file.length() == 0L) file.delete()
    }

    fun list(context: Context): List<Item> {
        purge(context)
        return dir(context).listFiles().orEmpty().filter { it.extension == "vcf" }.mapNotNull { f ->
            val parts = f.nameWithoutExtension.split('_', limit = 3)
            val at = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val fn = runCatching { f.useLines { l -> l.firstOrNull { it.startsWith("FN") }?.substringAfter(':') } }.getOrNull()
            Item(f, fn?.ifBlank { null } ?: parts.getOrNull(2) ?: "?", at)
        }.sortedByDescending { it.deletedAt }
    }

    fun daysLeft(item: Item, now: Long = System.currentTimeMillis()): Int =
        (((item.deletedAt + KEEP_MS) - now) / 86_400_000L).toInt().coerceAtLeast(0)

    fun remove(item: Item) { item.file.delete() }

    fun empty(context: Context) { dir(context).listFiles()?.forEach { it.delete() } }

    private fun purge(context: Context) {
        val limit = System.currentTimeMillis() - KEEP_MS
        dir(context).listFiles()?.forEach { f ->
            val at = f.name.substringBefore('_').toLongOrNull() ?: 0L
            if (at < limit) f.delete()
        }
    }
}
