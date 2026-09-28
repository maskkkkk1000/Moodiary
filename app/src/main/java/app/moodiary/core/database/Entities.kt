package app.moodiary.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "moods")
data class MoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val score: Double, val color: Long, val icon: String,
    val sortOrder: Int, val isArchived: Boolean, val createdAt: Long, val updatedAt: Long
)

@Entity(tableName = "activity_groups")
data class ActivityGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val sortOrder: Int, val isArchived: Boolean,
    val createdAt: Long, val updatedAt: Long
)

@Entity(tableName = "activities", foreignKeys = [
    ForeignKey(entity = ActivityGroupEntity::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.SET_NULL)
], indices = [Index("groupId")])
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long?, val name: String, val icon: String, val color: Long?,
    val sortOrder: Int, val isArchived: Boolean, val createdAt: Long, val updatedAt: Long
)

// Mood appearance and score are snapshots: customization must not rewrite history.
@Entity(tableName = "entries", foreignKeys = [
    ForeignKey(entity = MoodEntity::class, parentColumns = ["id"], childColumns = ["moodId"], onDelete = ForeignKey.RESTRICT)
], indices = [Index("timestamp"), Index("moodId")])
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long, val moodId: Long, val note: String,
    val moodName: String, val moodScore: Double, val moodColor: Long, val moodIcon: String,
    val createdAt: Long, val updatedAt: Long
)

@Entity(tableName = "entry_activities", primaryKeys = ["entryId", "activityId"], foreignKeys = [
    ForeignKey(entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ActivityEntity::class, parentColumns = ["id"], childColumns = ["activityId"], onDelete = ForeignKey.RESTRICT)
], indices = [Index("activityId")])
data class EntryActivityEntity(val entryId: Long, val activityId: Long)

@Entity(tableName = "entry_photos", foreignKeys = [
    ForeignKey(entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("entryId"), Index("localPath", unique = true)])
data class EntryPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long, val localPath: String, val sortOrder: Int, val createdAt: Long
)

@Entity(tableName = "goals", foreignKeys = [
    ForeignKey(entity = ActivityEntity::class, parentColumns = ["id"], childColumns = ["linkedActivityId"], onDelete = ForeignKey.RESTRICT)
], indices = [Index("linkedActivityId")])
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val linkedActivityId: Long?, val goalType: String, val targetCount: Int,
    val startDate: String, val endDate: String?, val isArchived: Boolean,
    val createdAt: Long, val updatedAt: Long
)

@Entity(tableName = "goal_schedules", primaryKeys = ["goalId", "dayOfWeek"], foreignKeys = [
    ForeignKey(entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.CASCADE)
])
data class GoalScheduleEntity(val goalId: Long, val dayOfWeek: Int)

@Entity(tableName = "goal_completions", foreignKeys = [
    ForeignKey(entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.CASCADE)
], indices = [Index(value = ["goalId", "date"], unique = true), Index("date")])
data class GoalCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long, val date: String, val completedAt: Long, val source: String
)

@Entity(tableName = "reminders", foreignKeys = [
    ForeignKey(entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["targetId"], onDelete = ForeignKey.SET_NULL)
], indices = [Index("targetId")])
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, val targetId: Long?, val hour: Int, val minute: Int,
    val daysOfWeek: String, val message: String, val enabled: Boolean,
    val createdAt: Long, val updatedAt: Long
)

@Entity(tableName = "note_templates")
data class NoteTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val content: String, val sortOrder: Int, val isArchived: Boolean
)

@Entity(tableName = "important_days", indices = [Index("date")])
data class ImportantDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, val title: String, val icon: String, val note: String
)

@Entity(tableName = "repository_metadata")
data class RepositoryMetadataEntity(@PrimaryKey val key: String, val value: String)
