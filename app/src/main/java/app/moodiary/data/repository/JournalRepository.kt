package app.moodiary.data.repository

import androidx.room.withTransaction
import app.moodiary.core.database.*
import app.moodiary.data.mapper.*
import app.moodiary.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Single entry point for journal mutations and consistent backup snapshots.
 * Media files are owned by PhotoStore; this class stores only validated managed filenames.
 */
@Singleton
class JournalRepository @Inject constructor(private val database: MoodiaryDatabase, private val restoreState: app.moodiary.core.backup.RestoreState) {
    private val dao = database.journalDao()
    private val writeMutex = Mutex()

    private class ExclusiveOwner(val repository: JournalRepository) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<ExclusiveOwner>
    }

    /** Reentrant for a sequential backup/restore operation which calls repository methods itself.
     * Do not launch concurrent child mutations inside this block.
     */
    suspend fun <T> withExclusive(block: suspend () -> T): T {
        if (currentCoroutineContext()[ExclusiveOwner]?.repository === this) return block()
        return writeMutex.withLock { withContext(Dispatchers.IO + ExclusiveOwner(this)) { block() } }
    }

    val data: Flow<JournalData> = database.invalidationTracker
        .createFlow(*MoodiaryDatabase.DATA_TABLES, emitInitialState = true)
        .map { snapshot() }.distinctUntilChanged().flowOn(Dispatchers.IO)

    suspend fun snapshot(): JournalData = withExclusive { database.withTransaction { readSnapshot() } }

    /** Narrow reads for background scheduling avoid materializing years of journal notes. */
    suspend fun reminders(): List<Reminder> = dao.reminders().map { it.model() }
    suspend fun goals(): List<Goal> = dao.goals().map { it.model() }
    suspend fun reminderState(): Pair<List<Reminder>, List<Goal>> = withExclusive {
        requireAvailable()
        database.withTransaction { reminders() to goals() }
    }

    private suspend fun readSnapshot() = JournalData(
        moods = dao.moods().map { it.model() }, groups = dao.groups().map { it.model() },
        activities = dao.activities().map { it.model() }, entries = dao.entries().map { it.model() },
        entryActivities = dao.entryActivities().map { it.model() }, photos = dao.photos().map { it.model() },
        goals = dao.goals().map { it.model() }, schedules = dao.schedules().map { it.model() },
        completions = dao.completions().map { it.model() }, reminders = dao.reminders().map { it.model() },
        templates = dao.templates().map { it.model() }, importantDays = dao.importantDays().map { it.model() },
        binaryGoals = dao.binaryGoals().map { it.model() }, binaryGoalRecords = dao.binaryGoalRecords().map { it.model() }
    )

    fun observeEntries(from: LocalDate, through: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<List<Entry>> {
        require(through >= from) { "End date must not precede start date" }
        return dao.observeEntries(from.atStartOfDay(zone).toInstant().toEpochMilli(), through.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            .map { rows -> rows.map { it.model() } }
    }

    suspend fun entriesForDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Entry> = withContext(Dispatchers.IO) {
        dao.entriesInRange(date.atStartOfDay(zone).toInstant().toEpochMilli(), date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()).map { it.model() }
    }

    suspend fun entriesPage(beforeTimestamp: Long = Long.MAX_VALUE, beforeId: Long = Long.MAX_VALUE, limit: Int = 100): List<Entry> {
        require(limit in 1..1000) { "Page size must be between 1 and 1000" }
        return dao.entriesPage(beforeTimestamp, beforeId, limit).map { it.model() }
    }

    suspend fun saveEntry(entry: Entry, activityIds: Set<Long>, photos: List<EntryPhoto>, expectedUpdatedAt: Long? = null): Long = mutate {
        require(entry.id >= 0) { "Invalid entry ID" }
        require(entry.note.length <= 1_000_000) { "Note is too long" }
        require(entry.timestamp in MIN_TIMESTAMP..MAX_TIMESTAMP) { "Entry date must be between years 0001 and 9999" }
        val previous = if (entry.id == 0L) null else requireNotNull(dao.entry(entry.id)) { "This entry no longer exists" }
        require(expectedUpdatedAt == null || previous?.updatedAt == expectedUpdatedAt) {
            "This entry changed after the draft was opened. Copy your note and reopen the entry to avoid overwriting newer data."
        }
        val mood = requireNotNull(dao.mood(entry.moodId)) { "Choose an available mood" }
        require(!mood.isArchived || previous?.moodId == mood.id) { "Choose an active mood for a new entry" }
        val oldActivityIds = previous?.let { dao.activitiesForEntry(it.id).mapTo(HashSet()) { row -> row.activityId } }.orEmpty()
        activityIds.forEach { id ->
            val activity = requireNotNull(dao.activity(id)) { "A selected activity no longer exists" }
            require(!activity.isArchived || id in oldActivityIds) { "Choose active activities" }
        }
        require(photos.size <= 30) { "An entry supports up to 30 photos" }
        require(photos.map { it.localPath }.distinct().size == photos.size) { "Duplicate photo attachment" }
        photos.forEach { requireManagedPath(it.localPath) }
        val oldPhotos = previous?.let { dao.entryPhotos(it.id) }.orEmpty().associateBy { it.localPath }
        // Never trust a stale editor's mood snapshot when the chosen mood has changed.
        val timestamp = nextUpdate(previous?.createdAt, previous?.updatedAt)
        val stable = entry.copy(
            moodName = if (previous?.moodId == mood.id) previous.moodName else mood.name,
            moodScore = if (previous?.moodId == mood.id) previous.moodScore else mood.score,
            moodColor = if (previous?.moodId == mood.id) previous.moodColor else mood.color,
            moodIcon = if (previous?.moodId == mood.id) previous.moodIcon else mood.icon,
            createdAt = previous?.createdAt ?: timestamp, updatedAt = timestamp
        )
        val saved = dao.upsert(stable.entity())
        val id = if (entry.id == 0L) saved else entry.id
        dao.clearEntryActivities(id)
        dao.insertEntryActivities(activityIds.map { EntryActivityEntity(id, it) })
        dao.clearEntryPhotos(id)
        dao.insertPhotos(photos.mapIndexed { order, photo ->
            val old = oldPhotos[photo.localPath]
            EntryPhotoEntity(old?.id ?: 0, id, photo.localPath, order, old?.createdAt ?: timestamp)
        })
        reconcileLinked(activityIds = oldActivityIds + activityIds)
        id
    }

    suspend fun deleteEntry(id: Long) = mutate {
        val activityIds = dao.activitiesForEntry(id).mapTo(HashSet()) { it.activityId }
        dao.deleteEntry(id)
        reconcileLinked(activityIds = activityIds)
    }

    suspend fun saveMood(value: Mood): Long = mutate {
        requireName(value.name, "Mood")
        require(value.score.isFinite() && value.score in -1000.0..1000.0) { "Mood score must be between -1000 and 1000" }
        require(value.icon.isNotBlank() && value.icon.length <= 64) { "Choose a short mood icon" }
        val previous = existing(value.id) { dao.mood(value.id) }
        if (value.isArchived && previous?.isArchived != true) {
            require(dao.moods().any { it.id != value.id && !it.isArchived }) { "Keep at least one active mood" }
        }
        val now = nextUpdate(previous?.createdAt, previous?.updatedAt)
        val result = dao.upsert(value.copy(name = value.name.trim(), createdAt = previous?.createdAt ?: now, updatedAt = now).entity())
        if (value.id == 0L) result else value.id
    }

    suspend fun saveGroup(value: ActivityGroup): Long = mutate {
        requireName(value.name, "Group")
        val previous = existing(value.id) { dao.group(value.id) }
        val now = nextUpdate(previous?.createdAt, previous?.updatedAt)
        val result = dao.upsert(value.copy(name = value.name.trim(), createdAt = previous?.createdAt ?: now, updatedAt = now).entity())
        if (value.id == 0L) result else value.id
    }

    suspend fun saveActivity(value: Activity): Long = mutate {
        requireName(value.name, "Activity")
        require(value.icon.isNotBlank() && value.icon.length <= 64) { "Choose a short activity icon" }
        value.groupId?.let { requireNotNull(dao.group(it)) { "The selected group no longer exists" } }
        val previous = existing(value.id) { dao.activity(value.id) }
        val now = nextUpdate(previous?.createdAt, previous?.updatedAt)
        val result = dao.upsert(value.copy(name = value.name.trim(), createdAt = previous?.createdAt ?: now, updatedAt = now).entity())
        if (value.id == 0L) result else value.id
    }

    suspend fun saveGoal(value: Goal, weekdays: Set<Int>): Long = mutate {
        requireName(value.name, "Goal")
        val start = requireDate(value.startDate)
        value.endDate?.let { require(requireDate(it) >= start) { "End date must not precede start date" } }
        require(value.targetCount in 1..7) { "A goal can complete once per date; weekly targets must be from 1 to 7" }
        require(value.goalType == GoalType.WEEKLY_COUNT || value.targetCount == 1) { "Daily and weekday goals complete once per date" }
        require(weekdays.all { it in 1..7 }) { "Invalid weekday" }
        require(value.goalType != GoalType.WEEKDAYS || weekdays.isNotEmpty()) { "Select at least one weekday" }
        value.linkedActivityId?.let { requireNotNull(dao.activity(it)) { "The linked activity no longer exists" } }
        val previous = existing(value.id) { dao.goal(value.id) }
        val now = nextUpdate(previous?.createdAt, previous?.updatedAt)
        val result = dao.upsert(value.copy(name = value.name.trim(), createdAt = previous?.createdAt ?: now, updatedAt = now).entity())
        val id = if (value.id == 0L) result else value.id
        dao.clearGoalSchedules(id)
        dao.insertSchedules(weekdays.map { GoalScheduleEntity(id, it) })
        reconcileLinked(goalIds = setOf(id))
        id
    }

    suspend fun setManualCompletion(goalId: Long, date: String, completed: Boolean) = mutate {
        val day = requireDate(date)
        val goal = requireNotNull(dao.goal(goalId)) { "This goal no longer exists" }.model()
        require(GoalEngine.isScheduled(goal, dao.schedules().map { it.model() }, day)) { "This goal is not scheduled for this date" }
        val old = dao.completion(goalId, date)
        if (completed) {
            dao.upsert(GoalCompletionEntity(old?.id ?: 0, goalId, date, System.currentTimeMillis(), CompletionSource.MANUAL.name))
        } else if (old?.source == CompletionSource.MANUAL.name) {
            dao.deleteCompletion(old.id)
        }
        // Removing a manual tick does not falsify a completion backed by an existing activity.
        reconcileLinked(goalIds = setOf(goalId))
    }

    suspend fun saveBinaryGoal(value: BinaryGoal): Long = mutate {
        requireName(value.name, "Small goal")
        require(value.icon.isNotBlank() && value.icon.length <= 64) { "Choose a short goal icon" }
        require(value.description.length <= 1_000_000) { "Goal description is too long" }
        require(value.sortOrder >= 0) { "Goal order cannot be negative" }
        val previous = existing(value.id) { dao.binaryGoal(value.id) }
        val now = nextUpdate(previous?.createdAt, previous?.updatedAt)
        val result = dao.upsert(value.copy(name = value.name.trim(), createdAt = previous?.createdAt ?: now, updatedAt = now).entity())
        if (value.id == 0L) result else value.id
    }

    /** null removes the outcome (UNSET); 0 is an explicitly recorded failure, 1 success.
     * The repository lock and database unique index make concurrent same-day changes idempotent.
     */
    suspend fun setBinaryGoalRecord(goalId: Long, date: String, value: Int?) = mutate {
        val day = requireDate(date)
        require(value == null || value in 0..1) { "A small goal result must be success or failure" }
        val goal = requireNotNull(dao.binaryGoal(goalId)) { "This small goal no longer exists" }
        val previous = dao.binaryGoalRecord(goalId, date)
        if (value == null) {
            dao.deleteBinaryGoalRecord(goalId, date)
        } else {
            // Clock rollback/timezone changes must not make an existing outcome impossible to edit.
            require(previous != null || day <= LocalDate.now()) { "Cannot record a result for a future date" }
            require(previous != null || !goal.isArchived) { "Restore this small goal before recording another result" }
            val now = nextUpdate(previous?.createdAt, previous?.updatedAt)
            dao.upsert(BinaryGoalRecordEntity(previous?.id ?: 0, goalId, date, value, previous?.createdAt ?: now, now))
        }
        Unit
    }

    /** A goal with history may only be archived, never removed together with its outcomes. */
    suspend fun deleteBinaryGoalIfUnused(id: Long) = mutate {
        requireNotNull(dao.binaryGoal(id)) { "This small goal no longer exists" }
        require(!dao.hasBinaryGoalRecords(id)) { "This small goal has recorded results; archive it to preserve history" }
        dao.deleteBinaryGoal(id)
    }

    /** Atomic reorder of exactly the displayed goals; hidden archived goals keep their order. */
    suspend fun reorderBinaryGoals(ids: List<Long>) = mutate {
        require(ids.distinct().size == ids.size) { "Duplicate small goal in ordering" }
        ids.forEachIndexed { order, id ->
            val goal = requireNotNull(dao.binaryGoal(id)) { "This small goal no longer exists" }
            dao.upsert(goal.copy(sortOrder = order, updatedAt = nextUpdate(goal.createdAt, goal.updatedAt)))
        }
    }

    suspend fun moveBinaryGoal(id: Long, direction: Int) = mutate {
        require(direction == -1 || direction == 1) { "Move one position at a time" }
        val goal = requireNotNull(dao.binaryGoal(id)) { "This small goal no longer exists" }
        val peers = dao.binaryGoals().filter { it.isArchived == goal.isArchived }.toMutableList()
        val from = peers.indexOfFirst { it.id == id }
        val to = from + direction
        if (to in peers.indices) {
            java.util.Collections.swap(peers, from, to)
            peers.forEachIndexed { order, value ->
                dao.upsert(value.copy(sortOrder = order, updatedAt = nextUpdate(value.createdAt, value.updatedAt)))
            }
        }
    }

    suspend fun saveReminder(value: Reminder): Long = mutate {
        require(value.hour in 0..23 && value.minute in 0..59) { "Choose a valid reminder time" }
        require(value.daysOfWeek.isNotEmpty() && value.daysOfWeek.all { it in 1..7 }) { "Select valid reminder weekdays" }
        require(value.message.isNotBlank() && value.message.length <= 1000) { "Reminder message must contain 1 to 1000 characters" }
        if (value.type == ReminderType.GOAL) requireNotNull(value.targetId?.let { dao.goal(it) }) { "Choose a goal for this reminder" }
        val previous = existing(value.id) { dao.reminder(value.id) }
        val now = nextUpdate(previous?.createdAt, previous?.updatedAt)
        val result = dao.upsert(value.copy(targetId = value.targetId.takeIf { value.type == ReminderType.GOAL }, createdAt = previous?.createdAt ?: now, updatedAt = now).entity())
        if (value.id == 0L) result else value.id
    }

    suspend fun deleteReminder(id: Long) = mutate { dao.deleteReminder(id) }

    suspend fun saveTemplate(value: NoteTemplate): Long = mutate {
        requireName(value.name, "Template")
        require(value.content.length <= 1_000_000) { "Template is too long" }
        require(value.id >= 0) { "Invalid template ID" }
        val result = dao.upsert(value.copy(name = value.name.trim()).entity())
        if (value.id == 0L) result else value.id
    }

    suspend fun deleteTemplate(id: Long) = mutate { dao.deleteTemplate(id) }

    suspend fun saveImportantDay(value: ImportantDay): Long = mutate {
        requireName(value.title, "Important day")
        requireDate(value.date)
        require(value.id >= 0) { "Invalid important day ID" }
        require(value.note.length <= 1_000_000 && value.icon.length <= 64) { "Important day text is too long" }
        require(value.icon.isNotBlank()) { "Choose an icon for this important day" }
        val result = dao.upsert(value.copy(title = value.title.trim()).entity())
        if (value.id == 0L) result else value.id
    }

    suspend fun deleteImportantDay(id: Long) = mutate { dao.deleteImportantDay(id) }

    /** Validation happens before any DELETE. Foreign-key/uniqueness failures roll back the whole replacement. */
    suspend fun replaceAll(data: JournalData) = mutate {
        DataValidation.requireValid(data)
        dao.clearBinaryGoalRecords(); dao.clearBinaryGoals()
        dao.clearEntryActivities(); dao.clearPhotos(); dao.clearCompletions(); dao.clearSchedules()
        dao.clearReminders(); dao.clearEntries(); dao.clearGoals(); dao.clearActivities(); dao.clearGroups()
        dao.clearMoods(); dao.clearTemplates(); dao.clearImportantDays()
        dao.insertMoods(data.moods.map { it.entity() })
        dao.insertGroups(data.groups.map { it.entity() })
        dao.insertActivities(data.activities.map { it.entity() })
        dao.insertEntries(data.entries.map { it.entity() })
        dao.insertEntryActivities(data.entryActivities.map { it.entity() })
        dao.insertPhotos(data.photos.map { it.entity() })
        dao.insertGoals(data.goals.map { it.entity() })
        dao.insertSchedules(data.schedules.map { it.entity() })
        dao.insertCompletions(data.completions.map { it.entity() })
        dao.insertReminders(data.reminders.map { it.entity() })
        dao.insertTemplates(data.templates.map { it.entity() })
        dao.insertImportantDays(data.importantDays.map { it.entity() })
        dao.insertBinaryGoals(data.binaryGoals.map { it.entity() })
        dao.insertBinaryGoalRecords(data.binaryGoalRecords.map { it.entity() })
        dao.upsert(RepositoryMetadataEntity(SEEDED, "true"))
        reconcileLinked()
    }

    /** Call on foreground/timezone change. Instants follow the current zone; manual local dates stay fixed. */
    suspend fun recalculateLinkedCompletions(zone: ZoneId = ZoneId.systemDefault()) = mutate { reconcileLinked(zone = zone) }

    private suspend fun reconcileLinked(activityIds: Set<Long>? = null, goalIds: Set<Long>? = null, zone: ZoneId = ZoneId.systemDefault()) {
        // Changing timezone invalidates every automatic date, regardless of the current mutation's scope.
        val timezoneChanged = dao.metadata(ZONE) != zone.id
        val goals = dao.goals().map { it.model() }.filter {
            timezoneChanged || (goalIds?.contains(it.id) ?: (activityIds?.contains(it.linkedActivityId) ?: true))
        }
        if (goals.isEmpty()) return
        val schedules = dao.schedules().map { it.model() }
        val activityDates = goals.mapNotNull { it.linkedActivityId }.distinct().chunked(900)
            .flatMap { dao.linkedActivityMoments(it) }.groupBy { it.activityId }
            .mapValues { (_, moments) -> moments.mapTo(HashSet()) { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() } }
        val desired = goals.flatMap { goal -> activityDates[goal.linkedActivityId].orEmpty()
            .filter { GoalEngine.isScheduled(goal, schedules, it) }.map { GoalDate(goal.id, it.toString()) } }.toSet()
        val existing = goals.map { it.id }.chunked(900).flatMap { dao.completionsForGoals(it) }
        existing.filter { it.source == CompletionSource.LINKED_ACTIVITY.name && GoalDate(it.goalId, it.date) !in desired }.forEach { dao.deleteCompletion(it.id) }
        val existingKeys = existing.mapTo(HashSet()) { GoalDate(it.goalId, it.date) }
        dao.insertCompletions(desired.filter { it !in existingKeys }.map {
            GoalCompletionEntity(goalId = it.goalId, date = it.date, completedAt = System.currentTimeMillis(), source = CompletionSource.LINKED_ACTIVITY.name)
        })
        dao.upsert(RepositoryMetadataEntity(ZONE, zone.id))
    }

    suspend fun seedDefaults() = mutate {
        if (dao.metadata(SEEDED) != null) return@mutate
        // A populated pre-seed database is never overwritten.
        if (dao.moods().isEmpty() && dao.entries().isEmpty()) {
            val now = System.currentTimeMillis()
            listOf(
                Mood(name = "Bright", score = 5.0, color = 0xFF287967, icon = "☀", sortOrder = 0),
                Mood(name = "Good", score = 4.0, color = 0xFF53803B, icon = "☺", sortOrder = 1),
                Mood(name = "Steady", score = 3.0, color = 0xFF947323, icon = "◉", sortOrder = 2),
                Mood(name = "Low", score = 2.0, color = 0xFF9B604C, icon = "☁", sortOrder = 3),
                Mood(name = "Rough", score = 1.0, color = 0xFF935A80, icon = "☂", sortOrder = 4)
            ).forEach { dao.upsert(it.copy(createdAt = now, updatedAt = now).entity()) }
            val groups = listOf("People", "Movement", "Rest", "Interests", "Daily life")
            val groupIds = groups.mapIndexed { order, name -> dao.upsert(ActivityGroup(name = name, sortOrder = order).entity()) }
            listOf(
                Triple(0, "Friends", "♡"), Triple(0, "Family", "⌂"), Triple(1, "Walk", "↟"),
                Triple(1, "Exercise", "⚡"), Triple(2, "Good sleep", "☾"), Triple(2, "Quiet time", "◌"),
                Triple(3, "Reading", "▤"), Triple(3, "Music", "♫"), Triple(4, "Study", "✎"),
                Triple(4, "Work", "▣"), Triple(4, "Cooking", "♨")
            ).forEachIndexed { order, (group, name, icon) -> dao.upsert(Activity(groupId = groupIds[group], name = name, icon = icon, sortOrder = order).entity()) }
            dao.upsert(NoteTemplate(name = "Evening reflection", content = "A moment I want to remember:\n\nSomething that felt difficult:\n\nWhat I noticed:\n\nOne small intention for tomorrow:").entity())
        }
        dao.upsert(RepositoryMetadataEntity(SEEDED, "true"))
        dao.upsert(RepositoryMetadataEntity(ZONE, ZoneId.systemDefault().id))
    }

    /** Foreign keys are also enforced by SQLite. This audit adds domain-level checks for imported data. */
    suspend fun audit(): List<String> = withExclusive {
        val current = snapshot()
        val errors = DataValidation.validate(current).toMutableList()
        val desired = GoalEngine.desiredLinkedCompletions(current, ZoneId.systemDefault())
        val actual = current.completions.mapTo(HashSet()) { GoalDate(it.goalId, it.date) }
        current.completions.filter { it.source == CompletionSource.LINKED_ACTIVITY && GoalDate(it.goalId, it.date) !in desired }
            .forEach { errors += "Stale automatic completion for goal ${it.goalId} on ${it.date}" }
        (desired - actual).forEach { errors += "Missing linked completion for goal ${it.goalId} on ${it.date}" }
        database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            while (cursor.moveToNext()) errors += "Broken database reference in ${cursor.getString(0)}"
        }
        errors
    }

    private class RestoreAccess : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<RestoreAccess>
    }

    /** Only the validated restore/recovery service may mutate while a recovery marker exists. */
    internal suspend fun <T> withRestoreAccess(block: suspend () -> T): T = withExclusive {
        withContext(RestoreAccess()) { block() }
    }

    fun requireAvailable() = restoreState.requireAvailable()

    private suspend fun <T> mutate(block: suspend () -> T): T = withExclusive {
        if (currentCoroutineContext()[RestoreAccess] == null) requireAvailable()
        database.withTransaction { block() }
    }

    private suspend fun <T> existing(id: Long, read: suspend () -> T?): T? {
        require(id >= 0) { "Invalid record ID" }
        return if (id == 0L) null else requireNotNull(read()) { "This item no longer exists" }
    }

    private fun requireName(name: String, type: String) {
        require(name.isNotBlank() && name.length <= 200) { "$type name must contain 1 to 200 characters" }
    }

    private fun requireDate(value: String): LocalDate {
        val date = LocalDate.parse(value)
        require(date.toString() == value && date.year in 1..9999) { "Date must be between years 0001 and 9999" }
        return date
    }

    private fun requireManagedPath(value: String) {
        require(DataValidation.isManagedPhotoPath(value)) { "Invalid managed photo filename" }
    }

    /** updatedAt doubles as an optimistic revision token, even across clock rollback or rapid edits. */
    private fun nextUpdate(createdAt: Long?, updatedAt: Long?): Long {
        require(updatedAt == null || updatedAt < MAX_TIMESTAMP) { "This record has reached the maximum supported modification time" }
        val now = System.currentTimeMillis()
        return maxOf(now, createdAt ?: now, updatedAt?.plus(1) ?: now).also {
            require(it in MIN_TIMESTAMP..MAX_TIMESTAMP) { "The device clock is outside supported years 0001–9999" }
        }
    }

    companion object {
        private const val SEEDED = "defaults_seeded"
        private const val ZONE = "automatic_completion_zone"
        private const val MIN_TIMESTAMP = -62135596800000L
        private const val MAX_TIMESTAMP = 253402300799999L
    }
}
