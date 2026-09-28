package app.moodiary.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class LinkedActivityMoment(val activityId: Long, val timestamp: Long)

/** All multi-table writes are coordinated by JournalRepository in one Room transaction. */
@Dao
interface JournalDao {
    @Query("SELECT * FROM moods ORDER BY sortOrder, id") suspend fun moods(): List<MoodEntity>
    @Query("SELECT * FROM activity_groups ORDER BY sortOrder, id") suspend fun groups(): List<ActivityGroupEntity>
    @Query("SELECT * FROM activities ORDER BY sortOrder, id") suspend fun activities(): List<ActivityEntity>
    @Query("SELECT * FROM entries ORDER BY timestamp DESC, id DESC") suspend fun entries(): List<EntryEntity>
    @Query("SELECT * FROM entry_activities ORDER BY entryId, activityId") suspend fun entryActivities(): List<EntryActivityEntity>
    @Query("SELECT * FROM entry_photos ORDER BY entryId, sortOrder, id") suspend fun photos(): List<EntryPhotoEntity>
    @Query("SELECT * FROM goals ORDER BY createdAt, id") suspend fun goals(): List<GoalEntity>
    @Query("SELECT * FROM goal_schedules ORDER BY goalId, dayOfWeek") suspend fun schedules(): List<GoalScheduleEntity>
    @Query("SELECT * FROM goal_completions ORDER BY date, goalId") suspend fun completions(): List<GoalCompletionEntity>
    @Query("SELECT * FROM reminders ORDER BY hour, minute, id") suspend fun reminders(): List<ReminderEntity>
    @Query("SELECT * FROM note_templates ORDER BY sortOrder, id") suspend fun templates(): List<NoteTemplateEntity>
    @Query("SELECT * FROM important_days ORDER BY date, id") suspend fun importantDays(): List<ImportantDayEntity>
    @Query("SELECT * FROM entries WHERE id = :id") suspend fun entry(id: Long): EntryEntity?
    @Query("SELECT * FROM moods WHERE id = :id") suspend fun mood(id: Long): MoodEntity?
    @Query("SELECT * FROM activity_groups WHERE id = :id") suspend fun group(id: Long): ActivityGroupEntity?
    @Query("SELECT * FROM activities WHERE id = :id") suspend fun activity(id: Long): ActivityEntity?
    @Query("SELECT * FROM goals WHERE id = :id") suspend fun goal(id: Long): GoalEntity?
    @Query("SELECT * FROM reminders WHERE id = :id") suspend fun reminder(id: Long): ReminderEntity?
    @Query("SELECT * FROM entry_photos WHERE entryId = :id ORDER BY sortOrder, id") suspend fun entryPhotos(id: Long): List<EntryPhotoEntity>
    @Query("SELECT * FROM entry_activities WHERE entryId = :id") suspend fun activitiesForEntry(id: Long): List<EntryActivityEntity>
    @Query("SELECT * FROM goal_completions WHERE goalId = :goalId AND date = :date") suspend fun completion(goalId: Long, date: String): GoalCompletionEntity?
    @Query("SELECT ea.activityId, e.timestamp FROM entry_activities ea INNER JOIN entries e ON e.id = ea.entryId WHERE ea.activityId IN (:activityIds)")
    suspend fun linkedActivityMoments(activityIds: List<Long>): List<LinkedActivityMoment>
    @Query("SELECT * FROM goal_completions WHERE goalId IN (:goalIds)")
    suspend fun completionsForGoals(goalIds: List<Long>): List<GoalCompletionEntity>
    @Query("SELECT value FROM repository_metadata WHERE `key` = :key") suspend fun metadata(key: String): String?

    /** Half-open instant interval avoids end-of-day rounding and DST assumptions. */
    @Query("SELECT * FROM entries WHERE timestamp >= :fromInclusive AND timestamp < :untilExclusive ORDER BY timestamp DESC, id DESC")
    fun observeEntries(fromInclusive: Long, untilExclusive: Long): Flow<List<EntryEntity>>
    @Query("SELECT * FROM entries WHERE timestamp >= :fromInclusive AND timestamp < :untilExclusive ORDER BY timestamp DESC, id DESC")
    suspend fun entriesInRange(fromInclusive: Long, untilExclusive: Long): List<EntryEntity>
    /** Keyset pagination remains stable when new entries arrive before the displayed page. */
    @Query("SELECT * FROM entries WHERE timestamp < :beforeTimestamp OR (timestamp = :beforeTimestamp AND id < :beforeId) ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun entriesPage(beforeTimestamp: Long, beforeId: Long, limit: Int): List<EntryEntity>

    // Upsert performs UPDATE on existing primary keys: REPLACE would cascade-delete history.
    @Upsert suspend fun upsert(value: MoodEntity): Long
    @Upsert suspend fun upsert(value: ActivityGroupEntity): Long
    @Upsert suspend fun upsert(value: ActivityEntity): Long
    @Upsert suspend fun upsert(value: EntryEntity): Long
    @Upsert suspend fun upsert(value: GoalEntity): Long
    @Upsert suspend fun upsert(value: ReminderEntity): Long
    @Upsert suspend fun upsert(value: NoteTemplateEntity): Long
    @Upsert suspend fun upsert(value: ImportantDayEntity): Long
    @Upsert suspend fun upsert(value: GoalCompletionEntity): Long
    @Upsert suspend fun upsert(value: RepositoryMetadataEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertEntryActivities(values: List<EntryActivityEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPhotos(values: List<EntryPhotoEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSchedules(values: List<GoalScheduleEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCompletions(values: List<GoalCompletionEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMoods(values: List<MoodEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertGroups(values: List<ActivityGroupEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertActivities(values: List<ActivityEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertEntries(values: List<EntryEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertGoals(values: List<GoalEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReminders(values: List<ReminderEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTemplates(values: List<NoteTemplateEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertImportantDays(values: List<ImportantDayEntity>)

    @Query("DELETE FROM entries WHERE id = :id") suspend fun deleteEntry(id: Long)
    @Query("DELETE FROM entry_activities WHERE entryId = :id") suspend fun clearEntryActivities(id: Long)
    @Query("DELETE FROM entry_photos WHERE entryId = :id") suspend fun clearEntryPhotos(id: Long)
    @Query("DELETE FROM goal_schedules WHERE goalId = :id") suspend fun clearGoalSchedules(id: Long)
    @Query("DELETE FROM goal_completions WHERE id = :id") suspend fun deleteCompletion(id: Long)
    @Query("DELETE FROM reminders WHERE id = :id") suspend fun deleteReminder(id: Long)
    @Query("DELETE FROM note_templates WHERE id = :id") suspend fun deleteTemplate(id: Long)
    @Query("DELETE FROM important_days WHERE id = :id") suspend fun deleteImportantDay(id: Long)

    @Query("DELETE FROM entry_activities") suspend fun clearEntryActivities()
    @Query("DELETE FROM entry_photos") suspend fun clearPhotos()
    @Query("DELETE FROM goal_completions") suspend fun clearCompletions()
    @Query("DELETE FROM goal_schedules") suspend fun clearSchedules()
    @Query("DELETE FROM reminders") suspend fun clearReminders()
    @Query("DELETE FROM entries") suspend fun clearEntries()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM activities") suspend fun clearActivities()
    @Query("DELETE FROM activity_groups") suspend fun clearGroups()
    @Query("DELETE FROM moods") suspend fun clearMoods()
    @Query("DELETE FROM note_templates") suspend fun clearTemplates()
    @Query("DELETE FROM important_days") suspend fun clearImportantDays()
}
