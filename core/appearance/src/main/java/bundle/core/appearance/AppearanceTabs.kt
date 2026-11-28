package bundle.core.appearance

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Bundle: the tabs of the Appearance screen, one per big kind of customisation. */
enum class AppearanceTab { Themes, Colors, Avatars, IconLanguage }

@Composable
fun AppearanceTabRow(selected: AppearanceTab, strings: AppearanceStrings, onSelect: (AppearanceTab) -> Unit) {
    PrimaryScrollableTabRow(selectedTabIndex = selected.ordinal, edgePadding = 16.dp, containerColor = MaterialTheme.colorScheme.background) {
        listOf(
            AppearanceTab.Themes to strings.tabThemes,
            AppearanceTab.Colors to strings.tabColors,
            AppearanceTab.Avatars to strings.tabAvatars,
            AppearanceTab.IconLanguage to strings.tabIconLanguage,
        ).forEach { (tab, label) ->
            Tab(selected = tab == selected, onClick = { onSelect(tab) }, text = { Text(label) })
        }
    }
}

/** This app's own look, or the bundle's; and "Apply to all apps". Shown on every tab. */
@Composable
fun OwnLookCard(strings: AppearanceStrings) {
    val context = LocalContext.current
    val store = AppearanceStore.get(context)
    val own by store.ownLook.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AppearanceSwitchRow(strings.ownLook, strings.ownLookSummary, own, { store.setOwnLook(it) }, appearanceSegment(0, if (own) 2 else 1))
        if (own) {
            AppearanceCard(appearanceSegment(1, 2)) {
                Button(onClick = {
                    store.applyToAllApps()
                    Toast.makeText(context, strings.applied, Toast.LENGTH_SHORT).show()
                }) { Text(strings.applyAll) }
            }
        }
    }
}

/** The themes: a drawn preview of each, where it shows (home screen or the whole app). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemesTab(strings: AppearanceStrings) {
    val store = AppearanceStore.get(LocalContext.current)
    val appearance by store.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AppearanceHeader(strings.themesHeader)
        AppearanceCard(appearanceSegment(0, 1)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.themesNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThemePack.entries.forEach { pack ->
                        ThemeTile(pack, strings.themeName(pack), appearance.themePack == pack) {
                            store.update { it.copy(themePack = pack) }
                        }
                    }
                }
            }
        }
        if (appearance.themePack != ThemePack.None) {
            AppearanceChoiceRow(
                strings.themeScope,
                listOf(ThemeScope.Home to strings.scopeHome, ThemeScope.Everywhere to strings.scopeEverywhere),
                appearance.themeScope,
                { scope -> store.update { it.copy(themeScope = scope) } },
                appearanceSegment(0, 1),
            )
        }
    }
}

/** A theme's miniature: its home art, then its conversation texture with two bubbles. */
@Composable
private fun ThemeTile(pack: ThemePack, name: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.width(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name; this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(width = 96.dp, height = 140.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(if (selected) 3.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, RoundedCornerShape(16.dp)),
        ) {
            if (pack == ThemePack.None) {
                Box(Modifier.size(width = 96.dp, height = 140.dp), contentAlignment = Alignment.Center) {
                    Text("Aa", style = MaterialTheme.typography.headlineMedium, color = scheme.onSurfaceVariant)
                }
            } else {
                Canvas(Modifier.size(width = 96.dp, height = 90.dp)) { drawThemeBackground(pack) }
                Canvas(Modifier.padding(top = 90.dp).size(width = 96.dp, height = 50.dp)) {
                    drawThemeTexture(pack)
                    drawRoundRect(Color(pack.bubbleTheirs), topLeft = androidx.compose.ui.geometry.Offset(8f, 10f), size = androidx.compose.ui.geometry.Size(size.width * 0.5f, 26f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f))
                    drawRoundRect(Color(pack.bubbleMine), topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.42f, 44f), size = androidx.compose.ui.geometry.Size(size.width * 0.5f, 26f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f))
                }
            }
        }
        Text(name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Colours: light or dark, the palette, and each element's colour over it. */
@Composable
fun ColorsTab(strings: AppearanceStrings) {
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

        AppearanceHeader(strings.elementsHeader)
        val e = appearance.elements
        val rows = listOf(
            Triple(strings.elementAccent, e.accent) { v: Int -> e.copy(accent = v) },
            Triple(strings.elementBackground, e.background) { v: Int -> e.copy(background = v) },
            Triple(strings.elementText, e.text) { v: Int -> e.copy(text = v) },
            Triple(strings.elementBubbleMine, e.bubbleMine) { v: Int -> e.copy(bubbleMine = v) },
            Triple(strings.elementBubbleTheirs, e.bubbleTheirs) { v: Int -> e.copy(bubbleTheirs = v) },
        )
        rows.forEachIndexed { i, (label, value, set) ->
            AppearanceCard(appearanceSegment(i, rows.size + 1)) {
                ElementColorRow(label, value, strings) { v -> store.update { it.copy(elements = set(v)) } }
            }
        }
        AppearanceCard(appearanceSegment(rows.size, rows.size + 1)) {
            Column {
                Text(strings.elementsNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(enabled = !e.isEmpty, onClick = { store.update { it.copy(elements = ElementColors()) } }) { Text(strings.resetElements) }
            }
        }
    }
}

/** The colours offered for one element: Auto, white, black, and the palette's colours. */
private val ELEMENT_CHOICES: List<Int> = listOf(0, 0xFFFFFFFF.toInt(), 0xFF111111.toInt()) +
    Palette.entries.mapNotNull { it.seed?.toInt() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ElementColorRow(label: String, value: Int, strings: AppearanceStrings, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ELEMENT_CHOICES.forEach { argb ->
                val brush = if (argb == 0) Brush.sweepGradient(listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFE53935))) else Brush.linearGradient(listOf(Color(argb), Color(argb)))
                Swatch(brush, value == argb, if (argb == 0) strings.auto else "#%06X".format(argb and 0xFFFFFF), onClick = { onPick(argb) }, size = 36.dp)
            }
        }
    }
}

/** Contact pictures and the expressive interface. */
@Composable
fun AvatarsTab(strings: AppearanceStrings) {
    val store = AppearanceStore.get(LocalContext.current)
    val appearance by store.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AppearanceHeader(strings.avatars)
        AppearanceChoiceRow(
            strings.avatars,
            listOf(AvatarStyle.Circle to strings.avatarCircle, AvatarStyle.Expressive to strings.avatarExpressive),
            appearance.avatarStyle,
            { style -> store.update { it.copy(avatarStyle = style) } },
            appearanceSegment(0, 2),
        )
        AppearanceSwitchRow(
            strings.expressiveUi, strings.expressiveUiSummary, appearance.expressiveUi,
            { on -> store.update { it.copy(expressiveUi = on) } },
            appearanceSegment(1, 2),
        )
    }
}

/** This app's language (the icon is added by the dialog when the app has one). */
@Composable
fun LanguageSection(strings: AppearanceStrings) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val store = AppearanceStore.get(LocalContext.current)
    val appearance by store.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
