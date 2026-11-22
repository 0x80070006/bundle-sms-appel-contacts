package com.android.messaging.ui.bundle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Bundle: the built-in chat backgrounds, plus a photo of the user's ([Custom]). */
enum class ChatWallpaper { None, Mist, Aurora, Dusk, Mint, Peach, Lagoon, Dots, Grid, Waves, Custom }

/**
 * How a conversation looks: its background (built-in or a photo), how much it's blurred and
 * darkened, and the colour of your own bubbles (0: the theme's).
 */
data class ChatStyle(
    val wallpaper: ChatWallpaper = ChatWallpaper.None,
    val photoPath: String? = null,
    /** 0 (sharp) to 1 (very blurred). */
    val blur: Float = 0f,
    /** 0 (as is) to 0.8 (much darker), so bubbles stay readable on a busy photo. */
    val dim: Float = 0f,
    /** ARGB of your bubbles; 0 keeps the theme's colour. */
    val bubbleColor: Int = 0,
    /** ARGB of the other person's bubbles; 0 keeps the theme's colour. */
    val incomingColor: Int = 0,
) {
    fun toJson(): String = JSONObject()
        .put("wallpaper", wallpaper.name)
        .put("photo", photoPath ?: "")
        .put("blur", blur.toDouble())
        .put("dim", dim.toDouble())
        .put("bubble", bubbleColor)
        .put("incoming", incomingColor)
        .toString()

    companion object {
        fun fromJson(json: String?): ChatStyle? = runCatching {
            val o = JSONObject(json ?: return null)
            ChatStyle(
                wallpaper = ChatWallpaper.entries.firstOrNull { it.name == o.optString("wallpaper") } ?: ChatWallpaper.None,
                photoPath = o.optString("photo").ifEmpty { null },
                blur = o.optDouble("blur", 0.0).toFloat(),
                dim = o.optDouble("dim", 0.0).toFloat(),
                bubbleColor = o.optInt("bubble", 0),
                incomingColor = o.optInt("incoming", 0),
            )
        }.getOrNull()
    }
}

/**
 * The default chat style and the ones set for single conversations, kept on the phone. A
 * conversation with its own style uses it; the others use the default.
 */
class ChatStyleStore private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("bundle_chat_style", Context.MODE_PRIVATE)
    private val _default = MutableStateFlow(ChatStyle.fromJson(prefs.getString(KEY_DEFAULT, null)) ?: ChatStyle())
    private val _overrides = MutableStateFlow(loadOverrides())

    val default: StateFlow<ChatStyle> = _default
    val overrides: StateFlow<Map<String, ChatStyle>> = _overrides

    fun styleFor(conversationId: String?, overrides: Map<String, ChatStyle>, default: ChatStyle): ChatStyle =
        conversationId?.let { overrides[it] } ?: default

    fun setDefault(style: ChatStyle) {
        prefs.edit().putString(KEY_DEFAULT, style.toJson()).apply()
        _default.value = style
        cleanPhotos()
    }

    /** Gives [conversationId] its own [style], or back to the default with null. */
    fun setFor(conversationId: String, style: ChatStyle?) {
        val next = _overrides.value.toMutableMap()
        if (style == null) next.remove(conversationId) else next[conversationId] = style
        prefs.edit().putString(KEY_OVERRIDES, JSONObject(next.mapValues { JSONObject(it.value.toJson()) }).toString()).apply()
        _overrides.value = next
        cleanPhotos()
    }

    private fun loadOverrides(): Map<String, ChatStyle> = runCatching {
        val o = JSONObject(prefs.getString(KEY_OVERRIDES, null) ?: return emptyMap())
        o.keys().asSequence().mapNotNull { key -> ChatStyle.fromJson(o.optJSONObject(key)?.toString())?.let { key to it } }.toMap()
    }.getOrDefault(emptyMap())

    /**
     * Copies a picked photo into the app's own storage, scaled to at most 1600 px on its long
     * side, so it stays available (and small) whatever happens to the original.
     */
    fun importPhoto(uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val file = File(photoDir(), "${UUID.randomUUID()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        file.absolutePath
    }.getOrNull()

    /** Deletes photos no style uses any more. */
    private fun cleanPhotos() {
        val used = (listOf(_default.value) + _overrides.value.values).mapNotNull { it.photoPath }.toSet()
        photoDir().listFiles()?.filter { it.absolutePath !in used }?.forEach { it.delete() }
    }

    private fun photoDir() = File(context.filesDir, "chat_wallpapers").apply { mkdirs() }

    companion object {
        private const val KEY_DEFAULT = "default"
        private const val KEY_OVERRIDES = "conversations"

        @Volatile private var instance: ChatStyleStore? = null

        fun get(context: Context): ChatStyleStore = instance ?: synchronized(this) {
            instance ?: ChatStyleStore(context.applicationContext).also { instance = it }
        }
    }
}

/** The style of the conversation on screen, for its background and bubbles. */
val LocalChatStyle = staticCompositionLocalOf { ChatStyle() }

/** Opens the conversation's style sheet (the menu's "Customise" item); null where there's none. */
val LocalConversationCustomizeAction = staticCompositionLocalOf<(() -> Unit)?> { null }
