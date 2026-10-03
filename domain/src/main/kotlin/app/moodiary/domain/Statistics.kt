package app.moodiary.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

enum class Confidence { INSUFFICIENT, LOW, MEDIUM, HIGH;
    companion object {
        fun forSamples(samples: Int) = when {
            samples < 5 -> INSUFFICIENT
            samples < 15 -> LOW
            samples < 30 -> MEDIUM
            else -> HIGH
        }
    }
}

data class DailyMood(val date: LocalDate, val average: Double, val count: Int)
data class ActivityFrequency(val activityId: Long, val entries: Int, val days: Int)
data class Association(
    val activityId: Long,
    val withCount: Int,
    val withoutCount: Int,
    val meanWith: Double?,
    val meanWithout: Double?
) {
    val difference: Double? get() = if (meanWith != null && meanWithout != null) meanWith - meanWithout else null
    // Both sides of a comparison must have enough observations.
    val confidence: Confidence get() = Confidence.forSamples(minOf(withCount, withoutCount))
}
data class CombinationPattern(val activityIds: Set<Long>, val count: Int, val mean: Double, val baseline: Double?, val confidence: Confidence)
data class StatisticsSummary(
    val entryCount: Int,
    val average: Double?,
    val trend: List<DailyMood>,
    val moodDistribution: Map<Long, Int>,
    val activityFrequency: List<ActivityFrequency>,
    val sameEntryAssociations: List<Association>,
    val nextDayAssociations: List<Association>,
    val weekdays: Map<DayOfWeek, Double>,
    val moodActivities: Map<Long, Map<Long, Int>>,
    val combinations: List<CombinationPattern>
)

object EntrySearch {
    fun filter(data: JournalData, filter: SearchFilter, zone: ZoneId): List<Entry> {
        if (filter.from != null && filter.through != null && filter.from > filter.through) return emptyList()
        val activities = data.activities.associateBy { it.id }
        val links = data.entryActivities.groupBy { it.entryId }
        val photoEntries = data.photos.mapTo(HashSet()) { it.entryId }
        val importantDates = data.importantDays.mapTo(HashSet()) { LocalDate.parse(it.date) }
        val query = filter.query.trim()
        return data.entries.asSequence().filter { entry ->
            val date = entry.date(zone)
            (filter.from == null || date >= filter.from) &&
                (filter.through == null || date <= filter.through) &&
                (filter.moodId == null || entry.moodId == filter.moodId) &&
                (filter.activityId == null || links[entry.id].orEmpty().any { it.activityId == filter.activityId }) &&
                (filter.hasPhoto == null || (entry.id in photoEntries) == filter.hasPhoto) &&
                (!filter.importantOnly || date in importantDates) &&
                (query.isEmpty() || entry.note.contains(query, ignoreCase = true) ||
                    links[entry.id].orEmpty().any { activities[it.activityId]?.name?.contains(query, ignoreCase = true) == true })
        }.sortedWith(compareByDescending<Entry> { it.timestamp }.thenByDescending { it.id }).toList()
    }
}

object Statistics {
    fun summarize(data: JournalData, zone: ZoneId, from: LocalDate? = null, through: LocalDate? = null, modules: Set<StatisticsModule> = StatisticsModule.entries.toSet()): StatisticsSummary {
        val entries = data.entries.filter { (from == null || it.date(zone) >= from) && (through == null || it.date(zone) <= through) }
        val entriesById = entries.associateBy { it.id }
        val needsLinks = modules.any { it in setOf(StatisticsModule.ACTIVITY_FREQUENCY, StatisticsModule.SAME_ENTRY_ASSOCIATION,
            StatisticsModule.NEXT_DAY_ASSOCIATION, StatisticsModule.MOOD_ACTIVITIES, StatisticsModule.ACTIVITY_COMBINATIONS) }
        val links = if (needsLinks) data.entryActivities.asSequence().filter { it.entryId in entriesById }.distinct().toList() else emptyList()
        val linksByEntry = links.groupBy { it.entryId }
        val entriesByActivity = links.groupBy { it.activityId }.mapValues { (_, links) -> links.map { entriesById.getValue(it.entryId) } }
        val needsDaily = modules.any { it in setOf(StatisticsModule.MOOD_TREND, StatisticsModule.NEXT_DAY_ASSOCIATION, StatisticsModule.WEEKDAY_PATTERN) }
        val daily = if (needsDaily) entries.groupBy { it.date(zone) }.toSortedMap() else emptyMap()
        val trend = daily.map { (date, rows) -> DailyMood(date, rows.map { it.moodScore }.average(), rows.size) }
        val dailyMean = trend.associate { it.date to it.average }
        val activityDates = entriesByActivity.mapValues { (_, rows) -> rows.mapTo(HashSet()) { it.date(zone) } }
        val total = entries.sumOf { it.moodScore }
        val activityIds = data.activities.map { it.id }
        val same = if (StatisticsModule.SAME_ENTRY_ASSOCIATION !in modules) emptyList() else activityIds.map { id ->
            val with = entriesByActivity[id].orEmpty()
            val sum = with.sumOf { it.moodScore }
            Association(id, with.size, entries.size - with.size, mean(sum, with.size), mean(total - sum, entries.size - with.size))
        }
        // Only observed adjacent pairs entirely within the selected range qualify.
        // Missing days are never filled, and each local source day has weight one.
        val adjacentDays = dailyMean.keys.filter { it.plusDays(1) in dailyMean }
        val next = if (StatisticsModule.NEXT_DAY_ASSOCIATION !in modules) emptyList() else activityIds.map { id ->
            var withCount = 0; var withoutCount = 0; var withSum = 0.0; var withoutSum = 0.0
            val dates = activityDates[id].orEmpty()
            adjacentDays.forEach { source ->
                val outcome = dailyMean.getValue(source.plusDays(1))
                if (source in dates) { withCount++; withSum += outcome } else { withoutCount++; withoutSum += outcome }
            }
            Association(id, withCount, withoutCount, mean(withSum, withCount), mean(withoutSum, withoutCount))
        }
        // Pair exploration is explicitly limited to the 24 most frequent activities, to bound work.
        // Counts/sums are accumulated without retaining a separate list of scores for each pair.
        val exploredIds = entriesByActivity.entries.sortedByDescending { it.value.size }.take(24).mapTo(HashSet()) { it.key }
        val pairs = HashMap<Set<Long>, PairAccumulator>()
        if (StatisticsModule.ACTIVITY_COMBINATIONS in modules) entries.forEach { entry ->
            val ids = linksByEntry[entry.id].orEmpty().map { it.activityId }.filter { it in exploredIds }.distinct().sorted()
            for (i in ids.indices) for (j in i + 1 until ids.size) {
                pairs.getOrPut(setOf(ids[i], ids[j])) { PairAccumulator() }.apply { count++; sum += entry.moodScore }
            }
        }
        return StatisticsSummary(
            entries.size, mean(total, entries.size), if (StatisticsModule.MOOD_TREND in modules) trend else emptyList(),
            if (StatisticsModule.MOOD_DISTRIBUTION in modules) entries.groupingBy { it.moodId }.eachCount() else emptyMap(),
            if (StatisticsModule.ACTIVITY_FREQUENCY in modules) entriesByActivity.map { (id, rows) -> ActivityFrequency(id, rows.size, activityDates.getValue(id).size) }.sortedByDescending { it.entries } else emptyList(),
            same, next,
            if (StatisticsModule.WEEKDAY_PATTERN in modules) trend.groupBy { it.date.dayOfWeek }.mapValues { (_, days) -> days.map { it.average }.average() } else emptyMap(),
            if (StatisticsModule.MOOD_ACTIVITIES in modules) entries.groupBy { it.moodId }.mapValues { (_, rows) -> rows.flatMap { linksByEntry[it.id].orEmpty() }.groupingBy { it.activityId }.eachCount() } else emptyMap(),
            pairs.map { (ids, scores) ->
                val baseline = mean(total - scores.sum, entries.size - scores.count)
                CombinationPattern(ids, scores.count, scores.sum / scores.count, baseline, Confidence.forSamples(minOf(scores.count, entries.size - scores.count)))
            }.sortedByDescending { it.count }.take(20)
        )
    }

    fun aggregate(entries: List<Entry>, mode: String): Double? = when (mode) {
        "LATEST" -> entries.maxWithOrNull(compareBy<Entry> { it.timestamp }.thenBy { it.id })?.moodScore
        "HIGHEST" -> entries.maxOfOrNull { it.moodScore }
        "LOWEST" -> entries.minOfOrNull { it.moodScore }
        else -> entries.takeIf { it.isNotEmpty() }?.map { it.moodScore }?.average()
    }

    private fun mean(sum: Double, count: Int): Double? = if (count == 0) null else sum / count
    private data class PairAccumulator(var count: Int = 0, var sum: Double = 0.0)
}
