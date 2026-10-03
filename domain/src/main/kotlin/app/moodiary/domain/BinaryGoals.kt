package app.moodiary.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class BinaryGoalMetrics(val success: Int, val failed: Int, val unset: Long) {
    val recorded: Int get() = success + failed
    /** No recorded outcomes means unknown, not zero percent. Unset is never a failure. */
    val completionRate: Double? get() = if (recorded == 0) null else success * 100.0 / recorded
}
data class BinaryGoalPeriod(val start: LocalDate, val success: Int, val failed: Int) {
    val completionRate: Double? get() = if (success + failed == 0) null else success * 100.0 / (success + failed)
}
data class BinaryGoalSummary(
    val total: BinaryGoalMetrics,
    val byGoal: Map<Long, BinaryGoalMetrics>,
    val weekly: List<BinaryGoalPeriod>,
    val monthly: List<BinaryGoalPeriod>
)

object BinaryGoalStatistics {
    /** O(records + goals + displayed periods), not O(goals * days * records).
     * Creation day begins each goal's opportunities; explicit historical results can extend that
     * start backwards. Archive only hides a goal and never erases its historical results.
     * Trends show at most the latest 104 weeks / 36 months, with empty buckets left unknown. */
    fun summarize(
        goals: List<BinaryGoal>, records: List<BinaryGoalRecord>, from: LocalDate?, through: LocalDate, zone: ZoneId
    ): BinaryGoalSummary {
        require(from == null || from <= through) { "Invalid date range" }
        val ids = goals.mapTo(HashSet()) { it.id }
        val earliest = HashMap<Long, LocalDate>()
        val counts = HashMap<Long, Counter>()
        val weeks = HashMap<LocalDate, Counter>()
        val months = HashMap<LocalDate, Counter>()
        // The unique database constraint is the source of truth. Reject invalid unvalidated input
        // rather than silently producing a misleading percentage from duplicate goal/day rows.
        val seen = HashSet<Pair<Long, String>>()
        records.forEach { row ->
            if (row.goalId !in ids) return@forEach
            require(row.value == 0 || row.value == 1) { "Binary result must be zero or one" }
            require(seen.add(row.goalId to row.date)) { "Duplicate binary goal date" }
            val date = LocalDate.parse(row.date)
            if (date > through) return@forEach
            earliest[row.goalId] = minOf(earliest[row.goalId] ?: date, date)
            if (from != null && date < from) return@forEach
            counts.getOrPut(row.goalId) { Counter() }.add(row.value)
            weeks.getOrPut(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) { Counter() }.add(row.value)
            months.getOrPut(date.withDayOfMonth(1)) { Counter() }.add(row.value)
        }
        var firstOpportunity: LocalDate? = null
        val byGoal = goals.associate { goal ->
            val created = Instant.ofEpochMilli(goal.createdAt).atZone(zone).toLocalDate()
            val start = maxOf(from ?: LocalDate.MIN, minOf(created, earliest[goal.id] ?: created))
            if (start <= through) firstOpportunity = minOf(firstOpportunity ?: start, start)
            val opportunities = if (start > through) 0L else ChronoUnit.DAYS.between(start, through) + 1
            val counter = counts[goal.id] ?: Counter()
            goal.id to BinaryGoalMetrics(counter.success, counter.failed, (opportunities - counter.success - counter.failed).coerceAtLeast(0))
        }
        val total = BinaryGoalMetrics(byGoal.values.sumOf { it.success }, byGoal.values.sumOf { it.failed }, byGoal.values.sumOf { it.unset })
        val first = firstOpportunity
        val weekly = if (first == null) emptyList() else {
            val last = through.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val start = maxOf(first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), last.minusWeeks(103))
            (0..ChronoUnit.WEEKS.between(start, last).toInt()).map { period(start.plusWeeks(it.toLong()), weeks) }
        }
        val monthly = if (first == null) emptyList() else {
            val last = through.withDayOfMonth(1)
            val start = maxOf(first.withDayOfMonth(1), last.minusMonths(35))
            (0..ChronoUnit.MONTHS.between(start, last).toInt()).map { period(start.plusMonths(it.toLong()), months) }
        }
        return BinaryGoalSummary(total, byGoal, weekly, monthly)
    }

    private fun period(start: LocalDate, counts: Map<LocalDate, Counter>) = BinaryGoalPeriod(start, counts[start]?.success ?: 0, counts[start]?.failed ?: 0)
    private class Counter(var success: Int = 0, var failed: Int = 0) {
        fun add(value: Int) { if (value == 1) success++ else failed++ }
    }
}
