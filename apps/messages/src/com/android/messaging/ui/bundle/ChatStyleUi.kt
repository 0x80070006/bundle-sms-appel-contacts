package com.android.messaging.ui.bundle

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import bundle.core.appearance.AppearanceCard
import bundle.core.appearance.AppearanceHeader
import bundle.core.appearance.Swatch
import bundle.core.appearance.appearanceSegment
import bundle.core.appearance.contentColorFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/** Colours offered for your own bubbles. */
private val BUBBLE_COLORS = listOf(
    0xFF1A73E8, 0xFF0B8043, 0xFFE37400, 0xFFD93025, 0xFFC2185B, 0xFF8E24AA,
    0xFF3949AB, 0xFF00897B, 0xFF6D4C41, 0xFF37474F, 0xFFF6BF26, 0xFF000000,
).map { it.toInt() }

/** Colours offered for the other person's bubbles: light and dark neutrals and pastels. */
private val INCOMING_COLORS = listOf(
    0xFFFFFFFF, 0xFFE8EAED, 0xFFE8F0FE, 0xFFE6F4EA, 0xFFFEF7E0, 0xFFFCE8E6, 0xFF3C4043, 0xFF202124,
).map { it.toInt() }

/** The text of the chat style screens, in French or English. */
class ChatStrings(private val fr: Boolean) {
    private fun t(fr: String, en: String) = if (this.fr) fr else en
    val appearanceSummary = t("Thème, couleurs, langue, icône de l'app et fonds des conversations", "Theme, colours, language, app icon and chat backgrounds")
    val defaultStyle = t("Style par défaut des conversations", "Default chat style")
    val customize = t("Personnaliser la conversation", "Customise chat")
    val ownStyle = t("Style propre à cette conversation", "Own style for this chat")
    val ownStyleSummary = t("Sinon, elle suit le style par défaut (Paramètres › Apparence)", "Otherwise it follows the default style (Settings › Appearance)")
    val background = t("Fond", "Background")
    val blur = t("Flou", "Blur")
    val dim = t("Assombrir", "Dim")
    val myBubbles = t("Mes bulles", "My bubbles")
    val theirBubbles = t("Bulles reçues", "Their bubbles")
    val theme = t("Thème", "Theme")
    val reset = t("Réinitialiser", "Reset")
    val done = t("OK", "Done")
    val sampleIn = t("Salut ! On se voit ce soir ?", "Hi! Still on for tonight?")
    val sampleOut = t("Avec plaisir, j'arrive vers 20 h", "Sure, I'll be there around 8")

    fun wallpaper(w: ChatWallpaper) = when (w) {
        ChatWallpaper.None -> t("Aucun", "None")
        ChatWallpaper.Mist -> t("Brume", "Mist")
        ChatWallpaper.Aurora -> t("Aurore", "Aurora")
        ChatWallpaper.Dusk -> t("Crépuscule", "Dusk")
        ChatWallpaper.Mint -> t("Menthe", "Mint")
        ChatWallpaper.Peach -> t("Pêche", "Peach")
        ChatWallpaper.Lagoon -> t("Lagon", "Lagoon")
        ChatWallpaper.Dots -> t("Points", "Dots")
        ChatWallpaper.Grid -> t("Grille", "Grid")
        ChatWallpaper.Waves -> t("Vagues", "Waves")
        ChatWallpaper.Custom -> t("Photo", "Photo")
    }
}

@Composable
@ReadOnlyComposable
fun chatStrings(): ChatStrings = ChatStrings(LocalConfiguration.current.locales[0]?.language == "fr")

/** Your bubble's colour for [style], or [themeColor] when it keeps the theme's. */
fun ChatStyle.outgoing(themeColor: Color): Color = if (bubbleColor != 0) Color(bubbleColor) else themeColor

fun ChatStyle.incoming(themeColor: Color): Color = if (incomingColor != 0) Color(incomingColor) else themeColor

/** Text colour on a bubble of [style]'s colour, or [themeContent] for the theme's. */
fun ChatStyle.outgoingContent(themeContent: Color): Color = if (bubbleColor != 0) contentColorFor(Color(bubbleColor)) else themeContent

fun ChatStyle.incomingContent(themeContent: Color): Color = if (incomingColor != 0) contentColorFor(Color(incomingColor)) else themeContent

/**
 * The conversation's background for [style]: a built-in gradient or pattern, or the user's
 * photo, then blurred and darkened as set. Draws nothing for [ChatWallpaper.None].
 */
@Composable
fun ChatBackground(style: ChatStyle, modifier: Modifier = Modifier) {
    if (style.wallpaper == ChatWallpaper.None) return
    Box(modifier) {
        val layer = Modifier
            .fillMaxSize()
            .then(if (style.blur > 0f) Modifier.blur((style.blur * 28).dp, BlurredEdgeTreatment.Rectangle) else Modifier)
        when (style.wallpaper) {
            ChatWallpaper.Custom -> {
                val photo = rememberChatPhoto(style.photoPath)
                if (photo != null) {
                    Image(photo, null, layer, contentScale = ContentScale.Crop)
                } else {
                    Box(layer.background(MaterialTheme.colorScheme.surfaceContainerHigh))
                }
            }
            else -> WallpaperCanvas(style.wallpaper, layer)
        }
        if (style.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = style.dim)))
    }
}

@Composable
private fun rememberChatPhoto(path: String?): ImageBitmap? {
    val photo by produceState<ImageBitmap?>(null, path) {
        value = path?.let { withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() } }
    }
    return photo
}

/** The built-in backgrounds, in light and dark versions that follow the theme. */
@Composable
private fun WallpaperCanvas(wallpaper: ChatWallpaper, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.4f
    fun c(light: Long, night: Long) = Color(if (dark) night else light)
    val gradient: List<Color>? = when (wallpaper) {
        ChatWallpaper.Mist -> listOf(scheme.primaryContainer, scheme.surfaceContainerLow, scheme.tertiaryContainer)
        ChatWallpaper.Aurora -> listOf(c(0xFFA8E6CF, 0xFF0F3D3E), c(0xFFA3C4F3, 0xFF1B2A49), c(0xFFCDB4DB, 0xFF3B1F4A))
        ChatWallpaper.Dusk -> listOf(c(0xFFFFB4A2, 0xFF3D1F2B), c(0xFFE5989B, 0xFF2B1F3D), c(0xFFB5A8C9, 0xFF141018))
        ChatWallpaper.Mint -> listOf(c(0xFFE9F7EF, 0xFF0B2E1D), c(0xFFB7E4C7, 0xFF12372A))
        ChatWallpaper.Peach -> listOf(c(0xFFFFE5D9, 0xFF3A2420), c(0xFFFFCAD4, 0xFF2E1A22))
        ChatWallpaper.Lagoon -> listOf(c(0xFFCAF0F8, 0xFF03293A), c(0xFF90E0EF, 0xFF014F63), c(0xFF48CAE4, 0xFF023E58))
        else -> null
    }
    val ink = scheme.primary.copy(alpha = if (dark) 0.22f else 0.14f)
    val base = scheme.surfaceContainerLow
    Canvas(modifier) {
        if (gradient != null) {
            drawRect(Brush.linearGradient(gradient, start = Offset.Zero, end = Offset(size.width, size.height)))
            return@Canvas
        }
        drawRect(base)
        val step = 22.dp.toPx()
        when (wallpaper) {
            ChatWallpaper.Dots -> {
                var y = step / 2
                var row = 0
                while (y < size.height) {
                    var x = if (row % 2 == 0) step / 2 else step
                    while (x < size.width) { drawCircle(ink, 2.2.dp.toPx(), Offset(x, y)); x += step }
                    y += step; row++
                }
            }
            ChatWallpaper.Grid -> {
                var x = 0f
                while (x < size.width) { drawLine(ink, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx()); x += step * 1.5f }
                var y = 0f
                while (y < size.height) { drawLine(ink, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()); y += step * 1.5f }
            }
            ChatWallpaper.Waves -> {
                val amp = 6.dp.toPx()
                var y = step
                while (y < size.height + step) {
                    val path = Path().apply {
                        moveTo(0f, y)
                        var x = 0f
                        while (x <= size.width) { lineTo(x, y + amp * sin(x / (step * 2) * 2 * PI).toFloat()); x += 4f }
                    }
                    drawPath(path, ink, style = Stroke(1.5.dp.toPx()))
                    y += step * 1.4f
                }
            }
            else -> Unit
        }
    }
}

/** A small conversation on [style]'s background, to see a change as it's made. */
@Composable
private fun ChatPreview(style: ChatStyle, strings: ChatStrings, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(scheme.background)) {
        ChatBackground(style, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
            Text(
                strings.sampleIn,
                color = style.incomingContent(scheme.onSurface),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(style.incoming(scheme.surfaceContainerHigh))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            Text(
                strings.sampleOut,
                color = style.outgoingContent(scheme.onPrimaryContainer),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.End)
                    .widthIn(max = 240.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(style.outgoing(scheme.primaryContainer))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * Everything about a chat's look, with a live preview: background (built-in or photo), blur,
 * dim, and both bubble colours. Used for the default style and for one conversation's own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatStyleEditor(style: ChatStyle, onChange: (ChatStyle) -> Unit, strings: ChatStrings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { ChatStyleStore.get(context).importPhoto(uri) }
                if (path != null) onChange(style.copy(wallpaper = ChatWallpaper.Custom, photoPath = path))
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ChatPreview(style, strings, Modifier.fillMaxWidth().height(200.dp))

        Text(strings.background, style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ChatWallpaper.entries) { wallpaper ->
                val selected = style.wallpaper == wallpaper
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 84.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable {
                                if (wallpaper == ChatWallpaper.Custom) {
                                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                } else {
                                    onChange(style.copy(wallpaper = wallpaper))
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (wallpaper) {
                            ChatWallpaper.None -> Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            ChatWallpaper.Custom -> {
                                if (style.photoPath != null) ChatBackground(style.copy(wallpaper = ChatWallpaper.Custom, blur = 0f, dim = 0f), Modifier.fillMaxSize())
                                Icon(Icons.Rounded.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.onSurface)
                            }
                            else -> ChatBackground(ChatStyle(wallpaper = wallpaper), Modifier.fillMaxSize())
                        }
                    }
                    Text(strings.wallpaper(wallpaper), style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        if (style.wallpaper != ChatWallpaper.None) {
            LabeledSlider(strings.blur, style.blur, 0f..1f) { onChange(style.copy(blur = it)) }
            LabeledSlider(strings.dim, style.dim, 0f..0.8f) { onChange(style.copy(dim = it)) }
        }

        Text(strings.myBubbles, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Swatch(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)), style.bubbleColor == 0, strings.theme, { onChange(style.copy(bubbleColor = 0)) }, size = 36.dp)
            BUBBLE_COLORS.forEach { argb ->
                Swatch(Brush.linearGradient(listOf(Color(argb), Color(argb))), style.bubbleColor == argb, "", { onChange(style.copy(bubbleColor = argb)) }, size = 36.dp)
            }
        }

        Text(strings.theirBubbles, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Swatch(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHighest)), style.incomingColor == 0, strings.theme, { onChange(style.copy(incomingColor = 0)) }, size = 36.dp)
            INCOMING_COLORS.forEach { argb ->
                Swatch(Brush.linearGradient(listOf(Color(argb), Color(argb))), style.incomingColor == argb, "", { onChange(style.copy(incomingColor = argb)) }, size = 36.dp)
            }
        }
        TextButton(onClick = { onChange(ChatStyle()) }, modifier = Modifier.align(Alignment.End)) { Text(strings.reset) }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(88.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}

/** Appearance's extra card in Messages: the default style of every conversation. */
@Composable
fun DefaultChatStyleSection() {
    val strings = chatStrings()
    val store = ChatStyleStore.get(LocalContext.current)
    val style by store.default.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AppearanceHeader(strings.defaultStyle)
        AppearanceCard(appearanceSegment(0, 1)) { ChatStyleEditor(style, store::setDefault, strings) }
    }
}

/**
 * The conversation menu's "Customise chat": its own style (background, blur, dim, bubbles), or
 * the default one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatStyleSheet(conversationId: String, onDismiss: () -> Unit) {
    val strings = chatStrings()
    val store = ChatStyleStore.get(LocalContext.current)
    val overrides by store.overrides.collectAsState()
    val default by store.default.collectAsState()
    val own = overrides[conversationId]
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(strings.customize, style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(strings.ownStyle, style = MaterialTheme.typography.bodyLarge)
                    Text(strings.ownStyleSummary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = own != null, onCheckedChange = { on -> store.setFor(conversationId, if (on) default else null) })
            }
            if (own != null) {
                ChatStyleEditor(own, { store.setFor(conversationId, it) }, strings)
            } else {
                ChatPreview(default, strings, Modifier.fillMaxWidth().height(200.dp))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(strings.done) }
        }
    }
}
