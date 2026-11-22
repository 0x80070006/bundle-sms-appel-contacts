package bundle.core.appearance

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The background colours an app icon can take: the same list as scripts/make_icons.py, which
 * makes one launcher alias ("bundle.launcher.<name>") per colour, except the app's own colour,
 * which is its default entry.
 */
enum class IconColor(val argb: Long) {
    Black(0xFF000000), Blue(0xFF1A73E8), Green(0xFF188038), Orange(0xFFD2550A), Red(0xFFC5221F),
    Pink(0xFFC2185B), Purple(0xFF7B1FA2), Indigo(0xFF3949AB), Teal(0xFF00796B), Graphite(0xFF3C4043),
}

/**
 * One app's icon: its white [glyph] (the app's bundle_icon_glyph drawable), its own colour, and
 * the launcher entry that shows it by default.
 */
data class AppIconConfig(
    @DrawableRes val glyph: Int,
    val appName: String,
    val defaultColor: IconColor,
    val defaultAlias: String = "bundle.launcher.Default",
)

/** Switches the launcher icon by enabling one launcher alias and disabling the others. */
object AppIcons {
    /** null stands for the app's own colour. */
    fun choices(config: AppIconConfig): List<IconColor?> = listOf<IconColor?>(null) + IconColor.entries.filter { it != config.defaultColor }

    private fun component(context: Context, config: AppIconConfig, color: IconColor?) =
        ComponentName(context.packageName, if (color == null) config.defaultAlias else "bundle.launcher.${color.name}")

    /** The colour the launcher shows now (null: the app's own). */
    fun current(context: Context, config: AppIconConfig): IconColor? {
        val pm = context.packageManager
        return choices(config).filterNotNull().firstOrNull { color ->
            runCatching { pm.getComponentEnabledSetting(component(context, config, color)) }.getOrNull() ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
    }

    fun apply(context: Context, config: AppIconConfig, color: IconColor?) {
        val pm = context.packageManager
        // Enable the new entry first, so the app is never without one.
        pm.setComponentEnabledSetting(component(context, config, color), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        choices(config).filter { it != color }.forEach { other ->
            pm.setComponentEnabledSetting(component(context, config, other), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}

/** The icon as the home screen shows it: [glyph] on [background] in a rounded square. */
@Composable
fun AppIconPreview(@DrawableRes glyph: Int, background: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 30))
            .background(background),
    ) {
        Image(painterResource(glyph), null, Modifier.fillMaxSize())
    }
}

/**
 * Appearance's "App icon" card: a live preview of the icon on a home-screen-like strip, the
 * colour choices (the app's own, black for a dark home screen, and the others) and Apply.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppIconSection(config: AppIconConfig, strings: AppearanceStrings) {
    val context = LocalContext.current
    var applied by remember { mutableStateOf(AppIcons.current(context, config)) }
    var picked by remember { mutableStateOf(applied) }
    fun colorOf(c: IconColor?) = Color(c?.argb ?: config.defaultColor.argb)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AppearanceHeader(strings.appIcon)
        AppearanceCard(appearanceSegment(0, 1)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Preview: the picked icon among neutral neighbours, as on a home screen.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)))
                        .padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(percent = 30)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppIconPreview(config.glyph, colorOf(picked), 72.dp)
                        Text(config.appName, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                    }
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(percent = 30)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcons.choices(config).forEach { choice ->
                        Swatch(
                            brush = Brush.linearGradient(listOf(colorOf(choice), colorOf(choice))),
                            selected = choice == picked,
                            label = strings.iconColor(choice),
                            onClick = { picked = choice },
                            size = 40.dp,
                        )
                    }
                }
                Text(strings.appIconNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { AppIcons.apply(context, config, picked); applied = picked },
                    enabled = picked != applied,
                    modifier = Modifier.align(Alignment.End),
                ) { Text(strings.apply) }
            }
        }
    }
}
