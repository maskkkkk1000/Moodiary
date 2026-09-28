package app.moodiary.domain

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import kotlin.system.measureTimeMillis

/** Reproducible host benchmark, not a claim about device frame time. */
class PerformanceTest {
    @Test fun datasetsThroughFiftyThousandRoundTripWithoutLoss() {
        val zone = ZoneId.of("Asia/Shanghai")
        for (size in listOf(10, 100, 1_000, 10_000, 50_000)) {
            val start = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli()
            val moods = (1L..5L).map { Mood(it, "Mood $it", it.toDouble(), createdAt = 0, updatedAt = 0) }
            val activities = (1L..10L).map { Activity(it, name = "Activity $it", createdAt = 0, updatedAt = 0) }
            val data = JournalData(moods = moods, activities = activities,
                entries = (1L..size.toLong()).map { id -> val mood = moods[(id % 5).toInt()]; Entry(id, start + id * 3_600_000, mood.id, "Record $id: 世界", mood.name, mood.score, mood.color, mood.icon, 0, 0) },
                entryActivities = (1L..size.toLong()).flatMap { id -> listOf(EntryActivity(id, id % 10 + 1), EntryActivity(id, (id + 3) % 10 + 1)) })
            var result: StatisticsSummary? = null
            val statistics = measureTimeMillis { result = Statistics.summarize(data, zone) }
            assertEquals(size, result!!.entryCount)
            val timeline = measureTimeMillis { assertEquals(size, EntrySearch.filter(data, SearchFilter(), zone).size) }
            val search = measureTimeMillis { assertTrue(EntrySearch.filter(data, SearchFilter(query = "世界", activityId = 1), zone).isNotEmpty()) }
            val archive = ByteArrayOutputStream()
            val backup = measureTimeMillis { BackupCodec.write(archive, data, AppPreferences(), { error("No photos") }) }
            val restore = measureTimeMillis { assertEquals(data, BackupCodec.read(ByteArrayInputStream(archive.toByteArray())).data) }
            println("PERF entries=$size timelineMs=$timeline searchMs=$search statisticsMs=$statistics backupMs=$backup restoreMs=$restore zipBytes=${archive.size()}")
        }
    }
}
