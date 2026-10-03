package app.moodiary.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.moodiary.core.datastore.StatisticsFilterStore
import app.moodiary.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files

@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class StatisticsFilterStoreTest {
    @Test fun filtersSurviveStoreRestartAndResetAndRapidUpdates() = runBlocking {
        val directory=Files.createTempDirectory("synthetic-statistics-filters").toFile()
        val file=java.io.File(directory,"filters.preferences_pb")
        var scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            var store=StatisticsFilterStore(PreferenceDataStoreFactory.create(scope=scope,produceFile={file}))
            val expected=StatisticsFilters(range=StatisticsRange.CUSTOM,customStart="2024-02-01",customEnd="2024-02-29",moodIds=setOf(1,2),activityIds=setOf(3,4),binaryGoalIds=setOf(9),modules=setOf(StatisticsModule.BINARY_GOAL_COMPLETION))
            store.update { expected }
            assertEquals(expected,store.filters.first())
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
            store=StatisticsFilterStore(PreferenceDataStoreFactory.create(scope=scope,produceFile={file}))
            assertEquals(expected,store.filters.first())
            coroutineScope { (20L..29L).map { id -> async {store.update {it.copy(activityIds=it.activityIds+id)}} }.awaitAll() }
            assertEquals(setOf(3L,4L)+(20L..29L),store.filters.first().activityIds)
            store.reset()
            assertEquals(StatisticsFilters(),store.filters.first())
        } finally {scope.coroutineContext[Job]!!.cancelAndJoin();directory.deleteRecursively()}
    }
}
