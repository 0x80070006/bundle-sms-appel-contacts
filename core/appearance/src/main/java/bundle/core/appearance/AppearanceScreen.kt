package bundle.core.appearance

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Grouped-card corners, as in the apps' settings: big outside, tight between rows. */
fun appearanceSegment(index: Int, count: Int, large: Dp = 24.dp, small: Dp = 6.dp): Shape = when {
    count == 1 -> RoundedCornerShape(large)
    index == 0 -> RoundedCornerShape(topStart = large, topEnd = large, bottomStart = small, bottomEnd = small)
    index == count - 1 -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = large, bottomEnd = large)
    else -> RoundedCornerShape(small)
}

/** A settings card in the apps' style. */
@Composable
fun AppearanceCard(shape: Shape, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceBright,
        modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) { Box(Modifier.padding(16.dp)) { content() } }
}

/** A section title in the theme's primary colour. */
@Composable
fun AppearanceHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Title and summary on the left, [trailing] (a switch) on the right. */
@Composable
fun AppearanceSwitchRow(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit, shape: Shape) {
    Surface(
        onClick = { onChange(!checked) },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (summary != null) {
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

/** Segmented choice with a title above, in a card. */
@Composable
fun <T> AppearanceChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, shape: Shape) {
    AppearanceCard(shape) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = value == selected,
                        onClick = { onSelect(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) { Text(label, maxLines = 1) }
                }
            }
        }
    }
}

/**
 * The bundle's Appearance screen, full screen over the app: theme, colours, pure black, contact
 * pictures and language, shared by the three apps; then this app's icon ([appIcon]), which each
 * app sets for itself. [extra] adds an app's own sections below
 * (Messages: chat backgrounds and bubbles).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceDialog(
    onDismiss: () -> Unit,
    appIcon: AppIconConfig? = null,
    extra: LazyListScope.(AppearanceStrings) -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val strings = appearanceStrings()
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(strings.appearance) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, strings.back) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                appearanceItems(strings)
                if (appIcon != null) item(key = "appearance-icon") { AppIconSection(appIcon, strings) }
                extra(strings)
            }
        }
    }
}

/** The shared sections, for a screen that has its own list. */
fun LazyListScope.appearanceItems(strings: AppearanceStrings) {
    item(key = "appearance-note") {
        Text(
            strings.sharedNote,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )
    }
    item(key = "appearance-theme") { ThemeSection(strings) }
}

@Composable
private fun ThemeSection(strings: AppearanceStrings) {
    val store = AppearanceStore.get(LocalContext.current)
    val appearance by store.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AppearanceHeader(strings.theme)
        AppearanceChoiceRow(
            strings.theme,
            listOf(ThemeMode.System to strings.themeSystem, ThemeMode.Light to strings.themeLight, ThemeMode.Dark to strings.themeDark),
            appearance.themeMode,
            { mode -> store.update { it.copy(themeMode = mode) } },
            appearanceSegment(0, 2),
        )
        AppearanceSwitchRow(
            strings.pureBlack, strings.pureBlackSummary, appearance.pureBlack,
            { on -> store.update { it.copy(pureBlack = on) } },
            appearanceSegment(1, 2),
        )

        AppearanceHeader(strings.colors)
        AppearanceCard(appearanceSegment(0, 1)) { PalettePicker(appearance, strings) { change -> store.update(change) } }

        AppearanceHeader(strings.avatars)
        AppearanceChoiceRow(
            strings.avatars,
            listOf(AvatarStyle.Circle to strings.avatarCircle, AvatarStyle.Expressive to strings.avatarExpressive),
            appearance.avatarStyle,
            { style -> store.update { it.copy(avatarStyle = style) } },
            appearanceSegment(0, 1),
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AppearanceHeader(strings.language)
            AppearanceChoiceRow(
                strings.languageNote,
                listOf(AppLanguage.System to strings.languageSystem, AppLanguage.French to "Français", AppLanguage.English to "English"),
                appearance.language,
                { lang -> store.update { it.copy(language = lang) } },
                appearanceSegment(0, 1),
            )
        }
    }
}

/** Colour swatches: wallpaper, the palettes, and a custom colour with hue and intensity sliders. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PalettePicker(appearance: Appearance, strings: AppearanceStrings, onChange: ((Appearance) -> Appearance) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(strings.colorsSummary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Palette.entries.forEach { palette ->
                val brush = when (palette) {
                    Palette.Wallpaper -> Brush.sweepGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary),
                    )
                    Palette.Custom -> Brush.sweepGradient(
                        listOf(Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFE53935)),
                    )
                    else -> Brush.linearGradient(listOf(Color(palette.seed!!), Color(palette.seed)))
                }
                Swatch(
                    brush = brush,
                    selected = appearance.palette == palette,
                    label = strings.palette(palette),
                    icon = palette == Palette.Wallpaper,
                    onClick = { onChange { it.copy(palette = palette) } },
                )
            }
        }
        if (appearance.palette == Palette.Custom) {
            val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(appearance.customSeed, it) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(Color(appearance.customSeed)))
                Text(strings.hue, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(72.dp))
                Slider(
                    value = hsv[0],
                    onValueChange = { h -> onChange { it.copy(customSeed = android.graphics.Color.HSVToColor(floatArrayOf(h, hsv[1], hsv[2]))) } },
                    valueRange = 0f..359f,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.size(32.dp))
                Text(strings.saturation, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(72.dp))
                Slider(
                    value = hsv[1],
                    onValueChange = { s -> onChange { it.copy(customSeed = android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], s, hsv[2].coerceAtLeast(0.55f)))) } },
                    valueRange = 0.2f..1f,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A round colour swatch with a check when selected, and its name below. */
@Composable
fun Swatch(brush: Brush, selected: Boolean, label: String, onClick: () -> Unit, icon: Boolean = false, size: Dp = 48.dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(size + 16.dp)) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                .padding(if (selected) 5.dp else 0.dp)
                .clip(CircleShape)
                .background(brush)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when {
                selected -> Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
                icon -> Icon(Icons.Rounded.Wallpaper, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** The colour's ARGB, for storing. */
fun Color.argb(): Int = toArgb()
