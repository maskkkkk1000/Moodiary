package app.moodiary.feature

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import app.moodiary.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.moodiary.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

private fun Double?.formatted(locale: Locale) = this?.let { "%.2f".format(locale, it) } ?: "—"

@OptIn(ExperimentalLayoutApi::class)
@Composable fun GoalsScreen(data: JournalData, vm: JournalViewModel, targetId: Long = 0L) {
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    var edit by remember { mutableStateOf<Goal?>(null) }
    var adding by remember { mutableStateOf(false) }
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val today = LocalDate.now()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val goalMetrics by produceState<Map<Long, GoalMetrics>?>(null, data.goals, data.schedules, data.completions, today) {
        value = null
        value = withContext(Dispatchers.Default) {
            val schedules = data.schedules.groupBy { it.goalId }
            val completions = data.completions.groupBy { it.goalId }
            data.goals.associate { goal -> goal.id to GoalEngine.metrics(goal, schedules[goal.id].orEmpty(), completions[goal.id].orEmpty(), today) }
        }
    }
    LaunchedEffect(targetId, goalMetrics != null) {
        if (targetId > 0 && goalMetrics != null) {
            val position = data.goals.indexOfFirst { it.id == targetId }
            if (position >= 0) listState.scrollToItem(position + 2)
        }
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.ui_small_steps), style = MaterialTheme.typography.headlineLarge); Button({ adding = true }) { Text(stringResource(R.string.ui_new_goal)) } }
        item { OutlinedTextField(dateText, { dateText = it }, label = { Text(stringResource(R.string.ui_completion_date_yyyy_mm_dd)) }, isError = date == null); Text(stringResource(R.string.ui_daily_streaks_count_scheduled_days_weekly_count_streaks_coun), style = MaterialTheme.typography.bodySmall) }
        if (data.goals.isEmpty()) item { Text(stringResource(R.string.ui_choose_something_you_would_like_to_make_time_for)) }
        else if (goalMetrics == null) item { CircularProgressIndicator() }
        items(data.goals, key = { it.id }) { goal ->
            val metrics = goalMetrics?.get(goal.id)
            val completed = data.completions.find { it.goalId == goal.id && it.date == dateText }
            Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (goal.isArchived) stringResource(R.string.journal_archived_name, goal.name) else goal.name, style = MaterialTheme.typography.titleLarge)
                if (metrics != null) {
                    Text(stringResource(R.string.journal_goal_streak, metrics.currentStreak, metrics.longestStreak, metrics.completionRate.formatted(locale)))
                    Text(stringResource(R.string.journal_scheduled_completions, metrics.completed, metrics.expected))
                    Text(metrics.recentWeeks.takeLast(4).joinToString("\n") { context.getString(R.string.journal_week_progress, it.weekStart.format(dateFormat), it.completed, it.target) })
                }
                if (goal.linkedActivityId != null) Text(stringResource(R.string.journal_linked_goal, data.activities.find { it.id == goal.linkedActivityId }?.name.orEmpty()), style = MaterialTheme.typography.bodySmall)
                val eligible = date != null && date <= today && GoalEngine.isScheduled(goal, data.schedules, date)
                Button(enabled = eligible && !goal.isArchived && completed?.source != CompletionSource.LINKED_ACTIVITY, onClick = { vm.run { vm.repository.setManualCompletion(goal.id, dateText, completed == null) } }) { Text(stringResource(if (completed?.source == CompletionSource.LINKED_ACTIVITY) R.string.journal_completed_activity else if (completed != null) R.string.journal_undo_completion else R.string.journal_mark_complete)) }
                FlowRow { TextButton({ edit = goal }) { Text(stringResource(R.string.ui_edit)) }; TextButton({ vm.run { vm.repository.saveGoal(goal.copy(isArchived = !goal.isArchived), data.schedules.filter { it.goalId == goal.id }.map { it.dayOfWeek }.toSet()) } }) { Text(stringResource(if (goal.isArchived) R.string.journal_restore else R.string.journal_archive)) } }
            } }
        }
    }
    if (adding || edit != null) GoalEditor(edit, data, vm) { adding = false; edit = null }
}

@Composable private fun achievementTitle(item: Achievement): String {
    val resource = when (item.id) {
    "first" -> R.string.journal_achievement_first
    "days7" -> R.string.journal_achievement_days7
    "days30" -> R.string.journal_achievement_days30
    "entries100" -> R.string.journal_achievement_entries100
    "entries365" -> R.string.journal_achievement_entries365
    "streak7" -> R.string.journal_achievement_streak7
    "streak30" -> R.string.journal_achievement_streak30
    "streak100" -> R.string.journal_achievement_streak100
        else -> return item.title
    }
    return stringResource(resource)
}

@Composable fun AchievementsScreen(data: JournalData) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val achievements by produceState<List<Achievement>>(emptyList(), data.entries, zone, today) {
        value = withContext(Dispatchers.Default) { JournalAchievements.evaluate(data.entries, zone, today) }
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.ui_your_journal_grows), style = MaterialTheme.typography.headlineMedium); Text(stringResource(R.string.ui_a_quiet_acknowledgement_of_the_time_you_give_yourself)) }
        items(achievements) { item -> Card { Column(Modifier.fillMaxWidth().padding(16.dp)) { Text((if (item.unlocked) "★ " else "☆ ") + achievementTitle(item), style = MaterialTheme.typography.titleMedium); Text("${item.progress} / ${item.target}") } } }
    }
}
