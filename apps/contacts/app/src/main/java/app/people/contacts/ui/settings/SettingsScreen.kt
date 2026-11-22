package app.people.contacts.ui.settings

import android.Manifest
import androidx.compose.material.icons.outlined.Cake
import app.people.contacts.calendar.BirthdayCalendar
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.people.contacts.R
import app.people.contacts.data.AccountKeys
import app.people.contacts.data.ContactAccount
import app.people.contacts.data.ListLayout
import bundle.core.appearance.AppearanceDialog
import bundle.core.appearance.AppIconConfig
import bundle.core.appearance.IconColor
import androidx.compose.material.icons.outlined.Palette
import app.people.contacts.data.SortOrder
import app.people.contacts.data.hasCallAndTextAccess
import app.people.contacts.ui.ContactsViewModel
import app.people.contacts.ui.components.ListRow
import app.people.contacts.ui.components.SEGMENT_GAP
import app.people.contacts.ui.components.SectionHeader
import app.people.contacts.ui.components.TextInputDialog
import app.people.contacts.ui.components.segmentShape

/** Settings: display, the birthday calendar and accounts, in grouped cards. */
@Composable
fun SettingsScreen(viewModel: ContactsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val order by viewModel.sortOrder.collectAsStateWithLifecycle()
    val hideNoPhone by viewModel.hideWithoutPhone.collectAsStateWithLifecycle()
    val recentsOn by viewModel.settings.recentsFromCallsAndTexts.collectAsStateWithLifecycle(false)
    val hidden by viewModel.settings.hiddenAccounts.collectAsStateWithLifecycle(emptySet())
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<ContactAccount?>(null) }
    var deleting by remember { mutableStateOf<ContactAccount?>(null) }
    var adding by remember { mutableStateOf(false) }
    val layout by viewModel.listLayout.collectAsStateWithLifecycle()
    val labelOrder by viewModel.labelOrder.collectAsStateWithLifecycle()
    var orderingLabels by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }

    val calendarOn by viewModel.settings.birthdayCalendar.collectAsStateWithLifecycle(false)
    val calendarAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.setBirthdayCalendar(BirthdayCalendar.canWrite(context))
    }
    val calendarEffective = calendarOn && BirthdayCalendar.canWrite(context)
    val setCalendar: (Boolean) -> Unit = { on ->
        when {
            !on -> viewModel.setBirthdayCalendar(false)
            BirthdayCalendar.canWrite(context) -> viewModel.setBirthdayCalendar(true)
            else -> calendarAccess.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        }
    }
    val recentsAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.setRecentsFromCallsAndTexts(hasCallAndTextAccess(context))
    }
    // Shown off if access was taken away in system settings since it was turned on.
    val recentsEffective = recentsOn && hasCallAndTextAccess(context)
    val setRecents: (Boolean) -> Unit = { on ->
        when {
            !on -> viewModel.setRecentsFromCallsAndTexts(false)
            hasCallAndTextAccess(context) -> viewModel.setRecentsFromCallsAndTexts(true)
            else -> recentsAccess.launch(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_SMS))
        }
    }

    Scaffold(
        topBar = {
            // Opaque in the background colour (status bar included), so the list scrolls behind it.
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            // Bundle: the look shared by Contacts, Phone and Messages.
            item(key = "appearance") {
                ListRow(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.settings_appearance_title),
                    summary = stringResource(R.string.settings_appearance_summary),
                    shape = segmentShape(0, 1),
                    onClick = { showAppearance = true },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(key = "display-header") { SectionHeader(stringResource(R.string.settings_display)) }
            item(key = "sort") { SortRow(order, viewModel::setSortOrder) }
            item(key = "layout") { LayoutRow(layout, viewModel::setListLayout) }
            item(key = "label-order") {
                ListRow(
                    icon = Icons.AutoMirrored.Outlined.Label,
                    title = stringResource(R.string.settings_label_order_title),
                    summary = stringResource(R.string.settings_label_order_summary),
                    shape = segmentShape(2, 5),
                    onClick = { orderingLabels = true },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item(key = "hide-no-phone") {
                ListRow(
                    icon = Icons.Outlined.Phone,
                    title = stringResource(R.string.settings_hide_no_phone_title),
                    summary = stringResource(R.string.settings_hide_no_phone_summary),
                    shape = segmentShape(3, 5),
                    onClick = { viewModel.setHideWithoutPhone(!hideNoPhone) },
                    trailing = { Switch(checked = hideNoPhone, onCheckedChange = viewModel::setHideWithoutPhone) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item(key = "recents") {
                ListRow(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.settings_recents_title),
                    summary = stringResource(R.string.settings_recents_summary),
                    shape = segmentShape(4, 5),
                    onClick = { setRecents(!recentsEffective) },
                    trailing = { Switch(checked = recentsEffective, onCheckedChange = setRecents) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }


            item(key = "calendar-header") { SectionHeader(stringResource(R.string.settings_birthdays)) }
            item(key = "calendar") {
                ListRow(
                    icon = Icons.Outlined.Cake,
                    title = stringResource(R.string.settings_calendar_title),
                    summary = stringResource(R.string.settings_calendar_summary),
                    shape = segmentShape(0, 1),
                    onClick = { setCalendar(!calendarEffective) },
                    trailing = { Switch(checked = calendarEffective, onCheckedChange = setCalendar) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item(key = "accounts-header") { SectionHeader(stringResource(R.string.settings_accounts)) }
            val rows = accounts.size + 1
            itemsIndexed(accounts, key = { _, a -> "account-${a.type}|${a.name}" }) { index, account ->
                val visible = !AccountKeys.isHidden(account.type, account.name, hidden)
                AccountRow(
                    account, visible, segmentShape(index, rows),
                    onVisibleChange = { viewModel.setAccountVisible(account, it) },
                    onRename = { renaming = account },
                    onDelete = { deleting = account },
                )
            }
            item(key = "account-add") {
                ListRow(
                    icon = Icons.Rounded.Add,
                    title = stringResource(R.string.add_on_device_account),
                    shape = segmentShape(rows - 1, rows),
                    onClick = { adding = true },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }

    if (showAppearance) {
        AppearanceDialog(
            onDismiss = { showAppearance = false },
            appIcon = AppIconConfig(R.drawable.bundle_icon_glyph, stringResource(R.string.app_name), IconColor.Orange),
        )
    }
    if (orderingLabels) {
        LabelOrderDialog(labelOrder, onMove = viewModel::moveLabel, onDismiss = { orderingLabels = false })
    }
    if (adding) {
        TextInputDialog(
            title = stringResource(R.string.add_on_device_account),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            fieldLabel = stringResource(R.string.account_name),
            onConfirm = { name -> adding = false; viewModel.createOnDeviceAccount(name) },
            onDismiss = { adding = false },
        )
    }
    renaming?.let { account ->
        TextInputDialog(
            title = stringResource(R.string.rename_account),
            initial = account.name.orEmpty(),
            confirmLabel = stringResource(R.string.rename),
            onConfirm = { name -> renaming = null; viewModel.renameOnDeviceAccount(account, name) },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { account ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_account_title, account.label)) },
            text = { Text(stringResource(R.string.delete_account_body)) },
            confirmButton = { TextButton(onClick = { deleting = null; viewModel.deleteOnDeviceAccount(account) }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** "Sort by" first or last name, as a segmented button under the title. */
@Composable
private fun SortRow(order: SortOrder, onChange: (SortOrder) -> Unit) {
    Surface(shape = segmentShape(0, 5), color = MaterialTheme.colorScheme.surfaceBright, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.AutoMirrored.Rounded.List, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.padding(start = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    Text(stringResource(R.string.sort_by), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.sort_by_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val options = listOf(SortOrder.FirstName to R.string.first_name, SortOrder.LastName to R.string.last_name)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = order == value,
                            onClick = { onChange(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) { Text(stringResource(label)) }
                    }
                }
            }
        }
    }
}

/** Bundle: "Contacts list" Classic or By label, as a segmented button like Sort by. */
@Composable
private fun LayoutRow(layout: ListLayout, onChange: (ListLayout) -> Unit) {
    Surface(shape = segmentShape(1, 5), color = MaterialTheme.colorScheme.surfaceBright, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.ViewAgenda, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.padding(start = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    Text(stringResource(R.string.settings_layout_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_layout_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val options = listOf(ListLayout.Classic to R.string.layout_classic, ListLayout.ByLabel to R.string.layout_by_label)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = layout == value,
                            onClick = { onChange(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) { Text(stringResource(label)) }
                    }
                }
            }
        }
    }
}

/** Bundle: the labels in list order, each with Move up and Move down. Changes save at once. */
@Composable
private fun LabelOrderDialog(order: List<String>, onMove: (String, Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_label_order_title)) },
        text = {
            if (order.isEmpty()) {
                Text(stringResource(R.string.label_order_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP)) {
                    itemsIndexed(order, key = { _, title -> title }) { index, title ->
                        Surface(
                            shape = segmentShape(index, order.size),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth().animateItem(),
                        ) {
                            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                IconButton(onClick = { onMove(title, -1) }, enabled = index > 0) {
                                    Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.move_up, title))
                                }
                                IconButton(onClick = { onMove(title, 1) }, enabled = index < order.lastIndex) {
                                    Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.move_down, title))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

/** An account: shown or hidden, and for on-device accounts the user made, rename and delete. */
@Composable
private fun AccountRow(
    account: ContactAccount,
    visible: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onVisibleChange: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val summary = when {
        account.type.isNullOrEmpty() -> stringResource(R.string.account_phone_summary)
        account.userMade -> stringResource(R.string.account_on_device_summary)
        else -> account.type
    }
    ListRow(
        icon = if (account.onDevice) Icons.Outlined.PhoneAndroid else Icons.Outlined.PersonOutline,
        title = account.label,
        summary = summary,
        shape = shape,
        onClick = { onVisibleChange(!visible) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (account.userMade) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onRename() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
                        }
                    }
                }
                Switch(checked = visible, onCheckedChange = onVisibleChange)
            }
        },
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}
