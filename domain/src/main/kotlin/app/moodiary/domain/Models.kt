package app.moodiary.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Serializable data class Mood(val id: Long = 0, val name: String, val score: Double, val color: Long = 0xFF326A5F, val icon: String = "●", val sortOrder: Int = 0, val isArchived: Boolean = false, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt)
@Serializable data class ActivityGroup(val id: Long = 0, val name: String, val sortOrder: Int = 0, val isArchived: Boolean = false, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt)
@Serializable data class Activity(val id: Long = 0, val groupId: Long? = null, val name: String, val icon: String = "○", val color: Long? = null, val sortOrder: Int = 0, val isArchived: Boolean = false, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt)
@Serializable data class Entry(val id: Long = 0, val timestamp: Long, val moodId: Long, val note: String = "", val moodName: String, val moodScore: Double, val moodColor: Long, val moodIcon: String, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt) {
    fun date(zone: ZoneId): LocalDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
}
@Serializable data class EntryActivity(val entryId: Long, val activityId: Long)
@Serializable data class EntryPhoto(val id: Long = 0, val entryId: Long, val localPath: String, val sortOrder: Int = 0, val createdAt: Long = System.currentTimeMillis())
@Serializable enum class GoalType { DAILY, WEEKDAYS, WEEKLY_COUNT }
@Serializable enum class CompletionSource { MANUAL, LINKED_ACTIVITY }
@Serializable data class Goal(val id: Long = 0, val name: String, val linkedActivityId: Long? = null, val goalType: GoalType = GoalType.DAILY, val targetCount: Int = 1, val startDate: String = LocalDate.now().toString(), val endDate: String? = null, val isArchived: Boolean = false, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt)
@Serializable data class GoalSchedule(val goalId: Long, val dayOfWeek: Int)
@Serializable data class GoalCompletion(val id: Long = 0, val goalId: Long, val date: String, val completedAt: Long = System.currentTimeMillis(), val source: CompletionSource = CompletionSource.MANUAL)
@Serializable enum class ReminderType { DIARY, GOAL }
@Serializable data class Reminder(val id: Long = 0, val type: ReminderType = ReminderType.DIARY, val targetId: Long? = null, val hour: Int = 20, val minute: Int = 0, val daysOfWeek: Set<Int> = (1..7).toSet(), val message: String = "Take a moment to record your day", val enabled: Boolean = true, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt)
@Serializable data class NoteTemplate(val id: Long = 0, val name: String, val content: String, val sortOrder: Int = 0, val isArchived: Boolean = false)
@Serializable data class ImportantDay(val id: Long = 0, val date: String, val title: String, val icon: String = "☆", val note: String = "")
@Serializable data class JournalData(
    val moods: List<Mood> = emptyList(),
    val groups: List<ActivityGroup> = emptyList(),
    val activities: List<Activity> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val entryActivities: List<EntryActivity> = emptyList(),
    val photos: List<EntryPhoto> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val schedules: List<GoalSchedule> = emptyList(),
    val completions: List<GoalCompletion> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val templates: List<NoteTemplate> = emptyList(),
    val importantDays: List<ImportantDay> = emptyList()
)
@Serializable data class AppPreferences(
    val theme: String = "SYSTEM", val palette: String = "FOREST", val aggregation: String = "AVERAGE",
    val relockMinutes: Int = 0, val biometrics: Boolean = false,
    val automaticBackup: Boolean = false, val backupTreeUri: String = "", val driveBackup: Boolean = false,
    val lastBackupAt: Long = 0, val lastBackupError: String = ""
)
data class SearchFilter(val query: String = "", val from: LocalDate? = null, val through: LocalDate? = null, val moodId: Long? = null, val activityId: Long? = null, val hasPhoto: Boolean? = null, val importantOnly: Boolean = false)
