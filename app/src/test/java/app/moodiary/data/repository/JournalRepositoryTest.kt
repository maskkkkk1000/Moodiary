package app.moodiary.data.repository

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import app.moodiary.core.database.MoodiaryDatabase
import app.moodiary.domain.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class JournalRepositoryTest {
    private lateinit var database: MoodiaryDatabase
    private lateinit var repository: JournalRepository
    private lateinit var originalZone: TimeZone

    @Before fun setup() = runBlocking {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoodiaryDatabase::class.java).build()
        repository = JournalRepository(database, app.moodiary.core.backup.RestoreState(ApplicationProvider.getApplicationContext()))
        repository.seedDefaults()
    }

    @After fun cleanup() {
        database.close()
        TimeZone.setDefault(originalZone)
    }

    private suspend fun newEntry(at: String = "2026-01-05T12:00:00Z", id: Long = 0, note: String = "Reflection"): Entry {
        val mood = repository.snapshot().moods.first()
        return Entry(id = id, timestamp = Instant.parse(at).toEpochMilli(), moodId = mood.id, note = note,
            moodName = mood.name, moodScore = mood.score, moodColor = mood.color, moodIcon = mood.icon)
    }

    private suspend fun linkedGoal(activityId: Long, type: GoalType = GoalType.DAILY, weekdays: Set<Int> = emptySet()): Long =
        repository.saveGoal(Goal(name = "Movement", linkedActivityId = activityId, goalType = type, startDate = "2026-01-01"), weekdays)

    @Test fun seedIsIdempotentAndDoesNotOverwriteCustomization() = runBlocking {
        val first = repository.snapshot()
        repository.saveMood(first.moods.first().copy(name = "My mood"))
        repository.seedDefaults()
        val second = repository.snapshot()
        assertEquals(first.moods.size, second.moods.size)
        assertEquals(first.activities.size, second.activities.size)
        assertEquals("My mood", second.moods.first().name)
        assertTrue(repository.audit().isEmpty())
    }

    @Test fun multipleEntriesHaveIndependentRelationsAndCascadeOwnedRows() = runBlocking {
        val activities = repository.snapshot().activities.take(2).map { it.id }.toSet()
        val first = repository.saveEntry(newEntry(), activities, listOf(EntryPhoto(entryId = 0, localPath = "first.img")))
        val second = repository.saveEntry(newEntry(), emptySet(), emptyList())
        assertNotEquals(first, second)
        assertEquals(2, repository.entriesForDay(LocalDate.parse("2026-01-05")).size)
        assertEquals(2, repository.snapshot().entryActivities.size)
        repository.deleteEntry(first)
        val remaining = repository.snapshot()
        assertEquals(listOf(second), remaining.entries.map { it.id })
        assertTrue(remaining.entryActivities.isEmpty())
        assertTrue(remaining.photos.isEmpty())
        assertEquals(11, remaining.activities.size)
    }

    @Test fun editingOrArchivingMoodPreservesHistoricalMeaningAndRelations() = runBlocking {
        val before = repository.snapshot()
        val mood = before.moods.first()
        val activity = before.activities.first()
        val entryId = repository.saveEntry(newEntry(), setOf(activity.id), emptyList())
        repository.saveMood(mood.copy(name = "Renamed", score = -5.0, icon = "🥳", isArchived = true))
        repository.saveActivity(activity.copy(name = "Renamed activity", icon = "material:book", color = 0xFF275F8A, isArchived = true))
        val old = repository.snapshot().entries.single()
        repository.saveEntry(old.copy(note = "Edited note", moodName = "Wrong", moodScore = 999.0), setOf(activity.id), emptyList())
        val stored = repository.snapshot()
        assertEquals(entryId, stored.entries.single().id)
        assertEquals(mood.name, stored.entries.single().moodName)
        assertEquals(mood.score, stored.entries.single().moodScore, 0.0)
        assertEquals(activity.id, stored.entryActivities.single().activityId)
        assertEquals(mood.icon, stored.entries.single().moodIcon)
        assertEquals("🥳", stored.moods.single { it.id == mood.id }.icon)
        assertEquals("material:book", stored.activities.single { it.id == activity.id }.icon)
        assertEquals(0xFF275F8A, stored.activities.single { it.id == activity.id }.color)
        assertEquals("Edited note", stored.entries.single().note)
    }

    @Test fun changedMoodSnapshotsCurrentDefinition() = runBlocking {
        val id = repository.saveEntry(newEntry(), emptySet(), emptyList())
        val another = repository.snapshot().moods[1]
        repository.saveMood(another.copy(name = "Restful", score = 7.0))
        repository.saveEntry(repository.snapshot().entries.single().copy(moodId = another.id), emptySet(), emptyList())
        val stored = repository.snapshot().entries.single()
        assertEquals(id, stored.id)
        assertEquals("Restful", stored.moodName)
        assertEquals(7.0, stored.moodScore, 0.0)
    }

    @Test fun photoConflictRollsBackEntryEditAndRelationships() = runBlocking {
        val activities = repository.snapshot().activities.take(2).map { it.id }
        repository.saveEntry(newEntry(note = "Original one"), setOf(activities[0]), listOf(EntryPhoto(entryId = 0, localPath = "shared.img")))
        val otherId = repository.saveEntry(newEntry(note = "Original two"), setOf(activities[1]), emptyList())
        val before = repository.snapshot()
        try {
            repository.saveEntry(before.entries.single { it.id == otherId }.copy(note = "Must roll back"), emptySet(), listOf(EntryPhoto(entryId = 0, localPath = "shared.img")))
            fail("Duplicate managed media ownership should fail")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(before, repository.snapshot())
    }

    @Test fun automaticCompletionIsIdempotentAndRecalculatesEditedDatesAndDeletion() = runBlocking {
        val activity = repository.snapshot().activities.first().id
        val goal = linkedGoal(activity)
        val first = repository.saveEntry(newEntry(), setOf(activity), emptyList())
        val second = repository.saveEntry(newEntry(), setOf(activity), emptyList())
        assertEquals(1, repository.snapshot().completions.size)
        repository.deleteEntry(first)
        assertEquals("2026-01-05", repository.snapshot().completions.single().date)
        val entry = repository.snapshot().entries.single()
        repository.saveEntry(entry.copy(timestamp = Instant.parse("2026-01-06T12:00:00Z").toEpochMilli()), setOf(activity), emptyList())
        assertEquals("2026-01-06", repository.snapshot().completions.single().date)
        assertEquals(goal, repository.snapshot().completions.single().goalId)
        repository.deleteEntry(second)
        assertTrue(repository.snapshot().completions.isEmpty())
    }

    @Test fun manualCompletionSurvivesRemovalOfActivityAndScheduleChanges() = runBlocking {
        val activity = repository.snapshot().activities.first().id
        val goalId = linkedGoal(activity)
        val entry = repository.saveEntry(newEntry(), setOf(activity), emptyList())
        repository.setManualCompletion(goalId, "2026-01-05", true)
        repository.deleteEntry(entry)
        assertEquals(CompletionSource.MANUAL, repository.snapshot().completions.single().source)
        repository.saveGoal(repository.snapshot().goals.single().copy(goalType = GoalType.WEEKDAYS), setOf(2))
        assertEquals("2026-01-05", repository.snapshot().completions.single().date)
        assertTrue(repository.audit().isEmpty())
    }

    @Test fun clearingManualCompletionRestoresActivityBackedCompletion() = runBlocking {
        val activity = repository.snapshot().activities.first().id
        val goalId = linkedGoal(activity)
        repository.saveEntry(newEntry(), setOf(activity), emptyList())
        repository.setManualCompletion(goalId, "2026-01-05", true)
        repository.setManualCompletion(goalId, "2026-01-05", false)
        assertEquals(CompletionSource.LINKED_ACTIVITY, repository.snapshot().completions.single().source)
    }

    @Test fun weekdaysAndTimezoneReconciliationUseLocalDate() = runBlocking {
        val activity = repository.snapshot().activities.first().id
        linkedGoal(activity, GoalType.WEEKDAYS, setOf(1))
        repository.saveEntry(newEntry(at = "2026-01-06T01:00:00Z"), setOf(activity), emptyList())
        assertTrue(repository.snapshot().completions.isEmpty())
        repository.recalculateLinkedCompletions(ZoneId.of("America/Los_Angeles"))
        assertEquals("2026-01-05", repository.snapshot().completions.single().date)
        repository.recalculateLinkedCompletions(ZoneId.of("UTC"))
        assertTrue(repository.snapshot().completions.isEmpty())
    }

    @Test fun concurrentEntryWritesCannotDuplicateAutomaticCompletion() = runBlocking {
        val activity = repository.snapshot().activities.first().id
        linkedGoal(activity)
        val draft = newEntry()
        coroutineScope { (1..8).map { async { repository.saveEntry(draft, setOf(activity), emptyList()) } }.awaitAll() }
        assertEquals(8, repository.snapshot().entries.size)
        assertEquals(1, repository.snapshot().completions.size)
    }

    @Test fun invalidRestoreLeavesEntireJournalUnchanged() = runBlocking {
        repository.saveEntry(newEntry(), emptySet(), emptyList())
        val before = repository.snapshot()
        val invalid = before.copy(entries = before.entries.map { it.copy(moodId = 987654321) })
        try { repository.replaceAll(invalid); fail("Orphan mood reference should fail") } catch (_: IllegalArgumentException) { }
        assertEquals(before, repository.snapshot())
    }

    @Test fun restoreRoundTripRetainsAllTablesAndNestedExclusiveDoesNotDeadlock() = runBlocking {
        val activity = repository.snapshot().activities.first().id
        val goalId = linkedGoal(activity)
        repository.saveEntry(newEntry(), setOf(activity), listOf(EntryPhoto(entryId = 0, localPath = "roundtrip.img")))
        repository.saveReminder(Reminder(type = ReminderType.GOAL, targetId = goalId, daysOfWeek = setOf(1, 3, 5)))
        repository.saveImportantDay(ImportantDay(date = "2026-02-14", title = "A memorable day"))
        val backup = repository.snapshot()
        repository.saveEntry(newEntry(note = "Will be replaced"), emptySet(), emptyList())
        withTimeout(10_000) {
            repository.withExclusive {
                repository.replaceAll(backup)
                assertEquals(backup, repository.snapshot())
            }
        }
        assertTrue(repository.audit().isEmpty())
    }

    @Test fun indexedDateRangeHandlesDstDayAndLeapDay() = runBlocking {
        val zone = ZoneId.of("America/New_York")
        repository.saveEntry(newEntry("2024-03-10T05:00:00Z"), emptySet(), emptyList())
        repository.saveEntry(newEntry("2024-03-11T03:59:59Z"), emptySet(), emptyList())
        repository.saveEntry(newEntry("2024-03-11T04:00:00Z"), emptySet(), emptyList())
        repository.saveEntry(newEntry("2024-02-29T15:00:00Z"), emptySet(), emptyList())
        assertEquals(2, repository.entriesForDay(LocalDate.parse("2024-03-10"), zone).size)
        assertEquals(1, repository.entriesForDay(LocalDate.parse("2024-02-29"), zone).size)
    }

    @Test fun keysetPaginationIncludesSameTimestampOnceEach() = runBlocking {
        repeat(5) { repository.saveEntry(newEntry(), emptySet(), emptyList()) }
        val first = repository.entriesPage(limit = 2)
        val second = repository.entriesPage(first.last().timestamp, first.last().id, 2)
        val third = repository.entriesPage(second.last().timestamp, second.last().id, 2)
        assertEquals(5, (first + second + third).map { it.id }.distinct().size)
        assertEquals(repository.snapshot().entries.map { it.id }, (first + second + third).map { it.id })
    }

    @Test fun sqliteEnforcesMoodReferenceAndRollsBackMultiStatementTransaction() = runBlocking {
        repository.saveEntry(newEntry(), emptySet(), emptyList())
        val before = repository.snapshot()
        try {
            database.withTransaction {
                database.journalDao().clearEntries()
                database.journalDao().clearMoods()
                database.openHelper.writableDatabase.execSQL("INSERT INTO entries (timestamp, moodId, note, moodName, moodScore, moodColor, moodIcon, createdAt, updatedAt) VALUES (1, 999, '', '', 1, 1, '', 1, 1)")
            }
            fail("SQLite must reject orphan entry")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(before, repository.snapshot())
    }
    @Test fun staleEditorRevisionIsRejectedInsideMutation() = runBlocking {
        repository.saveEntry(newEntry(), emptySet(), emptyList())
        val old = repository.snapshot().entries.single()
        repository.saveEntry(old.copy(note = "newer"), emptySet(), emptyList(), old.updatedAt)
        try { repository.saveEntry(old.copy(note = "stale"), emptySet(), emptyList(), old.updatedAt); fail("Reject stale editor") } catch (_: IllegalArgumentException) { }
        assertEquals("newer", repository.snapshot().entries.single().note)
    }
    @Test fun clockRollbackKeepsEveryMutableTimestampValidForBackup() = runBlocking {
        repository.saveEntry(newEntry(), emptySet(), emptyList())
        repository.saveGoal(Goal(name = "Practice", startDate = "2026-01-01"), emptySet())
        repository.saveReminder(Reminder())
        val future = Instant.parse("2099-01-01T00:00:00Z").toEpochMilli()
        val before = repository.snapshot()
        repository.replaceAll(before.copy(
            entries = before.entries.map { it.copy(createdAt = future, updatedAt = future) },
            moods = before.moods.map { it.copy(createdAt = future, updatedAt = future) },
            groups = before.groups.map { it.copy(createdAt = future, updatedAt = future) },
            activities = before.activities.map { it.copy(createdAt = future, updatedAt = future) },
            goals = before.goals.map { it.copy(createdAt = future, updatedAt = future) },
            reminders = before.reminders.map { it.copy(createdAt = future, updatedAt = future) }))
        val data = repository.snapshot()
        repository.saveEntry(data.entries.single(), emptySet(), emptyList())
        repository.saveMood(data.moods.first()); repository.saveGroup(data.groups.first())
        repository.saveActivity(data.activities.first()); repository.saveGoal(data.goals.single(), emptySet())
        repository.saveReminder(data.reminders.single())
        val result = repository.snapshot()
        assertTrue(result.entries.single().updatedAt > future)
        assertTrue(result.moods.first().updatedAt > future)
        assertTrue(result.groups.first().updatedAt > future)
        assertTrue(result.activities.first().updatedAt > future)
        assertTrue(result.goals.single().updatedAt > future)
        assertTrue(result.reminders.single().updatedAt > future)
        assertTrue(DataValidation.validate(result).isEmpty())
    }
    @Test fun importedDateRangeRemainsEditable() = runBlocking {
        repository.saveEntry(newEntry("0001-01-01T12:00:00Z"), emptySet(), emptyList())
        repository.saveEntry(newEntry("9999-12-31T12:00:00Z"), emptySet(), emptyList())
        repository.saveGoal(Goal(name = "Long history", startDate = "0001-01-01", endDate = "9999-12-31"), emptySet())
        repository.saveImportantDay(ImportantDay(date = "0001-01-01", title = "Start"))
        assertTrue(DataValidation.validate(repository.snapshot()).isEmpty())
    }

}
