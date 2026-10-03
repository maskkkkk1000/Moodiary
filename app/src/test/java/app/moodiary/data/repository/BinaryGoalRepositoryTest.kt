package app.moodiary.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.moodiary.core.backup.RestoreState
import app.moodiary.core.database.MoodiaryDatabase
import app.moodiary.domain.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BinaryGoalRepositoryTest {
    private lateinit var database: MoodiaryDatabase
    private lateinit var repository: JournalRepository

    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, MoodiaryDatabase::class.java).build()
        repository = JournalRepository(database, RestoreState(context))
        repository.seedDefaults()
    }
    @After fun close() { database.close() }

    @Test fun createEditArchiveRestorePreservesStableIdentityAndCreationTime() = runBlocking {
        val id = repository.saveBinaryGoal(BinaryGoal(name = "  Read  ", icon = "📚", description = "One page"))
        val created = repository.snapshot().binaryGoals.single()
        assertEquals("Read", created.name)
        repository.saveBinaryGoal(created.copy(name = "Read two pages", icon = "📖", description = "New description", createdAt = 1, isArchived = true))
        val archived = repository.snapshot().binaryGoals.single()
        assertEquals(id, archived.id); assertEquals(created.createdAt, archived.createdAt)
        assertTrue(archived.updatedAt > created.updatedAt)
        assertEquals("📖", archived.icon); assertTrue(archived.isArchived)
        repository.saveBinaryGoal(archived.copy(isArchived = false))
        assertFalse(repository.snapshot().binaryGoals.single().isArchived)
        assertTrue(repository.audit().isEmpty())
    }

    @Test fun successFailureUnsetAndSameDayUpdateHaveExactlyOneStableLogicalRecord() = runBlocking {
        val id = repository.saveBinaryGoal(BinaryGoal(name = "Read"))
        repository.setBinaryGoalRecord(id, "2024-02-29", 1)
        val first = repository.snapshot().binaryGoalRecords.single()
        repository.setBinaryGoalRecord(id, "2024-02-29", 0)
        val second = repository.snapshot().binaryGoalRecords.single()
        assertEquals(first.id, second.id); assertEquals(first.createdAt, second.createdAt)
        assertEquals(0, second.value); assertTrue(second.updatedAt > first.updatedAt)
        repository.setBinaryGoalRecord(id, "2024-02-29", null)
        assertTrue(repository.snapshot().binaryGoalRecords.isEmpty())
        repository.setBinaryGoalRecord(id, "2024-02-29", null)
        assertTrue(repository.snapshot().binaryGoalRecords.isEmpty())
    }

    @Test fun concurrentSameDateWritesCannotDuplicateRecords() = runBlocking {
        val id = repository.saveBinaryGoal(BinaryGoal(name = "Read"))
        coroutineScope { (1..24).map { async { repository.setBinaryGoalRecord(id, "2024-12-31", it % 2) } }.awaitAll() }
        assertEquals(1, repository.snapshot().binaryGoalRecords.size)
        assertTrue(repository.audit().isEmpty())
    }

    @Test fun goalWithHistoryCannotBeDeletedAndArchiveDoesNotLoseOutcomes() = runBlocking {
        val id = repository.saveBinaryGoal(BinaryGoal(name = "Read"))
        repository.setBinaryGoalRecord(id, "2024-01-01", 1)
        val recorded = repository.snapshot().binaryGoalRecords
        rejects { repository.deleteBinaryGoalIfUnused(id) }
        val goal = repository.snapshot().binaryGoals.single()
        repository.saveBinaryGoal(goal.copy(isArchived = true))
        assertEquals(recorded, repository.snapshot().binaryGoalRecords)
        rejects { repository.setBinaryGoalRecord(id, "2024-01-02", 1) }
        repository.setBinaryGoalRecord(id, "2024-01-01", 0)
        assertEquals(0, repository.snapshot().binaryGoalRecords.single().value)
        repository.setBinaryGoalRecord(id, "2024-01-01", null)
        repository.deleteBinaryGoalIfUnused(id)
        assertTrue(repository.snapshot().binaryGoals.isEmpty())
    }

    @Test fun reorderIsAtomicAndMoveUsesPeersIncludingDuplicateSortOrders() = runBlocking {
        val a = repository.saveBinaryGoal(BinaryGoal(name = "A"))
        val b = repository.saveBinaryGoal(BinaryGoal(name = "B"))
        val archived = repository.saveBinaryGoal(BinaryGoal(name = "Archived", isArchived = true))
        repository.moveBinaryGoal(b, -1)
        assertEquals(listOf(b, a), repository.snapshot().binaryGoals.filterNot { it.isArchived }.map { it.id })
        assertEquals(0, repository.snapshot().binaryGoals.single { it.id == archived }.sortOrder)
        repository.moveBinaryGoal(b, -1)
        assertEquals(b, repository.snapshot().binaryGoals.filterNot { it.isArchived }.first().id)
        val before = repository.snapshot()
        rejects { repository.reorderBinaryGoals(listOf(a, Long.MAX_VALUE)) }
        assertEquals(before, repository.snapshot())
        repository.reorderBinaryGoals(listOf(a, b))
        assertEquals(listOf(a, b), repository.snapshot().binaryGoals.filterNot { it.isArchived }.map { it.id })
    }

    @Test fun invalidAndFutureInputLeavesDatabaseUntouched() = runBlocking {
        val id = repository.saveBinaryGoal(BinaryGoal(name = "Read"))
        val before = repository.snapshot()
        rejects { repository.saveBinaryGoal(BinaryGoal(name = " ")) }
        rejects { repository.saveBinaryGoal(BinaryGoal(name = "Read", icon = "")) }
        rejects { repository.saveBinaryGoal(BinaryGoal(name = "Read", sortOrder = -1)) }
        rejects { repository.saveBinaryGoal(BinaryGoal(id = Long.MAX_VALUE, name = "Missing")) }
        rejects { repository.setBinaryGoalRecord(id, "2024-01-01", 2) }
        rejects { repository.setBinaryGoalRecord(id, LocalDate.now().plusDays(2).toString(), 1) }
        rejects { repository.setBinaryGoalRecord(Long.MAX_VALUE, "2024-01-01", 1) }
        try { repository.setBinaryGoalRecord(id, "2023-02-29", 1); fail("Invalid calendar date") } catch (_: java.time.DateTimeException) { }
        assertEquals(before, repository.snapshot())
    }

    @Test fun databaseAlsoEnforcesUniqueGoalDateAndRestrictsDeletion() = runBlocking {
        val id = repository.saveBinaryGoal(BinaryGoal(name = "Read"))
        repository.setBinaryGoalRecord(id, "2024-01-01", 1)
        val sql = database.openHelper.writableDatabase
        try {
            sql.execSQL("INSERT INTO binary_goal_records (goalId,date,value,createdAt,updatedAt) VALUES (?, '2024-01-01', 0, 0, 0)", arrayOf(id))
            fail("Must reject duplicate date")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        try { database.journalDao().deleteBinaryGoal(id); fail("Must preserve recorded history") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(1, repository.snapshot().binaryGoalRecords.size)
    }

    @Test fun invalidRestoreRollsBackAndValidRestoreReplacesAllBinaryRows() = runBlocking {
        val id = repository.saveBinaryGoal(BinaryGoal(name = "Read"))
        repository.setBinaryGoalRecord(id, "2024-01-01", 1)
        val before = repository.snapshot()
        rejects { repository.replaceAll(before.copy(binaryGoalRecords = before.binaryGoalRecords.map { it.copy(value = 9) })) }
        assertEquals(before, repository.snapshot())
        repository.replaceAll(before.copy(binaryGoals = emptyList(), binaryGoalRecords = emptyList()))
        assertTrue(repository.snapshot().binaryGoals.isEmpty())
        repository.replaceAll(before)
        assertEquals(before, repository.snapshot())
    }

    @Test fun importedClockRollbackAndFutureDatesRemainEditableWithoutTimestampRegression() = runBlocking {
        val id = repository.saveBinaryGoal(BinaryGoal(name = "Read"))
        val futureTimestamp = java.time.Instant.parse("2099-01-01T00:00:00Z").toEpochMilli()
        val data = repository.snapshot()
        repository.replaceAll(data.copy(binaryGoals = data.binaryGoals.map { it.copy(createdAt = futureTimestamp, updatedAt = futureTimestamp) },
            binaryGoalRecords = listOf(BinaryGoalRecord(1, id, "2099-01-01", 1, futureTimestamp, futureTimestamp))))
        repository.saveBinaryGoal(repository.snapshot().binaryGoals.single().copy(name = "Still editable"))
        repository.setBinaryGoalRecord(id, "2099-01-01", 0)
        val result = repository.snapshot()
        assertTrue(result.binaryGoals.single().updatedAt > futureTimestamp)
        assertTrue(result.binaryGoalRecords.single().updatedAt > futureTimestamp)
        assertTrue(repository.audit().isEmpty())
    }

    private suspend fun rejects(block: suspend () -> Unit) { try { block(); fail("Expected validation failure") } catch (_: IllegalArgumentException) { } }
}
