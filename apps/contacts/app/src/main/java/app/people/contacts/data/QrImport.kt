package app.people.contacts.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bundle: reads a contact QR code (a vCard or a MECARD) from a photo or a screenshot, on the
 * phone with zxing. Returns the vCard text, or null when the picture holds no contact code.
 */
object QrImport {
    suspend fun read(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val bitmap = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val side = maxOf(info.size.width, info.size.height)
                if (side > 2000) d.setTargetSampleSize(side / 2000 + 1)
            }
        }.getOrNull() ?: return@withContext null
        decode(bitmap)?.let(::toVCard)
    }

    private fun decode(bitmap: Bitmap): String? {
        val soft = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val pixels = IntArray(soft.width * soft.height)
        soft.getPixels(pixels, 0, soft.width, 0, 0, soft.width, soft.height)
        val source = RGBLuminanceSource(soft.width, soft.height, pixels)
        val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)
        return runCatching { MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text }.getOrNull()
    }

    /** The code's text as a vCard: a vCard as is, a MECARD converted, anything else null. */
    internal fun toVCard(text: String): String? {
        val t = text.trim()
        if (t.startsWith("BEGIN:VCARD", ignoreCase = true)) return t
        if (!t.startsWith("MECARD:", ignoreCase = true)) return null
        val fields = t.substring(7).split(';').mapNotNull { part ->
            val i = part.indexOf(':')
            if (i <= 0) null else part.substring(0, i).uppercase() to part.substring(i + 1).replace("\\,", ",").replace("\\:", ":")
        }
        val name = fields.firstOrNull { it.first == "N" }?.second?.split(',')?.reversed()?.joinToString(" ")?.trim()
            ?: return null
        return buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("FN:$name")
            appendLine("N:;$name;;;")
            fields.forEach { (k, v) ->
                when (k) {
                    "TEL" -> appendLine("TEL:$v")
                    "EMAIL" -> appendLine("EMAIL:$v")
                    "URL" -> appendLine("URL:$v")
                    "NOTE" -> appendLine("NOTE:$v")
                    "ADR" -> appendLine("ADR:;;$v;;;;")
                }
            }
            append("END:VCARD")
        }
    }
}
