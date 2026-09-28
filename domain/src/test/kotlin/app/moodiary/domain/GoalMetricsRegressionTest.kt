package app.moodiary.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Random

class GoalMetricsRegressionTest {
    private fun goal(type: GoalType = GoalType.DAILY, start: String = "2024-01-01", end: String? = null, target: Int = 1) =
        Goal(id = 1, name = "Practice", goalType = type, targetCount = target, startDate = start, endDate = end, createdAt = 0, updatedAt = 0)
    private fun completions(vararg dates: String) = dates.mapIndexed { index, date -> GoalCompletion(index + 1L, 1, date, 0) }
    private fun schedules(vararg weekdays: Int) = weekdays.map { GoalSchedule(1, it) }

    @Test fun weeklyGoalEndingTodayKeepsPendingStreakUntilTomorrow() {
        val goal = goal(GoalType.WEEKLY_COUNT, end = "2024-01-09", target = 2)
        val rows = completions("2024-01-01", "2024-01-02", "2024-01-08")
        val today = GoalEngine.metrics(goal, emptyList(), rows, LocalDate.parse("2024-01-09"))
        assertEquals(1, today.currentStreak)
        assertEquals(1, today.longestStreak)
        assertEquals(75.0, today.completionRate, 0.0)
        assertEquals(0, GoalEngine.metrics(goal, emptyList(), rows, LocalDate.parse("2024-01-10")).currentStreak)
        val finished = GoalEngine.metrics(goal, emptyList(), rows + completions("2024-01-09"), LocalDate.parse("2024-01-09"))
        assertEquals(2, finished.currentStreak)
        assertEquals(100.0, finished.completionRate, 0.0)
    }

    @Test(timeout = 5000) fun earliestLegalStartToTodayDoesNotEnumerateHistoricalDays() {
        val start = LocalDate.of(1, 1, 1)
        val today = LocalDate.now()
        val rows = completions(today.minusDays(3).toString(), today.minusDays(2).toString(), today.minusDays(1).toString())
        // Repeated calculations represent many imported goals. Cost must follow recorded data, not 2,000 years.
        repeat(100) {
            val result = GoalEngine.metrics(goal(start = start.toString()), emptyList(), rows, today)
            assertEquals((ChronoUnit.DAYS.between(start, today) + 1).toInt(), result.expected)
            assertEquals(3, result.completed)
            assertEquals(3, result.currentStreak)
            assertEquals(3, result.longestStreak)
            assertEquals(12, result.recentWeeks.size)
        }
    }

    @Test(timeout = 5000) fun maximumLegalDateSpanSupportsDailyWeekdayAndWeeklyGoals() {
        val start = LocalDate.of(1, 1, 1)
        val end = LocalDate.of(9999, 12, 31)
        val elapsed = ChronoUnit.DAYS.between(start, end) + 1
        val daily = GoalEngine.metrics(goal(start = start.toString(), end = end.toString()), emptyList(), emptyList(), end)
        assertEquals(elapsed.toInt(), daily.expected)
        assertEquals(0, daily.currentStreak)
        assertEquals(12, daily.recentWeeks.size)

        val selected = setOf(1, 3, 5)
        val weekdays = GoalEngine.metrics(goal(GoalType.WEEKDAYS, start.toString(), end.toString()), schedules(1, 3, 5), emptyList(), end)
        val expected = selected.sumOf { day ->
            val first = start.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(day)))
            (ChronoUnit.DAYS.between(first, end) / 7 + 1).toInt()
        }
        assertEquals(expected, weekdays.expected)
        val weekly = GoalEngine.metrics(goal(GoalType.WEEKLY_COUNT, start.toString(), end.toString(), 3), emptyList(), emptyList(), end)
        val weeks = ChronoUnit.WEEKS.between(start, end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) + 1
        assertEquals(weeks.toInt() * 3, weekly.expected)
        assertEquals(12, weekly.recentWeeks.size)
    }

    @Test fun weekdayGapsOnlyBreakStreakWhenAScheduledOpportunityIsMissed() {
        val goal = goal(GoalType.WEEKDAYS)
        val selected = schedules(1, 3, 5)
        val rows = completions("2024-01-01", "2024-01-03", "2024-01-08", "2024-01-10")
        val pendingFriday = GoalEngine.metrics(goal, selected, rows, LocalDate.parse("2024-01-12"))
        assertEquals(2, pendingFriday.currentStreak)
        assertEquals(2, pendingFriday.longestStreak)
        assertEquals(6, pendingFriday.expected)
        assertEquals(4, pendingFriday.completed)
        assertEquals(0, GoalEngine.metrics(goal, selected, rows, LocalDate.parse("2024-01-13")).currentStreak)
        val resumed = GoalEngine.metrics(goal, selected, rows + completions("2024-01-15", "2024-01-17"), LocalDate.parse("2024-01-18"))
        assertEquals(2, resumed.currentStreak)
        assertEquals(2, resumed.longestStreak)
    }

    @Test fun partialFirstAndLastWeeksRetainWeeklyTargetAndCapExtraCompletions() {
        val goal = goal(GoalType.WEEKLY_COUNT, "2024-01-03", "2024-01-09", 3)
        val rows = completions("2024-01-01", "2024-01-03", "2024-01-04", "2024-01-05", "2024-01-06", "2024-01-08", "2024-01-09", "2024-01-10")
        val result = GoalEngine.metrics(goal, emptyList(), rows, LocalDate.parse("2024-01-10"))
        assertEquals(6, result.expected)
        assertEquals(5, result.completed)
        assertEquals(0, result.currentStreak)
        assertEquals(1, result.longestStreak)
        assertEquals(listOf(3, 3), result.recentWeeks.map { it.target })
        assertEquals(listOf(3, 2), result.recentWeeks.map { it.completed })
        assertEquals(1, GoalEngine.metrics(goal, emptyList(), rows, LocalDate.parse("2024-01-09")).currentStreak)
    }

    @Test fun weekdayPartialWeeksHonorYearBoundaryLeapDayAndDuplicateHistory() {
        val goal = goal(GoalType.WEEKDAYS, "2023-12-31", "2024-02-29")
        val selected = schedules(1, 4, 7)
        val rows = completions("2023-12-30", "2023-12-31", "2024-01-01", "2024-01-04", "2024-02-25", "2024-02-26", "2024-02-29", "2024-02-29", "2024-03-03")
        val result = GoalEngine.metrics(goal, selected, rows, LocalDate.parse("2024-03-02"))
        assertEquals(6, result.completed)
        assertEquals(27, result.expected)
        assertEquals(3, result.currentStreak)
        assertEquals(3, result.longestStreak)
        assertEquals(1, result.recentWeeks.first().target)
        assertEquals(2, result.recentWeeks.last().target)
        assertEquals(2, result.recentWeeks.last().completed)
    }

    @Test fun aRangeWithNoSelectedWeekdayHasNoExpectedOrCompletedOpportunities() {
        val goal = goal(GoalType.WEEKDAYS, "2024-01-02", "2024-01-03")
        val result = GoalEngine.metrics(goal, schedules(5), completions("2024-01-02"), LocalDate.parse("2024-01-04"))
        assertEquals(GoalMetrics(0, 0, 0.0, 0, 0, listOf(WeeklyProgress(LocalDate.parse("2024-01-01"), 0, 0))), result)
    }

    @Test fun optimizedMetricsMatchEnumeratedReferenceAcrossSchedulesAndBoundaries() {
        val random = Random(73514)
        repeat(200) { sample ->
            val start = LocalDate.parse("2023-11-01").plusDays(random.nextInt(80).toLong())
            val today = start.plusDays(random.nextInt(150).toLong())
            val end = if (sample % 3 == 0) today.minusDays(random.nextInt(10).toLong()).coerceAtLeast(start) else today
            val type = GoalType.entries[sample % GoalType.entries.size]
            val target = if (type == GoalType.WEEKLY_COUNT) random.nextInt(7) + 1 else 1
            val goal = goal(type, start.toString(), end.toString(), target)
            val selected = (1..7).filter { random.nextBoolean() }.ifEmpty { listOf(1) }
            val schedules = selected.map { GoalSchedule(1, it) }
            val eligible = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }
                .filter { type != GoalType.WEEKDAYS || it.dayOfWeek.value in selected }.toList()
            val completed = eligible.filter { random.nextBoolean() }.toSet()
            val rows = completed.mapIndexed { index, date -> GoalCompletion(index + 1L, 1, date.toString(), 0) }
            val weeks = generateSequence(start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) { it.plusWeeks(1) }
                .takeWhile { it <= end }.toList()
            val progress = weeks.map { week ->
                val days = (0L..6L).map { week.plusDays(it) }
                val expected = if (type == GoalType.WEEKLY_COUNT) target else days.count { it in eligible }
                WeeklyProgress(week, minOf(expected, days.count { it in completed }), expected)
            }
            val states = if (type == GoalType.WEEKLY_COUNT) progress.map { it.completed == it.target } else eligible.map { it in completed }
            val pending = end == today && (type == GoalType.WEEKLY_COUNT || eligible.lastOrNull() == today)
            val effective = if (pending && states.lastOrNull() == false) states.dropLast(1) else states
            var run = 0
            var longest = 0
            states.forEach { done -> run = if (done) run + 1 else 0; longest = maxOf(longest, run) }
            val result = GoalEngine.metrics(goal, schedules, rows, today)
            assertEquals("sample $sample current", effective.asReversed().takeWhile { it }.size, result.currentStreak)
            assertEquals("sample $sample longest", longest, result.longestStreak)
            assertEquals("sample $sample expected", progress.sumOf { it.target }, result.expected)
            assertEquals("sample $sample completed", progress.sumOf { it.completed }, result.completed)
            assertEquals("sample $sample recent", progress.takeLast(12), result.recentWeeks)
        }
    }
}
