package app.people.contacts.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.people.contacts.R
import app.people.contacts.sync.ContactSync
import app.people.contacts.sync.SyncProblem
import app.people.contacts.sync.SyncStatus
import app.people.contacts.sync.SyncWorker
import app.people.contacts.sync.syncProblemOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Settings row's summary: off, the last sync, or what stopped it. */
@Composable
fun syncSummary(status: SyncStatus): String = when {
    !status.configured -> stringResource(R.string.sync_summary_off)
    status.problem != null -> problemText(status.problem, status.detail)
    status.lastAt == 0L -> stringResource(R.string.sync_never)
    else -> stringResource(R.string.sync_summary_last, DateUtils.getRelativeTimeSpanString(status.lastAt).toString())
}

@Composable
private fun problemText(problem: SyncProblem, detail: String): String = when (problem) {
    SyncProblem.Auth -> stringResource(R.string.sync_problem_auth)
    SyncProblem.Network -> stringResource(R.string.sync_problem_network)
    SyncProblem.Certificate -> stringResource(R.string.sync_problem_certificate)
    SyncProblem.Passphrase -> stringResource(R.string.sync_problem_passphrase)
    SyncProblem.Aborted -> stringResource(R.string.sync_problem_aborted)
    SyncProblem.Permission -> stringResource(R.string.sync_problem_permission)
    SyncProblem.Format -> stringResource(R.string.sync_problem_format)
    SyncProblem.NotSetUp, SyncProblem.Other -> stringResource(R.string.sync_problem_other, detail)
}

/** The Nextcloud sync screen: set it up, or see the last sync and sync now. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ContactSync.refresh(context) }
    val status by ContactSync.status.collectAsState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.sync_title)) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.sync_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (status.configured) Connected(status) else SetUp()
                Text(stringResource(R.string.sync_backup_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.sync_security), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SetUp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    var phrase2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var allAccounts by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Pair<SyncProblem, String>?>(null) }
    val short = phrase.isNotEmpty() && phrase.length < 10
    val mismatch = phrase2.isNotEmpty() && phrase != phrase2
    val ready = server.isNotBlank() && user.isNotBlank() && password.isNotBlank() && phrase.length >= 10 && phrase == phrase2 && !busy

    Card {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(server, { server = it }, R.string.sync_server, hint = stringResource(R.string.sync_server_hint), keyboard = KeyboardType.Uri)
            Field(user, { user = it }, R.string.sync_user)
            Field(password, { password = it }, R.string.sync_password, secret = true)
            Text(stringResource(R.string.sync_password_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Field(phrase, { phrase = it }, R.string.sync_passphrase, secret = true, error = if (short) stringResource(R.string.sync_passphrase_short) else null)
            Field(phrase2, { phrase2 = it }, R.string.sync_passphrase_confirm, secret = true, error = if (mismatch) stringResource(R.string.sync_passphrase_mismatch) else null)
            Text(stringResource(R.string.sync_passphrase_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            AccountsSwitch(allAccounts) { allAccounts = it }
            error?.let { (problem, detail) -> Text(problemText(problem, detail), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (busy) {
                Text(stringResource(R.string.sync_connecting), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Button(
                enabled = ready,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        val failure = withContext(Dispatchers.IO) {
                            runCatching { ContactSync.connect(context, server, user, password, phrase.toCharArray(), allAccounts) }.exceptionOrNull()
                        }
                        if (failure == null) {
                            phrase = ""; phrase2 = ""; password = ""
                            SyncWorker.schedule(context)
                            withContext(Dispatchers.IO) { ContactSync.run(context) }
                        } else {
                            error = syncProblemOf(failure) to (failure.message ?: failure.javaClass.simpleName)
                        }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.sync_connect)) }
        }
    }
}

@Composable
private fun Connected(status: SyncStatus) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmStop by remember { mutableStateOf(false) }
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.sync_connected_as, status.user, status.server.substringAfter("://").substringBefore('/')), style = MaterialTheme.typography.titleMedium)
            Text(syncSummary(status), style = MaterialTheme.typography.bodyMedium, color = if (status.problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            status.report?.let { r ->
                Text(stringResource(R.string.sync_result, r.created, r.updated, r.deleted, r.total, r.devices), style = MaterialTheme.typography.bodyMedium)
                if (r.suggestions > 0) Text(stringResource(R.string.sync_suggestions, r.suggestions), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                if (r.skippedDeletes > 0) Text(stringResource(R.string.sync_skipped, r.skippedDeletes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                if (r.failures > 0) Text(stringResource(R.string.sync_failures, r.failures), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            if (status.problem == null && status.detail.isNotBlank()) {
                Text(stringResource(R.string.sync_unreadable) + "\n" + status.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (status.running) {
                Text(stringResource(R.string.sync_running), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            AccountsSwitch(status.allAccounts) { ContactSync.setAllAccounts(context, it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !status.running, onClick = { scope.launch(Dispatchers.IO) { ContactSync.run(context) } }) { Text(stringResource(R.string.sync_now)) }
            }
            OutlinedButton(onClick = { confirmStop = true }) { Text(stringResource(R.string.sync_disconnect)) }
        }
    }
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.sync_disconnect)) },
            text = { Text(stringResource(R.string.sync_disconnect_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    SyncWorker.cancel(context)
                    ContactSync.disconnect(context)
                }) { Text(stringResource(R.string.sync_disconnect)) }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** "Also Google and other accounts' contacts", on by default. */
@Composable
private fun AccountsSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.sync_all_accounts), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.sync_all_accounts_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceBright, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: Int, hint: String? = null, secret: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, error: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        placeholder = hint?.let { { Text(it) } },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}
