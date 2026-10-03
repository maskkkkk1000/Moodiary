package app.moodiary

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.moodiary.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset

/** Measures the production calculator on Android with a year of synthetic outcomes. */
@RunWith(AndroidJUnit4::class)
class BinaryGoalPerformanceDeviceTest {
    @Test fun yearOfOutcomesForTenHundredAndThousandGoals(): Unit = runBlocking {
        withContext(Dispatchers.Default) {
            val start = LocalDate.of(2024, 1, 1)
            val created = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            for (count in listOf(10, 100, 1000)) {
                val goals = (1..count).map { BinaryGoal(it.toLong(), "Synthetic goal $it", createdAt = created) }
                val dates = (0 until 365).map { start.plusDays(it.toLong()).toString() }
                val records = goals.flatMap { goal -> dates.mapIndexed { index, date ->
                    BinaryGoalRecord(goal.id * 1000 + index, goal.id, date, index % 2, created, created)
                } }
                val before = SystemClock.elapsedRealtime()
                val result = BinaryGoalStatistics.summarize(goals, records, start, start.plusDays(364), ZoneOffset.UTC)
                val elapsed = SystemClock.elapsedRealtime() - before
                assertEquals(count * 365, result.total.recorded)
                assertEquals(0L, result.total.unset)
                assertEquals(count, result.byGoal.size)
                Log.i("BinaryGoalPerformance", "goals=$count rows=${records.size} elapsedMs=$elapsed")
                assertTrue("Android binary statistics exceeded 15 seconds: $elapsed ms", elapsed < 15_000)
            }
        }
    }
}
