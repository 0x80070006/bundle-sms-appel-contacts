package com.android.messaging.ui.appsettings.twill

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import bundle.core.appearance.AppIconConfig
import bundle.core.appearance.AppearanceDialog
import bundle.core.appearance.InstallHelpDialog
import bundle.core.appearance.installStrings
import androidx.compose.material.icons.rounded.Info
import bundle.core.appearance.IconColor
import bundle.core.appearance.appearanceStrings
import com.android.messaging.ui.bundle.DefaultChatStyleSection
import com.android.messaging.ui.bundle.chatStrings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.messaging.R
import com.android.messaging.ui.appsettings.common.SettingsCategoryHeader
import com.android.messaging.ui.appsettings.common.SettingsClickableItem
import com.android.messaging.ui.appsettings.common.SettingsGroupBreak
import com.android.messaging.ui.appsettings.common.SettingsGroupTitle
import com.android.messaging.ui.appsettings.common.SettingsLazyColumn
import com.android.messaging.ui.appsettings.common.SettingsSwitchItem
import com.android.messaging.ui.appsettings.common.SettingsTopAppBar
import com.android.messaging.ui.core.MessagingPreviewTheme

/**
 * Twill's top-level Settings: one entry per messaging protocol (SMS, Nostr, Delta Chat),
 * then app-wide items such as open source licenses. Protocol-specific settings live one level down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TwillSettingsScreen(
    onNavigateBack: () -> Unit,
    onSmsSettingsClick: () -> Unit,
    onLicensesClick: () -> Unit,
    modifier: Modifier = Modifier,
    onNostrSettingsClick: () -> Unit = {},
    onDeltaChatSettingsClick: () -> Unit = {},
    isSmsEnabled: Boolean = true,
    /** Test builds only: upstream's debug options, formerly in the inbox's menu. */
    onDebugClick: (() -> Unit)? = null,
    isBottomBarEnabled: Boolean = false,
    onBottomBarChange: (Boolean) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // Bundle: the look shared with Contacts and Phone, this app's icon, and the chats' style.
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var showInstallHelp by rememberSaveable { mutableStateOf(false) }
    if (showInstallHelp) InstallHelpDialog(onDismiss = { showInstallHelp = false })
    if (showAppearance) {
        AppearanceDialog(
            onDismiss = { showAppearance = false },
            appIcon = AppIconConfig(
                glyph = R.drawable.bundle_icon_glyph,
                appName = stringResource(R.string.app_name),
                defaultColor = IconColor.Blue,
                defaultAlias = "com.android.messaging.ui.conversationlist.ConversationListActivity",
            ),
            extra = { item(key = "chat-style") { DefaultChatStyleSection() } },
        )
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SettingsTopAppBar(
                title = stringResource(R.string.settings_activity_title),
                onNavigateBack = onNavigateBack,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        SettingsLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "appearance") {
                val strings = appearanceStrings()
                SettingsClickableItem(
                    title = strings.appearance,
                    summary = chatStrings().appearanceSummary,
                    icon = Icons.Rounded.Palette,
                    onClick = { showAppearance = true },
                )
            }
            item(key = "install_help") {
                val help = installStrings()
                SettingsClickableItem(
                    title = help.rowTitle,
                    summary = help.rowSummary,
                    icon = Icons.Rounded.Info,
                    onClick = { showInstallHelp = true },
                )
            }
            item(key = "services_header", contentType = SettingsGroupTitle) {
                SettingsCategoryHeader(title = stringResource(R.string.twill_settings_services))
            }
            item(key = "sms_settings") {
                SettingsClickableItem(
                    title = stringResource(R.string.sms_settings_title),
                    summary = stringResource(
                        when {
                            isSmsEnabled -> R.string.sms_settings_summary
                            else -> R.string.twill_sms_off_summary
                        },
                    ),
                    onClick = onSmsSettingsClick,
                )
            }
            item(key = "nostr_settings") {
                SettingsClickableItem(
                    title = stringResource(R.string.nostr_settings_title),
                    summary = stringResource(R.string.nostr_settings_summary),
                    onClick = onNostrSettingsClick,
                )
            }
            item(key = "deltachat_settings") {
                SettingsClickableItem(
                    title = stringResource(R.string.deltachat_settings_title),
                    summary = stringResource(R.string.deltachat_settings_summary),
                    onClick = onDeltaChatSettingsClick,
                )
            }
            item(key = "inbox_divider", contentType = SettingsGroupBreak) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            item(key = "inbox_header", contentType = SettingsGroupTitle) {
                SettingsCategoryHeader(title = stringResource(R.string.twill_settings_inbox))
            }
            item(key = "bottom_bar") {
                SettingsSwitchItem(
                    title = stringResource(R.string.twill_bottom_bar),
                    summary = stringResource(R.string.twill_bottom_bar_summary),
                    checked = isBottomBarEnabled,
                    onCheckedChange = onBottomBarChange,
                )
            }
            item(key = "about_divider", contentType = SettingsGroupBreak) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            item(key = "about_header", contentType = SettingsGroupTitle) {
                SettingsCategoryHeader(title = stringResource(R.string.twill_settings_about))
            }
            item(key = "licenses") {
                SettingsClickableItem(
                    title = stringResource(R.string.menu_license),
                    onClick = onLicensesClick,
                )
            }
            if (onDebugClick != null) {
                item(key = "debug") {
                    SettingsClickableItem(
                        title = stringResource(R.string.action_debug_options),
                        summary = stringResource(R.string.twill_debug_summary),
                        onClick = onDebugClick,
                    )
                }
            }
        }
    }
}

/** Android Studio preview of the settings screen, light and dark. */
@PreviewLightDark
@Composable
private fun TwillSettingsScreenPreview() {
    MessagingPreviewTheme {
        TwillSettingsScreen(
            onNavigateBack = {},
            onSmsSettingsClick = {},
            onLicensesClick = {},
        )
    }
}
