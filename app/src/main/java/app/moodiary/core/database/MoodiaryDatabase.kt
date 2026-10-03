package app.moodiary.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        RepositoryMetadataEntity::class, BinaryGoalEntity::class, BinaryGoalRecordEntity::class],
    version = 2,
    exportSchema = true
)
abstract class MoodiaryDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao

    companion object {
        const val NAME = "moodiary.db"
        /** Additive upgrade only: every existing table and row remains untouched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `binary_goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT NOT NULL, `description` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `binary_goal_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goalId` INTEGER NOT NULL, `date` TEXT NOT NULL, `value` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`goalId`) REFERENCES `binary_goals`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_binary_goal_records_goalId_date` ON `binary_goal_records` (`goalId`, `date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_binary_goal_records_date` ON `binary_goal_records` (`date`)")
            }
        }
        val DATA_TABLES = arrayOf("moods", "activity_groups", "activities", "entries", "entry_activities",
            "entry_photos", "goals", "goal_schedules", "goal_completions", "reminders", "note_templates", "important_days", "binary_goals", "binary_goal_records")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): MoodiaryDatabase =
        Room.databaseBuilder(context, MoodiaryDatabase::class.java, MoodiaryDatabase.NAME)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(MoodiaryDatabase.MIGRATION_1_2)
            // Intentionally no fallbackToDestructiveMigration: an unsupported upgrade fails safely.
            .build()
}
