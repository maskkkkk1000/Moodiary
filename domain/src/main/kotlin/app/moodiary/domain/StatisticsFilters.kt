package app.moodiary.domain

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@Serializable enum class StatisticsRange { THIS_WEEK, THIS_MONTH, DAYS_7, DAYS_30, MONTHS_3, YEAR_1, ALL, CUSTOM }
@Serializable enum class StatisticsModule {
    MOOD_TREND, MOOD_DISTRIBUTION, ACTIVITY_FREQUENCY, SAME_ENTRY_ASSOCIATION,
    NEXT_DAY_ASSOCIATION, WEEKDAY_PATTERN, MOOD_ACTIVITIES, ACTIVITY_COMBINATIONS,
    GOAL_COMPLETION, BINARY_GOAL_COMPLETION
}

@Serializable data class StatisticsFilters(
    val range: StatisticsRange = StatisticsRange.DAYS_30,
    val customStart: String = "",
    val customEnd: String = "",
    /** Empty means all, otherwise multi-selection is OR within each category. */
    val moodIds: Set<Long> = emptySet(),
    val activityIds: Set<Long> = emptySet(),
    val binaryGoalIds: Set<Long> = emptySet(),
    val modules: Set<StatisticsModule> = setOf(StatisticsModule.MOOD_TREND, StatisticsModule.MOOD_DISTRIBUTION, StatisticsModule.BINARY_GOAL_COMPLETION)
) {
    fun dates(today: LocalDate): StatisticsDates = when (range) {
        StatisticsRange.THIS_WEEK -> StatisticsDates(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today)
        StatisticsRange.THIS_MONTH -> StatisticsDates(today.withDayOfMonth(1), today)
        StatisticsRange.DAYS_7 -> StatisticsDates(today.minusDays(6), today)
        StatisticsRange.DAYS_30 -> StatisticsDates(today.minusDays(29), today)
        StatisticsRange.MONTHS_3 -> StatisticsDates(today.minusMonths(3).plusDays(1), today)
        StatisticsRange.YEAR_1 -> StatisticsDates(today.minusYears(1).plusDays(1), today)
        StatisticsRange.ALL -> StatisticsDates(null, today)
        StatisticsRange.CUSTOM -> {
            val from = LocalDate.parse(customStart)
            val through = LocalDate.parse(customEnd)
            require(from.year in 1..9999 && through.year in 1..9999 && from <= through) { "Invalid custom date range" }
            StatisticsDates(from, through)
        }
    }
}

data class StatisticsDates(val from: LocalDate?, val through: LocalDate)

object StatisticsEntryFilter {
    fun apply(data: JournalData, filters: StatisticsFilters, dates: StatisticsDates, zone: ZoneId): List<Entry> {
        val matchingActivities = if (filters.activityIds.isEmpty()) emptySet() else data.entryActivities.asSequence()
            .filter { it.activityId in filters.activityIds }.map { it.entryId }.toHashSet()
        return data.entries.filter { entry ->
            val date = entry.date(zone)
            (dates.from == null || date >= dates.from) && date <= dates.through &&
                (filters.moodIds.isEmpty() || entry.moodId in filters.moodIds) &&
                (filters.activityIds.isEmpty() || entry.id in matchingActivities)
        }
    }
}

data class SelectedStatistics(
    val dates: StatisticsDates,
    val entries: StatisticsSummary?,
    val goals: Map<Long, GoalMetrics>,
    val binaryGoals: BinaryGoalSummary?
)

/** Entry filters apply to entries only. Both independent goal systems share the date range;
 * no diary observation is needed for a manually recorded goal result. */
object StatisticsCalculator {
    fun calculate(data: JournalData, filters: StatisticsFilters, zone: ZoneId, today: LocalDate): SelectedStatistics {
        val dates = filters.dates(today)
        val entryModules = filters.modules - setOf(StatisticsModule.GOAL_COMPLETION, StatisticsModule.BINARY_GOAL_COMPLETION)
        val summary = if (entryModules.isEmpty()) null else Statistics.summarize(
            data.copy(entries = StatisticsEntryFilter.apply(data, filters, dates, zone)), zone, modules = entryModules
        )
        val goals = if (StatisticsModule.GOAL_COMPLETION !in filters.modules) emptyMap() else {
            val schedules = data.schedules.groupBy { it.goalId }
            val completions = data.completions.groupBy { it.goalId }
            data.goals.associate { goal ->
                val start = maxOf(LocalDate.parse(goal.startDate), dates.from ?: LocalDate.parse(goal.startDate))
                goal.id to GoalEngine.metrics(goal.copy(startDate = start.toString()), schedules[goal.id].orEmpty(),
                    completions[goal.id].orEmpty(), minOf(dates.through, today))
            }
        }
        val binary = if (StatisticsModule.BINARY_GOAL_COMPLETION !in filters.modules) null
        else if (dates.from != null && dates.from > today) BinaryGoalSummary(BinaryGoalMetrics(0, 0, 0), emptyMap(), emptyList(), emptyList())
        else BinaryGoalStatistics.summarize(
            data.binaryGoals.filter { filters.binaryGoalIds.isEmpty() || it.id in filters.binaryGoalIds },
            data.binaryGoalRecords, dates.from, minOf(dates.through, today), zone
        )
        return SelectedStatistics(dates, summary, goals, binary)
    }
}
