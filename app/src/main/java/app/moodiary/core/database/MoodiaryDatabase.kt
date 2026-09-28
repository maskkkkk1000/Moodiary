package app.moodiary.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [MoodEntity::class, ActivityGroupEntity::class, ActivityEntity::class,
        EntryEntity::class, EntryActivityEntity::class, EntryPhotoEntity::class,
        GoalEntity::class, GoalScheduleEntity::class, GoalCompletionEntity::class,
        ReminderEntity::class, NoteTemplateEntity::class, ImportantDayEntity::class,
        RepositoryMetadataEntity::class],
    version = 1,
    exportSchema = true
)
abstract class MoodiaryDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao

    companion object {
        const val NAME = "moodiary.db"
        val DATA_TABLES = arrayOf("moods", "activity_groups", "activities", "entries", "entry_activities",
            "entry_photos", "goals", "goal_schedules", "goal_completions", "reminders", "note_templates", "important_days")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): MoodiaryDatabase =
        Room.databaseBuilder(context, MoodiaryDatabase::class.java, MoodiaryDatabase.NAME)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            // Initial production schema is v1. Future versions must register explicit migrations.
            // Intentionally no fallbackToDestructiveMigration: an unsupported upgrade fails safely.
            .build()
}
