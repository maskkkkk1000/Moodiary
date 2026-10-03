package app.moodiary.feature

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.text.NumberFormat
import app.moodiary.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.moodiary.domain.*
import app.moodiary.core.designsystem.IconPicker
import app.moodiary.core.designsystem.JournalIcon
import java.time.LocalDate

@Composable fun MoreScreen(open: (String) -> Unit) {
    val sections = listOf(
        stringResource(R.string.journal_your_journal) to listOf("goals" to stringResource(R.string.journal_goals), "moods" to stringResource(R.string.journal_moods), "activities" to stringResource(R.string.journal_activities), "groups" to stringResource(R.string.journal_groups), "templates" to stringResource(R.string.journal_templates), "important" to stringResource(R.string.journal_important), "achievements" to stringResource(R.string.journal_achievements)),
        stringResource(R.string.journal_your_preferences) to listOf("reminders" to stringResource(R.string.journal_reminders), "appearance" to stringResource(R.string.journal_appearance), "language" to stringResource(R.string.journal_language), "privacy" to stringResource(R.string.journal_privacy)),
        stringResource(R.string.journal_your_data) to listOf("backup" to stringResource(R.string.journal_backup), "export" to stringResource(R.string.journal_export), "audit" to stringResource(R.string.journal_audit), "about" to stringResource(R.string.journal_about))
    )
    LazyColumn(Modifier.testTag("more_list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.ui_make_it_yours), style = MaterialTheme.typography.headlineLarge) }
        sections.forEach { (title, links) ->
            item { Text(title, Modifier.padding(top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            items(links) { (route, label) ->
                Card(onClick = { open(route) }, modifier = Modifier.fillMaxWidth().testTag("more_$route"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        JournalIcon(when (route) { "goals" -> "🌱"; "binary_goals" -> "🎯"; "moods" -> "🌞"; "activities" -> "🏃"; "groups" -> "🗂"; "templates" -> "📝"; "important" -> "📌"; "achievements" -> "🌟"; "reminders" -> "🔔"; "appearance" -> "🎨"; "language" -> "🌐"; "privacy" -> "🔒"; "backup" -> "☁"; "export" -> "📤"; "audit" -> "🛡"; else -> "🌿" }, size = 24.dp)
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ManagementScreen(kind: String, data: JournalData, vm: JournalViewModel) {
    val locale = LocalConfiguration.current.locales[0]
    val numberFormat = remember(locale) { NumberFormat.getNumberInstance(locale).apply { isGroupingUsed = false; maximumFractionDigits = 340 } }
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    var editing by remember { mutableStateOf<Long?>(null) }
    var deleting by remember { mutableStateOf<Long?>(null) }
    val title = when (kind) { "moods" -> stringResource(R.string.journal_moods); "activities" -> stringResource(R.string.journal_activities); "groups" -> stringResource(R.string.journal_groups); "templates" -> stringResource(R.string.journal_templates); else -> stringResource(R.string.journal_important) }
    data class RowItem(val id: Long, val label: String, val detail: String, val archived: Boolean, val order: Int, val icon: String? = null)
    val rows = when (kind) {
        "moods" -> data.moods.map { RowItem(it.id, it.name, stringResource(R.string.journal_score, numberFormat.format(it.score)), it.isArchived, it.sortOrder, it.icon) }
        "activities" -> data.activities.map { RowItem(it.id, it.name, data.groups.find { g -> g.id == it.groupId }?.name ?: stringResource(R.string.journal_other), it.isArchived, it.sortOrder, it.icon) }
        "groups" -> data.groups.map { RowItem(it.id, it.name, "", it.isArchived, it.sortOrder) }
        "templates" -> data.templates.map { RowItem(it.id, it.name, it.content, it.isArchived, it.sortOrder) }
        else -> data.importantDays.map { RowItem(it.id, "${it.icon} ${it.title}", "${LocalDate.parse(it.date).format(dateFormat)} ${it.note}", false, 0) }
    }.sortedWith(compareBy<RowItem> { it.archived }.thenBy { it.order }.thenBy { it.id })
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(title, style = MaterialTheme.typography.headlineMedium); Button({ editing = 0L }) { Text(stringResource(R.string.ui_add)) } }
        if (rows.isEmpty()) item { Text(stringResource(R.string.ui_nothing_here_yet_add_your_first_item)) }
        items(rows, key = { it.id }) { row ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.icon?.let { JournalIcon(it, size = 32.dp) }
                    Text(if (row.archived) stringResource(R.string.journal_archived_name, row.label) else row.label, style = MaterialTheme.typography.titleMedium)
                }
                if (row.detail.isNotBlank()) Text(row.detail, maxLines = 3)
                FlowRow {
                    TextButton({ editing = row.id }, Modifier.testTag("edit_${kind}_${row.id}")) { Text(stringResource(R.string.ui_edit)) }
                    if (kind == "important") TextButton({ deleting = row.id }) { Text(stringResource(R.string.ui_delete)) }
                    else {
                        TextButton({ vm.run {
                            when (kind) {
                                "moods" -> vm.repository.saveMood(data.moods.first { it.id == row.id }.copy(isArchived = !row.archived))
                                "activities" -> vm.repository.saveActivity(data.activities.first { it.id == row.id }.copy(isArchived = !row.archived))
                                "groups" -> vm.repository.saveGroup(data.groups.first { it.id == row.id }.copy(isArchived = !row.archived))
                                "templates" -> vm.repository.saveTemplate(data.templates.first { it.id == row.id }.copy(isArchived = !row.archived))
                            }
                        } }) { Text(stringResource(if (row.archived) R.string.journal_restore else R.string.journal_archive)) }
                        val previous = rows.takeWhile { it.id != row.id }.lastOrNull()
                        TextButton(enabled = previous != null, onClick = { vm.run {
                            if (previous != null) {
                                val newOrder = previous.order
                                val previousOrder = row.order.takeIf { it != previous.order } ?: row.order + 1
                                when (kind) {
                                    "moods" -> { vm.repository.saveMood(data.moods.first { it.id == row.id }.copy(sortOrder = newOrder)); vm.repository.saveMood(data.moods.first { it.id == previous.id }.copy(sortOrder = previousOrder)) }
                                    "activities" -> { vm.repository.saveActivity(data.activities.first { it.id == row.id }.copy(sortOrder = newOrder)); vm.repository.saveActivity(data.activities.first { it.id == previous.id }.copy(sortOrder = previousOrder)) }
                                    "groups" -> { vm.repository.saveGroup(data.groups.first { it.id == row.id }.copy(sortOrder = newOrder)); vm.repository.saveGroup(data.groups.first { it.id == previous.id }.copy(sortOrder = previousOrder)) }
                                    "templates" -> { vm.repository.saveTemplate(data.templates.first { it.id == row.id }.copy(sortOrder = newOrder)); vm.repository.saveTemplate(data.templates.first { it.id == previous.id }.copy(sortOrder = previousOrder)) }
                                }
                            }
                        } }) { Text(stringResource(R.string.ui_move_up)) }
                    }
                }
            } }
        }
    }
    editing?.let { id -> ItemEditor(kind, id, data, vm) { editing = null } }
    deleting?.let { id -> AlertDialog({ deleting = null }, title = { Text(stringResource(R.string.ui_delete_important_day)) }, text = { Text(stringResource(R.string.ui_journal_entries_on_this_date_will_be_kept)) }, confirmButton = { TextButton({ vm.run { vm.repository.deleteImportantDay(id) }; deleting = null }) { Text(stringResource(R.string.ui_delete)) } }, dismissButton = { TextButton({ deleting = null }) { Text(stringResource(R.string.ui_cancel)) } }) }
}

@Composable private fun ItemEditor(kind: String, id: Long, data: JournalData, vm: JournalViewModel, close: () -> Unit) {
    val context = LocalContext.current
    val mood = data.moods.find { it.id == id }
    val activity = data.activities.find { it.id == id }
    val group = data.groups.find { it.id == id }
    val template = data.templates.find { it.id == id }
    val day = data.importantDays.find { it.id == id }
    var name by rememberSaveable { mutableStateOf(when (kind) { "moods" -> mood?.name; "activities" -> activity?.name; "groups" -> group?.name; "templates" -> template?.name; else -> day?.title } ?: "") }
    var icon by rememberSaveable { mutableStateOf(when (kind) { "moods" -> mood?.icon; "activities" -> activity?.icon; else -> day?.icon } ?: "●") }
    var score by rememberSaveable { mutableStateOf(mood?.score?.toString() ?: "3.0") }
    var color by rememberSaveable { mutableStateOf((if (kind == "moods") mood?.color else activity?.color)?.toString(16) ?: "ff326a5f") }
    var content by rememberSaveable { mutableStateOf((if (kind == "templates") template?.content else day?.note) ?: "") }
    var date by rememberSaveable { mutableStateOf(day?.date ?: LocalDate.now().toString()) }
    var groupId by rememberSaveable { mutableStateOf(activity?.groupId) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(if (id == 0L) R.string.journal_create_item else R.string.journal_edit_item)) }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.ui_name)) }, singleLine = true) }
            if (kind in listOf("moods", "activities", "important")) item { IconPicker(icon, { icon = it }, allowMaterial = kind == "activities") }
            if (kind == "moods") item { OutlinedTextField(score, { score = it }, label = { Text(stringResource(R.string.ui_mood_score_0_10)) }, singleLine = true); Text(stringResource(R.string.ui_changes_apply_to_new_entries_existing_mood_snapshots_are_pre)) }
            if (kind == "moods" || kind == "activities") item {
                ColorPalette(color) { color = it }
                OutlinedTextField(color, { color = it }, label = { Text(stringResource(R.string.ui_color_aarrggbb_hex)) }, singleLine = true)
            }
            if (kind == "activities") item { ChoiceRow(stringResource(R.string.journal_group), listOf(null to stringResource(R.string.journal_other)) + data.groups.filter { !it.isArchived }.map { it.id to it.name }, groupId) { groupId = it } }
            if (kind == "templates" || kind == "important") item { OutlinedTextField(content, { content = it }, label = { Text(stringResource(if (kind == "templates") R.string.journal_template_text else R.string.journal_note)) }, minLines = 3) }
            if (kind == "important") item { OutlinedTextField(date, { date = it }, label = { Text(stringResource(R.string.ui_date_yyyy_mm_dd)) }) }
            if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton({
        try {
            require(name.isNotBlank()) { context.getString(R.string.journal_enter_name) }
            if (kind in listOf("moods", "activities", "important")) require(icon.isNotBlank()) { context.getString(R.string.iter_choose_icon) }
            val parsedColor = if (kind == "moods" || kind == "activities") color.removePrefix("#").toLong(16).also { require(it in 0..0xFFFFFFFF) { context.getString(R.string.journal_valid_color) } } else 0L
            val parsedScore = if (kind == "moods") score.toDouble().also { require(it.isFinite() && it in -1000.0..1000.0) { context.getString(R.string.journal_score_bounds) } } else 0.0
            if (kind == "important") LocalDate.parse(date)
            vm.run {
                when (kind) {
                    "moods" -> vm.repository.saveMood((mood ?: Mood(name = name, score = parsedScore, sortOrder = data.moods.size)).copy(name = name.trim(), score = parsedScore, icon = icon, color = parsedColor))
                    "activities" -> vm.repository.saveActivity((activity ?: Activity(name = name, sortOrder = data.activities.size)).copy(name = name.trim(), icon = icon, color = parsedColor, groupId = groupId))
                    "groups" -> vm.repository.saveGroup((group ?: ActivityGroup(name = name, sortOrder = data.groups.size)).copy(name = name.trim()))
                    "templates" -> vm.repository.saveTemplate((template ?: NoteTemplate(name = name, content = content, sortOrder = data.templates.size)).copy(name = name.trim(), content = content))
                    else -> vm.repository.saveImportantDay((day ?: ImportantDay(date = date, title = name)).copy(date = date, title = name.trim(), icon = icon, note = content))
                }
                close()
            }
        } catch (failure: Exception) { error = if (failure is java.time.format.DateTimeParseException || failure is NumberFormatException) context.getString(R.string.journal_check_input) else failure.message ?: context.getString(R.string.journal_check_input) }
    }) { Text(stringResource(R.string.ui_save)) } }, dismissButton = { TextButton(close) { Text(stringResource(R.string.ui_cancel)) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ColorPalette(selected: String, choose: (String) -> Unit) {
    Text(stringResource(R.string.iter_color_presets), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("ff326a5f", "ff275f8a", "ff755078", "ff974858", "ff875316", "ff687342").forEach { hex ->
            val color = Color(hex.toLong(16))
            val description = stringResource(R.string.iter_color_option, "#${hex.drop(2)}")
            FilledTonalIconToggleButton(selected.removePrefix("#").equals(hex, ignoreCase = true), { choose(hex) }, Modifier.size(48.dp)) {
                Surface(Modifier.size(30.dp), shape = CircleShape, color = color, contentColor = if (color.luminance() > 0.5f) Color.Black else Color.White) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (selected.removePrefix("#").equals(hex, ignoreCase = true)) "✓" else "", Modifier.semantics { contentDescription = description })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun GoalEditor(goal: Goal?, data: JournalData, vm: JournalViewModel, close: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var name by rememberSaveable { mutableStateOf(goal?.name ?: "") }
    var type by remember { mutableStateOf(goal?.goalType ?: GoalType.DAILY) }
    var linked by rememberSaveable { mutableStateOf(goal?.linkedActivityId) }
    var target by rememberSaveable { mutableStateOf(goal?.targetCount?.toString() ?: "3") }
    var days by remember { mutableStateOf(data.schedules.filter { it.goalId == goal?.id }.map { it.dayOfWeek }.toSet().ifEmpty { setOf(1, 3, 5) }) }
    var start by rememberSaveable { mutableStateOf(goal?.startDate ?: LocalDate.now().toString()) }
    var end by rememberSaveable { mutableStateOf(goal?.endDate ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(close, title = { Text(stringResource(if (goal == null) R.string.journal_new_goal else R.string.journal_edit_goal)) }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.ui_goal_name)) }) }
            item { ChoiceRow(stringResource(R.string.journal_schedule), GoalType.entries.map { it to stringResource(when (it) { GoalType.DAILY -> R.string.journal_schedule_daily; GoalType.WEEKDAYS -> R.string.journal_schedule_weekdays; GoalType.WEEKLY_COUNT -> R.string.journal_schedule_weekly_count }) }, type) { type = it } }
            item { ChoiceRow(stringResource(R.string.journal_linked_activity), listOf(null to stringResource(R.string.journal_manual_only)) + data.activities.filter { !it.isArchived || it.id == linked }.map { it.id to it.name }, linked) { linked = it } }
            if (type == GoalType.WEEKDAYS) item { FlowRow { (1..7).forEach { d -> FilterChip(d in days, { days = if (d in days) days - d else days + d }, { Text(java.time.DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, locale)) }) } } }
            if (type == GoalType.WEEKLY_COUNT) item { OutlinedTextField(target, { target = it }, label = { Text(stringResource(R.string.ui_days_per_week_1_7)) }); Text(stringResource(R.string.ui_at_most_one_completion_per_local_date)) }
            item { OutlinedTextField(start, { start = it }, label = { Text(stringResource(R.string.ui_start_date)) }) }
            item { OutlinedTextField(end, { end = it }, label = { Text(stringResource(R.string.ui_end_date_optional)) }) }
            if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton({
        try {
            require(name.isNotBlank()) { context.getString(R.string.journal_enter_name) }; val from = LocalDate.parse(start); val through = end.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
            require(through == null || through >= from) { context.getString(R.string.journal_end_before_start) }
            require(type != GoalType.WEEKDAYS || days.isNotEmpty()) { context.getString(R.string.journal_select_weekday) }
            val count = if (type == GoalType.WEEKLY_COUNT) target.toInt().also { require(it in 1..7) { context.getString(R.string.journal_weekly_count_bounds) } } else 1
            vm.run { vm.repository.saveGoal((goal ?: Goal(name = name)).copy(name = name.trim(), linkedActivityId = linked, goalType = type, targetCount = count, startDate = start, endDate = end.takeIf { it.isNotBlank() }), if (type == GoalType.WEEKDAYS) days else emptySet()); close() }
        } catch (failure: Exception) { error = if (failure is java.time.format.DateTimeParseException || failure is NumberFormatException) context.getString(R.string.journal_check_input) else failure.message ?: context.getString(R.string.journal_check_input) }
    }) { Text(stringResource(R.string.ui_save_goal)) } }, dismissButton = { TextButton(close) { Text(stringResource(R.string.ui_cancel)) } })
}
