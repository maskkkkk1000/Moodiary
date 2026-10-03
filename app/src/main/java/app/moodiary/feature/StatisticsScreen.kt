package app.moodiary.feature

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.moodiary.R
import app.moodiary.domain.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private fun Double?.number(locale: Locale) = this?.let { "%.2f".format(locale, it) } ?: "—"
private fun <T> Set<T>.toggled(value: T) = if (value in this) this - value else this + value
@Composable private fun statisticsConfidence(value: Confidence) = stringResource(when(value) {
    Confidence.INSUFFICIENT -> R.string.journal_confidence_insufficient
    Confidence.LOW -> R.string.journal_confidence_low
    Confidence.MEDIUM -> R.string.journal_confidence_medium
    Confidence.HIGH -> R.string.journal_confidence_high
})
@Composable private fun rangeName(value: StatisticsRange) = stringResource(when(value) {
    StatisticsRange.THIS_WEEK -> R.string.stats_this_week; StatisticsRange.THIS_MONTH -> R.string.stats_this_month
    StatisticsRange.DAYS_7 -> R.string.journal_range_week; StatisticsRange.DAYS_30 -> R.string.journal_range_month
    StatisticsRange.MONTHS_3 -> R.string.journal_range_quarter; StatisticsRange.YEAR_1 -> R.string.journal_range_year
    StatisticsRange.ALL -> R.string.journal_range_all; StatisticsRange.CUSTOM -> R.string.stats_custom
})
@Composable private fun moduleName(value: StatisticsModule) = stringResource(when(value) {
    StatisticsModule.MOOD_TREND -> R.string.ui_daily_mood_trend
    StatisticsModule.MOOD_DISTRIBUTION -> R.string.ui_mood_distribution
    StatisticsModule.ACTIVITY_FREQUENCY -> R.string.ui_activity_frequency
    StatisticsModule.SAME_ENTRY_ASSOCIATION -> R.string.ui_same_entry_associations
    StatisticsModule.NEXT_DAY_ASSOCIATION -> R.string.ui_next_day_associations
    StatisticsModule.WEEKDAY_PATTERN -> R.string.ui_weekday_mood_averages
    StatisticsModule.MOOD_ACTIVITIES -> R.string.ui_activities_alongside_each_mood
    StatisticsModule.ACTIVITY_COMBINATIONS -> R.string.ui_activity_combinations
    StatisticsModule.GOAL_COMPLETION -> R.string.stats_habit_goals
    StatisticsModule.BINARY_GOAL_COMPLETION -> R.string.stats_binary_goals
})

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable fun StatisticsScreen(vm: StatisticsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val writeError by vm.writeError.collectAsStateWithLifecycle()
    val filters = state.filters
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(Modifier.fillMaxSize().testTag("statistics_list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.ui_patterns_with_perspective), style = MaterialTheme.typography.headlineMedium) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip({ sheet = "time" }, { Text(rangeName(filters.range)) }, Modifier.testTag("stats_filter_time"))
                AssistChip({ sheet = "mood" }, { Text(stringResource(R.string.stats_moods, filters.moodIds.size)) }, Modifier.testTag("stats_filter_mood"))
                AssistChip({ sheet = "activity" }, { Text(stringResource(R.string.stats_activities, filters.activityIds.size)) }, Modifier.testTag("stats_filter_activity"))
                AssistChip({ sheet = "modules" }, { Text(stringResource(R.string.stats_modules, filters.modules.size)) }, Modifier.testTag("stats_filter_modules"))
                AssistChip({ sheet = "binary" }, { Text(stringResource(R.string.stats_goals, filters.binaryGoalIds.size)) }, Modifier.testTag("stats_filter_binary"))
            }
            Text(stringResource(R.string.stats_filter_hint), style = MaterialTheme.typography.bodySmall)
            TextButton(vm::reset, Modifier.testTag("stats_reset")) { Text(stringResource(R.string.stats_reset)) }
        }
        if (writeError) item { Text(stringResource(R.string.stats_write_error), color = MaterialTheme.colorScheme.error) }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().testTag("stats_loading")) }
        if (state.error) item { Text(stringResource(R.string.stats_error), color = MaterialTheme.colorScheme.error); Button(vm::retry) { Text(stringResource(R.string.ui_retry_opening_journal)) } }
        state.result?.let { result ->
            item { Text("${result.dates.from ?: "…"} — ${result.dates.through}", style = MaterialTheme.typography.labelLarge) }
            result.entries?.let { entries -> item {
                Card(Modifier.fillMaxWidth().testTag("stats_entry_summary")) { Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.stats_entry_count, entries.entryCount), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.journal_average_mood, entries.average.number(locale)))
                    if (entries.entryCount == 0) Text(stringResource(R.string.ui_record_a_few_moments_to_start_exploring_your_patterns))
                } }
            } }
            if (filters.modules.isEmpty()) item { Text(stringResource(R.string.stats_no_modules)) }
            items(StatisticsModule.entries.filter { it in filters.modules }, key = { it.name }) { module ->
                var expanded by rememberSaveable(module) { mutableStateOf(true) }
                var limit by rememberSaveable(module) { mutableIntStateOf(20) }
                Card(Modifier.fillMaxWidth().animateContentSize().testTag("stats_module_${module.name}")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton({ expanded = !expanded }, Modifier.fillMaxWidth()) { Text((if (expanded) "▾ " else "▸ ") + moduleName(module), style = MaterialTheme.typography.titleMedium) }
                        if (expanded) {
                            StatisticsModuleBody(module, result, state.data, locale, limit)
                            val count = when(module) {
                                StatisticsModule.MOOD_DISTRIBUTION -> result.entries?.moodDistribution?.size ?: 0
                                StatisticsModule.ACTIVITY_FREQUENCY -> result.entries?.activityFrequency?.size ?: 0
                                StatisticsModule.SAME_ENTRY_ASSOCIATION -> result.entries?.sameEntryAssociations?.size ?: 0
                                StatisticsModule.NEXT_DAY_ASSOCIATION -> result.entries?.nextDayAssociations?.size ?: 0
                                StatisticsModule.MOOD_ACTIVITIES -> result.entries?.moodActivities?.size ?: 0
                                StatisticsModule.GOAL_COMPLETION -> result.goals.size
                                StatisticsModule.BINARY_GOAL_COMPLETION -> result.binaryGoals?.byGoal?.size ?: 0
                                else -> 0
                            }
                            if (count > limit) TextButton({ limit += 20 }) { Text(stringResource(R.string.stats_show_more, limit, count)) }
                        }
                    }
                }
            }
        }
    }
    sheet?.let { section ->
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(stringResource(R.string.stats_filter_title), style = MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.heightIn(max = 430.dp).testTag("stats_filter_list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (section == "time") {
                        items(StatisticsRange.entries.filter { it != StatisticsRange.CUSTOM }) { range ->
                            FilterChip(filters.range == range, { vm.update { it.copy(range = range) } }, { Text(rangeName(range)) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("stats_range_${range.name}"))
                        }
                        item { CustomStatisticsRange(filters) { start, end -> vm.update { it.copy(range = StatisticsRange.CUSTOM, customStart = start, customEnd = end) } } }
                    } else if (section == "modules") {
                        items(StatisticsModule.entries) { module ->
                            FilterChip(module in filters.modules, { vm.update { it.copy(modules = it.modules.toggled(module)) } }, { Text(moduleName(module)) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("stats_select_${module.name}"))
                        }
                    } else {
                        val options = when(section) {
                            "mood" -> state.data.moods.map { it.id to it.name }
                            "activity" -> state.data.activities.map { it.id to it.name }
                            else -> state.data.binaryGoals.map { it.id to it.name }
                        }
                        val selected = when(section) { "mood" -> filters.moodIds; "activity" -> filters.activityIds; else -> filters.binaryGoalIds }
                        item { FilterChip(selected.isEmpty(), { vm.update { when(section) { "mood" -> it.copy(moodIds = emptySet()); "activity" -> it.copy(activityIds = emptySet()); else -> it.copy(binaryGoalIds = emptySet()) } } }, { Text(stringResource(R.string.stats_all)) }, Modifier.heightIn(min = 48.dp)) }
                        if (options.isEmpty()) item { Text(stringResource(R.string.stats_no_options)) }
                        items(options, key = { it.first }) { (id, name) ->
                            FilterChip(id in selected, { vm.update { when(section) { "mood" -> it.copy(moodIds = it.moodIds.toggled(id)); "activity" -> it.copy(activityIds = it.activityIds.toggled(id)); else -> it.copy(binaryGoalIds = it.binaryGoalIds.toggled(id)) } } },
                                { Text(name) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("stats_${section}_$id"))
                        }
                    }
                }
                TextButton({ sheet = null }, Modifier.fillMaxWidth().testTag("stats_close_filters")) { Text(stringResource(R.string.stats_done)) }
            }
        }
    }
}

@Composable private fun CustomStatisticsRange(filters: StatisticsFilters, apply: (String, String) -> Unit) {
    var start by rememberSaveable { mutableStateOf(filters.customStart.ifEmpty { LocalDate.now().minusDays(29).toString() }) }
    var end by rememberSaveable { mutableStateOf(filters.customEnd.ifEmpty { LocalDate.now().toString() }) }
    val valid = runCatching { val a = LocalDate.parse(start); val b = LocalDate.parse(end); a <= b && a.year in 1..9999 && b.year in 1..9999 }.getOrDefault(false)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.stats_custom))
        OutlinedTextField(start, { start = it }, label = { Text(stringResource(R.string.stats_start)) }, isError = !valid, modifier = Modifier.testTag("stats_custom_start"))
        OutlinedTextField(end, { end = it }, label = { Text(stringResource(R.string.stats_end)) }, isError = !valid, modifier = Modifier.testTag("stats_custom_end"))
        if (!valid) Text(stringResource(R.string.stats_invalid_range), color = MaterialTheme.colorScheme.error)
        Button({ apply(start, end) }, enabled = valid, modifier = Modifier.testTag("stats_custom_apply")) { Text(stringResource(R.string.stats_apply)) }
    }
}

@Composable private fun StatisticsModuleBody(module: StatisticsModule, result: SelectedStatistics, data: JournalData, locale: Locale, limit: Int) {
    val stats = result.entries
    when(module) {
        StatisticsModule.MOOD_TREND -> { TrendChart(stats?.trend.orEmpty().map { it.date to it.average }); Text(stringResource(R.string.ui_lines_join_consecutive_recorded_days_only), style = MaterialTheme.typography.bodySmall) }
        StatisticsModule.MOOD_DISTRIBUTION -> stats?.moodDistribution?.entries?.take(limit)?.forEach { (id,count) -> Text(stringResource(R.string.journal_distribution, data.moods.find { it.id == id }?.name ?: "$id", count, (count * 100.0 / stats.entryCount.coerceAtLeast(1)).number(locale))) }
        StatisticsModule.ACTIVITY_FREQUENCY -> stats?.activityFrequency?.take(limit)?.forEach { row -> Text(stringResource(R.string.stats_frequency, data.activities.find { it.id == row.activityId }?.name.orEmpty(), row.entries, row.days)) }
        StatisticsModule.SAME_ENTRY_ASSOCIATION, StatisticsModule.NEXT_DAY_ASSOCIATION -> {
            Text(stringResource(R.string.ui_comparisons_describe_your_records_they_do_not_show_that_an_a), style = MaterialTheme.typography.bodySmall)
            if (module == StatisticsModule.NEXT_DAY_ASSOCIATION) Text(stringResource(R.string.ui_each_recorded_source_day_counts_once_only_the_immediately_fo), style = MaterialTheme.typography.bodySmall)
            (if (module == StatisticsModule.NEXT_DAY_ASSOCIATION) stats?.nextDayAssociations else stats?.sameEntryAssociations).orEmpty().take(limit).forEach { row ->
                Text(data.activities.find { it.id == row.activityId }?.name.orEmpty(), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.journal_association_values, row.meanWith.number(locale), row.withCount, row.meanWithout.number(locale), row.withoutCount))
                Text(stringResource(if (module == StatisticsModule.NEXT_DAY_ASSOCIATION) R.string.journal_next_day_difference else R.string.journal_same_entry_difference, row.difference.number(locale)))
                Text(stringResource(R.string.journal_sample_confidence, statisticsConfidence(row.confidence)))
            }
            Text(stringResource(R.string.ui_confidence_is_a_sample_size_heuristic_not_statistical_certai), style = MaterialTheme.typography.bodySmall)
        }
        StatisticsModule.WEEKDAY_PATTERN -> stats?.weekdays?.forEach { (day,value) -> Text("${day.getDisplayName(TextStyle.FULL, locale)}: ${value.number(locale)}") }
        StatisticsModule.MOOD_ACTIVITIES -> stats?.moodActivities?.entries?.take(limit)?.forEach { (id,values) -> Text("${data.moods.find { it.id == id }?.name}: " + values.entries.sortedByDescending { it.value }.joinToString { "${data.activities.find { a -> a.id == it.key }?.name} (${it.value})" }) }
        StatisticsModule.ACTIVITY_COMBINATIONS -> {
            Text(stringResource(R.string.ui_comparisons_describe_your_records_they_do_not_show_that_an_a), style = MaterialTheme.typography.bodySmall)
            stats?.combinations?.forEach { row -> Text(stringResource(R.string.journal_combination, row.activityIds.joinToString(" + ") { id -> data.activities.find { it.id == id }?.name ?: "$id" }, row.mean.number(locale), row.count, row.baseline.number(locale), statisticsConfidence(row.confidence))) }
            Text(stringResource(R.string.ui_confidence_is_a_sample_size_heuristic_not_statistical_certai), style = MaterialTheme.typography.bodySmall)
        }
        StatisticsModule.GOAL_COMPLETION -> {
            Text(stringResource(R.string.stats_goal_independent), style = MaterialTheme.typography.bodySmall)
            if (result.goals.isEmpty()) Text(stringResource(R.string.stats_no_options))
            result.goals.entries.take(limit).forEach { (id, row) -> Text("${data.goals.find { it.id == id }?.name}: ${row.completed} / ${row.expected} · ${row.completionRate.number(locale)}%") }
        }
        StatisticsModule.BINARY_GOAL_COMPLETION -> result.binaryGoals?.let { summary ->
            Text(stringResource(R.string.stats_goal_independent), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.stats_binary_definition), style = MaterialTheme.typography.bodySmall)
            BinaryMetrics(summary.total, locale)
            if (summary.byGoal.isEmpty()) Text(stringResource(R.string.binary_empty_hint))
            summary.byGoal.entries.take(limit).forEach { (id,metrics) ->
                data.binaryGoals.find { it.id == id }?.let { goal -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    app.moodiary.core.designsystem.JournalIcon(goal.icon, size = 24.dp)
                    Text(goal.name, style = MaterialTheme.typography.titleSmall)
                } }
                BinaryMetrics(metrics, locale)
            }
            Text(stringResource(R.string.stats_weekly), style = MaterialTheme.typography.titleMedium)
            summary.weekly.forEach { Text("${it.start}: ${it.completionRate.number(locale)}% (${it.success}/${it.success + it.failed})") }
            Text(stringResource(R.string.stats_monthly), style = MaterialTheme.typography.titleMedium)
            summary.monthly.forEach { Text("${it.start.toString().take(7)}: ${it.completionRate.number(locale)}% (${it.success}/${it.success + it.failed})") }
            Text(stringResource(R.string.stats_trend_limit), style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable private fun BinaryMetrics(metrics: BinaryGoalMetrics, locale: Locale) {
    Text(stringResource(R.string.stats_binary_counts, metrics.success, metrics.failed, metrics.recorded, metrics.unset))
    Text(stringResource(R.string.stats_rate, metrics.completionRate.number(locale)))
}
