package app.moodiary

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.moodiary.core.database.MoodiaryDatabase
import app.moodiary.data.repository.JournalRepository
import app.moodiary.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class DatabaseReliabilityTest {
    @Test fun fiftyThousandEntriesUseIndexedReadsAndSurviveReopening() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "moodiary-instrumented-reliability.db"
        context.deleteDatabase(name)
        var database = Room.databaseBuilder(context, MoodiaryDatabase::class.java, name).build()
        try {
            var repository = JournalRepository(database, app.moodiary.core.backup.RestoreState(context))
            repository.seedDefaults()
            val seed = repository.snapshot()
            val mood = seed.moods.first()
            val start = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli()
            val entries = (1L..50_000L).map { id -> Entry(id, start + id * 60_000, mood.id, "Real Room row $id 世界", mood.name, mood.score, mood.color, mood.icon, 0, 0) }
            val dataset = seed.copy(entries = entries, entryActivities = entries.map { EntryActivity(it.id, seed.activities.first().id) })
            val restore = measureTimeMillis { repository.replaceAll(dataset) }
            val page = measureTimeMillis { assertEquals(100, repository.entriesPage(limit = 100).size) }
            val snapshot = measureTimeMillis { assertEquals(50_000, repository.snapshot().entries.size) }
            database.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN SELECT * FROM entries WHERE timestamp >= 1 AND timestamp < 9999999999999 ORDER BY timestamp DESC, id DESC").use { cursor ->
                val descriptions = mutableListOf<String>()
                while (cursor.moveToNext()) descriptions += cursor.getString(3)
                assertTrue(descriptions.toString(), descriptions.any { it.contains("index_entries_timestamp") })
            }
            database.close()
            database = Room.databaseBuilder(context, MoodiaryDatabase::class.java, name).build()
            repository = JournalRepository(database, app.moodiary.core.backup.RestoreState(context))
            assertEquals(50_000, repository.snapshot().entries.size)
            assertEquals("Real Room row 50000 世界", repository.entriesPage(limit = 1).single().note)
            assertTrue(repository.audit().isEmpty())
            println("ANDROID_PERF rows=50000 replaceMs=$restore page100Ms=$page fullSnapshotMs=$snapshot")
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
