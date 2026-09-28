package app.moodiary.feature

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import java.time.format.TextStyle
import app.moodiary.R

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.moodiary.domain.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ReminderScreen(data: JournalData, vm: JournalViewModel, tools: DataToolsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Reminder?>(null) }
    var permission by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> permission = if (allowed) context.getString(R.string.journal_notifications_enabled) else context.getString(R.string.journal_notifications_blocked) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.ui_a_gentle_nudge), style = MaterialTheme.typography.headlineLarge); Text(stringResource(R.string.ui_reminders_follow_your_local_timezone_android_may_delay_them_)) }
        item { FlowRow { Button({ adding = true }) { Text(stringResource(R.string.ui_add_reminder)) }; if (Build.VERSION.SDK_INT >= 33) TextButton({ launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.ui_allow_notifications)) } } }
        if (permission.isNotEmpty()) item { Text(permission) }
        if (data.reminders.isEmpty()) item { Text(stringResource(R.string.ui_no_reminders_scheduled)) }
        items(data.reminders, key = { it.id }) { reminder -> Card { Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("%02d:%02d".format(locale, reminder.hour, reminder.minute), style = MaterialTheme.typography.titleLarge)
            Text(reminder.message)
            Text(reminder.daysOfWeek.sorted().joinToString { java.time.DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale) })
            if (reminder.targetId != null) Text(stringResource(R.string.journal_reminder_goal, data.goals.find { it.id == reminder.targetId }?.name.orEmpty()))
            Row { Checkbox(reminder.enabled, { value -> vm.run { vm.repository.saveReminder(reminder.copy(enabled = value)); tools.reminders.reconcile() } }); Text(stringResource(R.string.ui_enabled)) }
            FlowRow { TextButton({ editing = reminder }) { Text(stringResource(R.string.ui_edit)) }; TextButton({ deleting = reminder }) { Text(stringResource(R.string.ui_delete)) } }
        } } }
    }
    if (adding || editing != null) {
        val original = editing
        var message by remember { mutableStateOf(original?.message ?: context.getString(R.string.journal_default_reminder)) }
        var hour by remember { mutableStateOf(original?.hour?.toString() ?: "20") }
        var minute by remember { mutableStateOf(original?.minute?.toString() ?: "0") }
        var days by remember { mutableStateOf(original?.daysOfWeek ?: (1..7).toSet()) }
        var goalId by remember { mutableStateOf(original?.targetId) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog({ adding = false; editing = null }, title = { Text(stringResource(R.string.ui_reminder)) }, text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(message, { message = it }, label = { Text(stringResource(R.string.ui_message)) }) }
                item { Row { OutlinedTextField(hour, { hour = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.ui_hour)) }); OutlinedTextField(minute, { minute = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.ui_minute)) }) } }
                item { FlowRow { (1..7).forEach { day -> FilterChip(day in days, { days = if (day in days) days - day else days + day }, { Text(java.time.DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale)) }) } } }
                item { ChoiceRow(stringResource(R.string.journal_reminder_for), listOf(null to stringResource(R.string.journal_diary)) + data.goals.filter { !it.isArchived }.map { it.id to it.name }, goalId) { goalId = it } }
                if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton({
            try {
                val h = hour.toInt(); val m = minute.toInt(); require(h in 0..23 && m in 0..59) { context.getString(R.string.journal_valid_time) }; require(days.isNotEmpty()) { context.getString(R.string.journal_select_a_weekday) }; require(message.isNotBlank()) { context.getString(R.string.journal_enter_message) }
                vm.run { vm.repository.saveReminder((original ?: Reminder()).copy(type = if (goalId == null) ReminderType.DIARY else ReminderType.GOAL, targetId = goalId, hour = h, minute = m, daysOfWeek = days, message = message)); tools.reminders.reconcile(); adding = false; editing = null }
            } catch (e: Exception) { error = if (e is NumberFormatException) context.getString(R.string.journal_valid_time) else e.message }
        }) { Text(stringResource(R.string.ui_save_reminder)) } }, dismissButton = { TextButton({ adding = false; editing = null }) { Text(stringResource(R.string.ui_cancel)) } })
    }
    deleting?.let { item -> AlertDialog({ deleting = null }, title = { Text(stringResource(R.string.ui_delete_reminder)) }, confirmButton = { TextButton({ vm.run { vm.repository.deleteReminder(item.id); tools.reminders.reconcile() }; deleting = null }) { Text(stringResource(R.string.ui_delete)) } }, dismissButton = { TextButton({ deleting = null }) { Text(stringResource(R.string.ui_cancel)) } }) }
}
