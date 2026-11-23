package bundle.core.design

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import java.io.ByteArrayOutputStream

/**
 * Bundle: cartoon avatars (people, animals, robots, monsters and sci-fi characters, drawn in
 * core/design/avatars). A contact with
 * no photo shows one, always the same for that contact; the contact editor offers them as photos.
 */
object CartoonAvatars {
    val people: List<Int> = PEOPLE_AVATARS.toList()
    val animals: List<Int> = ANIMAL_AVATARS.toList()
    val robots: List<Int> = ROBOT_AVATARS.toList()
    val monsters: List<Int> = MONSTER_AVATARS.toList()
    val sciFi: List<Int> = SCIFI_AVATARS.toList()
    val all: List<Int> = people + animals + robots + monsters + sciFi

    /**
     * The avatar a contact with no photo shows, picked at random from [seed] (its contact id):
     * always the same for one contact. Contact ids follow each other, so the id is scrambled
     * first, or contacts 1, 2, 3… would get avatars 1, 2, 3… (all people).
     */
    @DrawableRes
    fun forSeed(seed: Long): Int = all[cartoonAvatarIndex(seed, all.size)]

    /**
     * The avatar as a square JPEG [side] pixels wide, to save as a contact photo (contact
     * photos are JPEG squares). Null if it can't be drawn. Call off the main thread.
     */
    fun jpeg(context: Context, @DrawableRes avatar: Int, side: Int = 720): ByteArray? = runCatching {
        val drawable = context.getDrawable(avatar) ?: return null
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, side, side)
        drawable.draw(Canvas(bitmap))
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            out.toByteArray()
        }
    }.getOrNull()
}
