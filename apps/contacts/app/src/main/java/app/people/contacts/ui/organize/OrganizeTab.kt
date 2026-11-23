package app.people.contacts.ui.organize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.people.contacts.R
import app.people.contacts.ui.ContactsViewModel
import app.people.contacts.ui.components.ListRow
import app.people.contacts.ui.components.SEGMENT_GAP
import app.people.contacts.ui.components.SectionHeader
import app.people.contacts.ui.components.TextInputDialog
import app.people.contacts.ui.components.segmentShape
import kotlinx.coroutines.launch

/** The Organize tab: labels, then Merge duplicates and Settings. */
@Composable
fun OrganizeTab(
    viewModel: ContactsViewModel,
    contentPadding: PaddingValues,
    onOpenLabel: (Long) -> Unit,
    onOpenMerge: () -> Unit,
    onOpenFix: () -> Unit,
) {
    val labels by viewModel.labels.collectAsStateWithLifecycle()
    val duplicates by viewModel.duplicates.collectAsStateWithLifecycle()
    val fixes by viewModel.fixes.collectAsStateWithLifecycle()
    val backup = rememberBackupActions(viewModel)
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    val list = labels.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
    ) {
        item(key = "labels-header") { SectionHeader(stringResource(R.string.section_labels)) }
        val rows = list.size + 1
        itemsIndexed(list, key = { _, l -> "label-${l.id}" }) { index, label ->
            ListRow(
                icon = Icons.AutoMirrored.Outlined.Label,
                title = label.title,
                // One label across every account: its contacts, wherever they're saved.
                summary = pluralStringResource(R.plurals.contact_count, label.memberIds.size, label.memberIds.size),
                shape = segmentShape(index, rows),
                onClick = { onOpenLabel(label.id) },
                modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
            )
        }
        item(key = "label-create") {
            ListRow(
                icon = Icons.Rounded.Add,
                title = stringResource(R.string.create_label),
                shape = segmentShape(rows - 1, rows),
                onClick = { creating = true },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        item(key = "manage-header") { SectionHeader(stringResource(R.string.fix_and_manage)) }
        item(key = "merge") {
            ListRow(
                icon = Icons.Outlined.AutoFixHigh,
                title = stringResource(R.string.merge_duplicates),
                summary = if (duplicates.isEmpty()) stringResource(R.string.merge_none)
                else pluralStringResource(R.plurals.merge_suggestions, duplicates.size, duplicates.size),
                summaryColor = if (duplicates.isEmpty()) null else MaterialTheme.colorScheme.primary,
                shape = segmentShape(0, 4),
                onClick = onOpenMerge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item(key = "fix") {
            val count = fixes?.count ?: 0
            ListRow(
                icon = Icons.Outlined.Healing,
                title = stringResource(R.string.fix_contacts),
                summary = if (count == 0) stringResource(R.string.fix_summary_none)
                else pluralStringResource(R.plurals.fix_suggestions, count, count),
                summaryColor = if (count == 0) null else MaterialTheme.colorScheme.primary,
                shape = segmentShape(1, 4),
                onClick = onOpenFix,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item(key = "import") {
            ListRow(
                icon = Icons.Outlined.Download,
                title = stringResource(R.string.import_contacts),
                summary = stringResource(R.string.import_summary),
                shape = segmentShape(2, 4),
                onClick = backup.import,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item(key = "export") {
            ListRow(
                icon = Icons.Outlined.Upload,
                title = stringResource(R.string.export_contacts),
                summary = stringResource(R.string.export_summary),
                shape = segmentShape(3, 4),
                onClick = backup.export,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    if (creating) {
        val context = androidx.compose.ui.platform.LocalContext.current
        TextInputDialog(
            title = stringResource(R.string.create_label),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            fieldLabel = stringResource(R.string.label_name),
            onConfirm = { name ->
                creating = false
                scope.launch {
                    if (!viewModel.labelStore.create(name)) {
                        android.widget.Toast.makeText(context, R.string.save_failed, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { creating = false },
        )
    }
}
