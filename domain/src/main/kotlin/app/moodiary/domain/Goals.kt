package app.moodiary.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class GoalDate(val goalId: Long, val date: String)
data class WeeklyProgress(val weekStart: LocalDate, val completed: Int, val target: Int)
data class GoalMetrics(val currentStreak: Int, val longestStreak: Int, val completionRate: Double, val completed: Int, val expected: Int, val recentWeeks: List<WeeklyProgress>)
data class Streak(val current: Int, val longest: Int)

object GoalEngine {
    fun isScheduled(goal: Goal, schedules: List<GoalSchedule>, date: LocalDate): Boolean =
        date >= LocalDate.parse(goal.startDate) && (goal.endDate == null || date <= LocalDate.parse(goal.endDate)) &&
            (goal.goalType != GoalType.WEEKDAYS || schedules.any { it.goalId == goal.id && it.dayOfWeek == date.dayOfWeek.value })

    /** Desired automatic rows, independent of existing rows. Repository reconciles in the entry transaction,
     * preserves MANUAL rows and removes obsolete LINKED_ACTIVITY rows. One goal/date can complete once. */
    fun desiredLinkedCompletions(data: JournalData, zone: ZoneId): Set<GoalDate> {
        val entryDates = data.entries.associate { it.id to it.date(zone) }
        val daysByActivity = data.entryActivities.groupBy { it.activityId }.mapValues { (_, rows) -> rows.mapNotNullTo(HashSet()) { entryDates[it.entryId] } }
        return data.goals.asSequence().filter { it.linkedActivityId != null }.flatMap { goal ->
            // Archive hides a goal; its historical eligible completions must stay valid.
            daysByActivity[goal.linkedActivityId].orEmpty().asSequence().filter { isScheduled(goal, data.schedules, it) }.map { GoalDate(goal.id, it.toString()) }
        }.toSet()
    }

    fun metrics(goal: Goal, schedules: List<GoalSchedule>, completions: List<GoalCompletion>, today: LocalDate): GoalMetrics {
        val start = LocalDate.parse(goal.startDate)
        val end = minOf(today, goal.endDate?.let(LocalDate::parse) ?: today)
        if (start > end) return GoalMetrics(0, 0, 0.0, 0, 0, emptyList())
        val weekdays = if (goal.goalType == GoalType.WEEKDAYS) schedules.asSequence()
            .filter { it.goalId == goal.id }.map { it.dayOfWeek }.toSet() else (1..7).toSet()
        val complete = completions.asSequence().filter { it.goalId == goal.id }.map { LocalDate.parse(it.date) }
            .filter { it in start..end && it.dayOfWeek.value in weekdays }.toSet()
        val weekStart = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekCount = ChronoUnit.WEEKS.between(weekStart, weekEnd).toInt() + 1
        val countsByWeek = complete.groupingBy { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.eachCount()
        // Only display weeks are materialized. A valid year-0001 goal must not allocate millions of days.
        val recentWeeks = (maxOf(0, weekCount - 12) until weekCount).map { offset ->
            val week = weekStart.plusWeeks(offset.toLong())
            val target = if (goal.goalType == GoalType.WEEKLY_COUNT) goal.targetCount else
                scheduledCount(maxOf(start, week), minOf(end, week.plusDays(6)), weekdays)
            WeeklyProgress(week, minOf(countsByWeek[week] ?: 0, target), target)
        }
        if (goal.goalType == GoalType.WEEKLY_COUNT) {
            val completedWeeks = countsByWeek.asSequence().filter { it.value >= goal.targetCount }
                .map { ChronoUnit.WEEKS.between(weekStart, it.key).toInt() }.sorted().toList()
            // The end date is inclusive: its week remains pending until that local day has passed.
            val streak = streakForPositions(completedWeeks, weekCount, pendingLast = end == today)
            val completed = countsByWeek.values.sumOf { minOf(it, goal.targetCount) }
            val expected = weekCount * goal.targetCount
            return GoalMetrics(streak.current, streak.longest, percentage(completed, expected), completed, expected, recentWeeks)
        }
        val expected = scheduledCount(start, end, weekdays)
        // Consecutive scheduled opportunities have adjacent positions even across non-scheduled dates.
        val completedPositions = complete.sorted().map { scheduledCount(start, it, weekdays) - 1 }
        val streak = streakForPositions(completedPositions, expected, pendingLast = end == today && today.dayOfWeek.value in weekdays)
        return GoalMetrics(streak.current, streak.longest, percentage(complete.size, expected), complete.size, expected, recentWeeks)
    }

    private fun percentage(numerator: Int, denominator: Int) = if (denominator == 0) 0.0 else numerator.toDouble() / denominator * 100

    /** O(7), independent of the number of years spanned. Supported dates fit comfortably in Int. */
    private fun scheduledCount(start: LocalDate, end: LocalDate, weekdays: Set<Int>): Int {
        if (start > end || weekdays.isEmpty()) return 0
        val days = ChronoUnit.DAYS.between(start, end) + 1
        val fullWeeks = days / 7
        val remainder = (days % 7).toInt()
        val extra = (0 until remainder).count { offset -> (start.dayOfWeek.value - 1 + offset) % 7 + 1 in weekdays }
        return (fullWeeks * weekdays.size + extra).toInt()
    }

    /** Positions are sorted, unique zero-based scheduled opportunities that were completed. */
    private fun streakForPositions(positions: List<Int>, expected: Int, pendingLast: Boolean): Streak {
        var run = 0; var longest = 0; var previous = -2
        positions.forEach { position ->
            run = if (position == previous + 1) run + 1 else 1
            longest = maxOf(longest, run)
            previous = position
        }
        var terminal = expected - 1
        if (pendingLast && positions.lastOrNull() != terminal) terminal--
        return Streak(if (positions.lastOrNull() == terminal) run else 0, longest)
    }
}

data class Achievement(val id: String, val title: String, val unlocked: Boolean, val progress: Int, val target: Int)

object JournalAchievements {
    fun streak(entries: List<Entry>, zone: ZoneId, today: LocalDate): Streak {
        val days = entries.map { it.date(zone) }.filter { it <= today }.distinct().sorted()
        var longest = 0; var run = 0; var prior: LocalDate? = null
        days.forEach { day -> run = if (prior?.plusDays(1) == day) run + 1 else 1; longest = maxOf(longest, run); prior = day }
        val current = if (days.lastOrNull() == today || days.lastOrNull() == today.minusDays(1)) run else 0
        return Streak(current, longest)
    }

    fun evaluate(entries: List<Entry>, zone: ZoneId, today: LocalDate): List<Achievement> {
        val recorded = entries.filter { it.date(zone) <= today }
        val days = recorded.map { it.date(zone) }.distinct().size
        val longest = streak(recorded, zone, today).longest
        return listOf(
            achievement("first", "First page", recorded.size, 1),
            achievement("days7", "Seven days recorded", days, 7), achievement("days30", "Thirty days recorded", days, 30),
            achievement("entries100", "One hundred entries", recorded.size, 100), achievement("entries365", "365 entries", recorded.size, 365),
            achievement("streak7", "Seven day rhythm", longest, 7), achievement("streak30", "Thirty day rhythm", longest, 30),
            achievement("streak100", "One hundred day rhythm", longest, 100)
        )
    }
    private fun achievement(id: String, name: String, actual: Int, target: Int) = Achievement(id, name, actual >= target, minOf(actual, target), target)
}
