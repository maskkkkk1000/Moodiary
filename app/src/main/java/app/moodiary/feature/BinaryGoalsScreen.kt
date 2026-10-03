package app.moodiary.feature

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.moodiary.R
import app.moodiary.core.designsystem.IconPicker
import app.moodiary.core.designsystem.JournalIcon
import app.moodiary.domain.BinaryGoal
import app.moodiary.domain.BinaryGoalRecord
import app.moodiary.domain.JournalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Binary results are independent of frequency goals: a missing record is never a failure. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun BinaryGoalsScreen(data: JournalData, vm: JournalViewModel, onStatistics: () -> Unit = {}) {
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val dateValid = date != null && date.year in 1..9999 && date <= LocalDate.now()
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val goals = remember(data.binaryGoals, showArchived) {
        data.binaryGoals.filter { it.isArchived == showArchived }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    }
    val records by produceState<Map<Long, BinaryGoalRecord>?>(null, data.binaryGoalRecords, dateText) {
        value = null
        value = withContext(Dispatchers.Default) { data.binaryGoalRecords.filter { it.date == dateText }.associateBy { it.goalId } }
    }
    val referencedGoals by produceState<Set<Long>?>(null, data.binaryGoalRecords) {
        value = null
        value = withContext(Dispatchers.Default) { data.binaryGoalRecords.mapTo(mutableSetOf()) { it.goalId } }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("binary_list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(stringResource(R.string.binary_title), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.binary_intro), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button({ editId = 0 }, enabled = !busy, modifier = Modifier.testTag("binary_create")) { Text(stringResource(R.string.binary_create)) }
                TextButton(onStatistics) { Text(stringResource(R.string.binary_statistics)) }
            }
        }
        item {
            OutlinedTextField(dateText, { dateText = it }, Modifier.fillMaxWidth().testTag("binary_date"), label = { Text(stringResource(R.string.binary_date)) }, singleLine = true, isError = !dateValid,
                supportingText = { Text(stringResource(if (dateValid) R.string.binary_tap_hint else R.string.binary_invalid_date)) })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ dateText = LocalDate.now().toString() }) { Text(stringResource(R.string.binary_today)) }
                FilterChip(!showArchived, { showArchived = false }, { Text(stringResource(R.string.binary_active)) })
                FilterChip(showArchived, { showArchived = true }, { Text(stringResource(R.string.binary_archived)) })
            }
        }
        if (goals.isEmpty()) item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (showArchived) stringResource(R.string.binary_empty_archive) else stringResource(R.string.binary_empty), style = MaterialTheme.typography.titleMedium)
                if (!showArchived) Text(stringResource(R.string.binary_empty_hint))
            } }
        }
        itemsIndexed(goals, key = { _, goal -> goal.id }) { index, goal ->
            val record = records?.get(goal.id)
            val status = stringResource(when (record?.value) { 1 -> R.string.binary_success; 0 -> R.string.binary_failed; else -> R.string.binary_unset })
            Card(Modifier.fillMaxWidth().animateContentSize().testTag("binary_goal_${goal.id}")) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        JournalIcon(goal.icon, size = 36.dp)
                        Column(Modifier.weight(1f)) { Text(goal.name, style = MaterialTheme.typography.titleLarge); if (goal.description.isNotBlank()) Text(goal.description, style = MaterialTheme.typography.bodyMedium) }
                    }
                    Text(status, Modifier.testTag("binary_status_${goal.id}"), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(1 to R.string.binary_success, 0 to R.string.binary_failed).forEach { (value, label) ->
                            val description = "${goal.name}: ${stringResource(label)}"
                            FilterChip(selected = record?.value == value, onClick = { vm.run { vm.repository.setBinaryGoalRecord(goal.id, dateText, if (record?.value == value) null else value) } },
                                label = { Text((if (value == 1) "✓ " else "× ") + stringResource(label)) },
                                enabled = (dateValid || (date != null && date.year in 1..9999 && record != null)) && !goal.isArchived && !busy && records != null,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("binary_${goal.id}_$value").semantics { contentDescription = description })
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton({ editId = goal.id }, enabled = !busy, modifier = Modifier.testTag("binary_edit_${goal.id}")) { Text(stringResource(R.string.ui_edit)) }
                        TextButton({ vm.run { vm.repository.saveBinaryGoal(goal.copy(isArchived = !goal.isArchived)) } }, enabled = !busy, modifier = Modifier.testTag("binary_archive_${goal.id}")) { Text(stringResource(if (goal.isArchived) R.string.binary_restore else R.string.binary_archive)) }
                        TextButton({ vm.run { vm.repository.moveBinaryGoal(goal.id, -1) } }, enabled = index > 0 && !busy) { Text(stringResource(R.string.binary_move_up)) }
                        TextButton({ vm.run { vm.repository.moveBinaryGoal(goal.id, 1) } }, enabled = index < goals.lastIndex && !busy) { Text(stringResource(R.string.binary_move_down)) }
                        if (referencedGoals?.let { goal.id !in it } == true) TextButton({ deletingId = goal.id }, enabled = !busy) { Text(stringResource(R.string.binary_delete)) }
                    }
                }
            }
        }
    }
    editId?.let { id ->
        val goal = data.binaryGoals.find { it.id == id }
        BinaryGoalEditor(goal, data.binaryGoals.size, vm) { editId = null }
    }
    deletingId?.let { id ->
        AlertDialog(onDismissRequest = { deletingId = null }, title = { Text(stringResource(R.string.binary_delete_title)) }, text = { Text(stringResource(R.string.binary_delete_hint)) },
            confirmButton = { TextButton({ vm.run { vm.repository.deleteBinaryGoalIfUnused(id); deletingId = null } }, enabled = !busy) { Text(stringResource(R.string.binary_delete)) } },
            dismissButton = { TextButton({ deletingId = null }) { Text(stringResource(R.string.ui_cancel)) } })
    }
}

@Composable private fun BinaryGoalEditor(goal: BinaryGoal?, defaultOrder: Int, vm: JournalViewModel, close: () -> Unit) {
    var name by rememberSaveable(goal?.id) { mutableStateOf(goal?.name.orEmpty()) }
    var icon by rememberSaveable(goal?.id) { mutableStateOf(goal?.icon ?: "🎯") }
    var description by rememberSaveable(goal?.id) { mutableStateOf(goal?.description.orEmpty()) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    androidx.compose.ui.window.Dialog(onDismissRequest = { if (!busy) close() }) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.testTag("binary_editor")) {
                item { Text(stringResource(if (goal == null) R.string.binary_create else R.string.binary_edit), style = MaterialTheme.typography.titleLarge) }
                item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().testTag("binary_name"), label = { Text(stringResource(R.string.binary_name)) }, singleLine = true, enabled = !busy) }
                item { IconPicker(icon, { icon = it }, allowMaterial = true) }
                item { OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.binary_description)) }, enabled = !busy, maxLines = 5) }
                item { Button({ vm.run {
                    vm.repository.saveBinaryGoal((goal ?: BinaryGoal(name = name, sortOrder = defaultOrder)).copy(name = name.trim(), icon = icon.trim(), description = description.trim()))
                    close()
                } }, enabled = !busy && name.isNotBlank() && name.length <= 120 && icon.isNotBlank() && icon.length <= 64 && description.length <= 2_000,
                    modifier = Modifier.testTag("binary_save")) { Text(stringResource(R.string.binary_save)) } }
                item { TextButton(close, enabled = !busy) { Text(stringResource(R.string.ui_cancel)) } }
            }
        }
    }
}
