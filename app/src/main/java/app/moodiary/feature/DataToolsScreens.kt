package app.moodiary.feature

import androidx.compose.ui.res.stringResource
import app.moodiary.R

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.moodiary.domain.*
import app.moodiary.core.backup.PreparedRestore
import androidx.activity.result.IntentSenderRequest
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Composable fun BackupScreen(preferences: AppPreferences, vm: JournalViewModel, tools: DataToolsViewModel = hiltViewModel()) {
    val busy by tools.busy.collectAsStateWithLifecycle()
    val status by tools.status.collectAsStateWithLifecycle()
    val prepared by tools.prepared.collectAsStateWithLifecycle()
    val cloud by tools.cloud.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var connected by remember { mutableStateOf(tools.drive.connected()) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) tools.run(context.getString(R.string.data_tools_backup_saved)) { tools.backups.saveLocal(uri) } }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) tools.run(context.getString(R.string.data_tools_backup_validated)) { tools.prepared.value = tools.backups.prepareRestore(uri) } }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) tools.run(context.getString(R.string.data_tools_folder_selected)) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        vm.settings.update { it.copy(backupTreeUri = uri.toString()) }
    } }
    val signIn = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        try { tools.drive.accept(tools.drive.authorizationClient.getAuthorizationResultFromIntent(result.data)); connected = tools.drive.connected(); tools.status.value = context.getString(R.string.data_tools_drive_connected) }
        catch (e: Exception) { tools.status.value = context.getString(R.string.data_tools_drive_sign_in_failed, e.message.orEmpty()) }
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.ui_your_data_in_your_hands), style = MaterialTheme.typography.headlineMedium); Text(stringResource(R.string.ui_backups_contain_notes_photos_and_settings_store_them_somewhe)) }
        item { Button({ backup.launch("moodiary-${LocalDate.now()}.zip") }, enabled = !busy) { Text(stringResource(R.string.ui_create_local_backup)) } }
        item { OutlinedButton({ restore.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !busy) { Text(stringResource(R.string.ui_select_backup_to_restore)) } }
        item { Text(stringResource(R.string.ui_archive_limits_2_gb_total_64_mb_structured_data_32_mb_per_ph), style = MaterialTheme.typography.bodySmall) }
        item { Text(stringResource(R.string.ui_automatic_backup), style = MaterialTheme.typography.titleLarge); Text(stringResource(R.string.ui_runs_approximately_daily_when_battery_and_storage_allow_the_)) }
        item { OutlinedButton({ folder.launch(null) }, enabled = !busy) { Text(stringResource(if (preferences.backupTreeUri.isBlank()) R.string.data_tools_choose_folder else R.string.data_tools_change_folder)) } }
        item { Row { Checkbox(preferences.automaticBackup, { value -> tools.run { require(!value || preferences.backupTreeUri.isNotBlank() || preferences.driveBackup) { context.getString(R.string.data_tools_choose_destination_first) }; vm.settings.update { it.copy(automaticBackup = value) }; tools.automatic.schedule(value) } }, enabled = !busy); Text(stringResource(R.string.ui_enable_daily_automatic_backup)) } }
        if (preferences.lastBackupAt > 0) item { Text(stringResource(R.string.data_tools_last_success, Instant.ofEpochMilli(preferences.lastBackupAt).toString())) }
        if (preferences.lastBackupError.isNotEmpty()) item { Text(stringResource(R.string.data_tools_last_error, preferences.lastBackupError), color = MaterialTheme.colorScheme.error) }
        item { Text(stringResource(R.string.ui_optional_google_drive), style = MaterialTheme.typography.titleLarge); Text(stringResource(R.string.ui_cloud_backup_is_off_until_you_connect_an_account_local_featu)) }
        item { OutlinedButton({
            fun failed(failure: Exception) { tools.status.value = context.getString(R.string.data_tools_drive_authorization_failed, failure.message.orEmpty()) }
            try {
                tools.drive.authorize().addOnSuccessListener { result ->
                    try {
                        if (result.hasResolution()) signIn.launch(IntentSenderRequest.Builder(requireNotNull(result.pendingIntent).intentSender).build())
                        else { tools.drive.accept(result); connected = true; tools.status.value = context.getString(R.string.data_tools_drive_connected) }
                    } catch (failure: Exception) { failed(failure) }
                }.addOnFailureListener(::failed)
            } catch (failure: Exception) { failed(failure) }
        }, enabled = !busy) { Text(stringResource(if (connected) R.string.data_tools_reconnect_drive else R.string.data_tools_connect_drive)) } }
        if (connected) {
            item { Button({ tools.run(context.getString(R.string.data_tools_uploaded_drive)) { tools.backups.uploadDrive() } }, enabled = !busy) { Text(stringResource(R.string.ui_back_up_to_drive_now)) } }
            item { Row { Checkbox(preferences.driveBackup, { value -> tools.run { vm.settings.update { it.copy(driveBackup = value) } } }); Text(stringResource(R.string.ui_include_drive_in_automatic_backup)) } }
            item { OutlinedButton({ tools.run(context.getString(R.string.data_tools_drive_backups_loaded)) { tools.cloud.value = tools.backups.driveBackups() } }, enabled = !busy) { Text(stringResource(R.string.ui_list_drive_backups)) } }
            items(cloud, key = { it.id }) { item -> TextButton({ tools.run(context.getString(R.string.data_tools_drive_backup_validated)) { tools.prepared.value = tools.backups.prepareDriveRestore(item.id) } }, enabled = !busy) { Text(stringResource(R.string.data_tools_restore_cloud_item, item.name, item.modifiedAt)) } }
            item { TextButton({ tools.run { vm.settings.update { it.copy(driveBackup = false) }; try { tools.drive.disconnect() } finally { connected = tools.drive.connected() } } }) { Text(stringResource(R.string.ui_disconnect_drive)) } }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (status.isNotEmpty()) item { Text(status) }
    }
    prepared?.let { staged -> RestoreConfirmation(staged, busy, { tools.run(context.getString(R.string.data_tools_restore_completed)) { tools.backups.restore(staged); tools.prepared.value = null; tools.reminders.reconcile(); tools.automatic.schedule(false) } }, { staged.file.delete(); tools.prepared.value = null }) }
}

@Composable fun RestoreConfirmation(staged: PreparedRestore, busy: Boolean, confirm: () -> Unit, cancel: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) cancel() }, title = { Text(stringResource(R.string.ui_replace_this_journal)) }, text = { Text(stringResource(R.string.data_tools_restore_confirmation, staged.createdAt.toString(), staged.entries, staged.photos)) }, confirmButton = { TextButton(enabled = !busy, onClick = confirm) { Text(stringResource(R.string.ui_replace_local_data)) } }, dismissButton = { TextButton(enabled = !busy, onClick = cancel) { Text(stringResource(R.string.ui_cancel)) } })
}

@Composable fun ExportScreen(tools: DataToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var format by rememberSaveable { mutableStateOf("JSON") }
    var range by rememberSaveable { mutableStateOf("All") }
    var from by rememberSaveable { mutableStateOf(LocalDate.now().minusDays(29).toString()) }
    var through by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var photos by rememberSaveable { mutableStateOf(false) }
    val busy by tools.busy.collectAsStateWithLifecycle()
    val status by tools.status.collectAsStateWithLifecycle()
    var pendingFormat by rememberSaveable { mutableStateOf("JSON") }
    var pendingFrom by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingThrough by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) tools.run(context.getString(R.string.data_tools_export_saved)) { tools.exports.export(uri, pendingFormat, SearchFilter(from = pendingFrom?.let(LocalDate::parse), through = pendingThrough?.let(LocalDate::parse)), photos) } }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.ui_export_your_journal), style = MaterialTheme.typography.headlineMedium) }
        item { ChoiceRow(stringResource(R.string.data_tools_format), listOf("JSON", "CSV", "PDF").map { it to it }, format) { format = it } }
        item { Text(stringResource(when (format) { "JSON" -> R.string.data_tools_json_description; "CSV" -> R.string.data_tools_csv_description; else -> R.string.data_tools_pdf_description })) }
        if (format != "JSON") item { ChoiceRow(stringResource(R.string.data_tools_range), listOf("7 days" to stringResource(R.string.data_tools_seven_days), "30 days" to stringResource(R.string.data_tools_thirty_days), "Current month" to stringResource(R.string.data_tools_current_month), "Custom" to stringResource(R.string.data_tools_custom), "All" to stringResource(R.string.data_tools_all)), range) { range = it } }
        if (format != "JSON" && range == "Custom") item { OutlinedTextField(from, { from = it }, label = { Text(stringResource(R.string.ui_from_date)) }); OutlinedTextField(through, { through = it }, label = { Text(stringResource(R.string.ui_through_date)) }) }
        if (format == "PDF") item { Row { Checkbox(photos, { photos = it }); Text(stringResource(R.string.ui_include_photos)) } }
        item { Button(enabled = !busy, onClick = {
            try {
                val today = LocalDate.now()
                val first = when (range) { "7 days" -> today.minusDays(6); "30 days" -> today.minusDays(29); "Current month" -> today.withDayOfMonth(1); "Custom" -> LocalDate.parse(from); else -> null }
                val last = if (range == "Custom") LocalDate.parse(through) else if (range == "All") null else today
                require(first == null || last == null || first <= last) { context.getString(R.string.data_tools_end_date_after_start) }
                pendingFormat = format; pendingFrom = first?.toString(); pendingThrough = last?.toString()
                launcher.launch("moodiary-${LocalDate.now()}.${format.lowercase()}")
            } catch (e: Exception) { tools.status.value = if (e is DateTimeParseException) context.getString(R.string.data_tools_invalid_range) else e.message ?: context.getString(R.string.data_tools_invalid_range) }
        }) { Text(stringResource(R.string.ui_save_export)) } }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (status.isNotEmpty()) item { Text(status) }
    }
}
