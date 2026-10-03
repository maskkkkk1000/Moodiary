package app.moodiary

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.moodiary.core.backup.RestoreState
import app.moodiary.core.database.MoodiaryDatabase
import app.moodiary.core.datastore.SettingsStore
import app.moodiary.core.media.PhotoStore
import app.moodiary.data.repository.JournalRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IterationMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MoodiaryDatabase::class.java)
    @Test fun realVersionOneDatabaseUpgradesWithoutChangingAnyLegacyRow() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "iteration-migration-test.db"
        context.deleteDatabase(name)
        val settings = SettingsStore(context)
        val oldSettings = settings.preferences.first()
        val photo = PhotoStore(context).file("migration-test.img")
        check(!photo.exists())
        photo.writeBytes(byteArrayOf(1, 4, 7, 9))
        settings.update { it.copy(theme = "DARK", aggregation = "LATEST") }
        val oldRows = linkedMapOf<String, List<List<String?>>>()
        val statements = listOf(
            "INSERT INTO moods VALUES (71,'Mood preserved',4.0,4281494111,'🌞',3,1,1000,1001)",
            "INSERT INTO activity_groups VALUES (72,'Group preserved',2,1,1000,1001)",
            "INSERT INTO activities VALUES (73,72,'Activity preserved','📚',4281494111,2,1,1000,1001)",
            "INSERT INTO entries VALUES (74,1704067200000,71,'Private synthetic note','Snapshot mood',4.0,4281494111,'☀',1000,1001)",
            "INSERT INTO entry_activities VALUES (74,73)",
            "INSERT INTO entry_photos VALUES (75,74,'migration-test.img',0,1000)",
            "INSERT INTO goals VALUES (76,'Goal preserved',73,'DAILY',1,'2024-01-01',NULL,0,1000,1001)",
            "INSERT INTO goal_schedules VALUES (76,1)",
            "INSERT INTO goal_completions VALUES (77,76,'2024-01-01',1704067200000,'MANUAL')",
            "INSERT INTO reminders VALUES (78,'GOAL',76,20,30,'1,2,3','Reminder preserved',1,1000,1001)",
            "INSERT INTO note_templates VALUES (79,'Template preserved','Prompt preserved',0,0)",
            "INSERT INTO important_days VALUES (80,'2024-01-01','Day preserved','☆','Note preserved')",
            "INSERT INTO repository_metadata VALUES ('migration-test','keep this value')"
        )
        val tables = MoodiaryDatabase.DATA_TABLES.filterNot { it.startsWith("binary_") } + "repository_metadata"
        fun rows(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): List<List<String?>> =
            db.query("SELECT * FROM `$table`").use { c -> buildList { while(c.moveToNext()) add((0 until c.columnCount).map { if(c.isNull(it)) null else c.getString(it) }) } }
        try {
            helper.createDatabase(name, 1).use { old ->
                statements.forEach(old::execSQL)
                tables.forEach { oldRows[it] = rows(old, it) }
            }
            helper.runMigrationsAndValidate(name, 2, true, MoodiaryDatabase.MIGRATION_1_2).use { upgraded ->
                assertEquals(2, upgraded.version)
                tables.forEach { assertEquals("All columns in $it must survive", oldRows[it], rows(upgraded, it)) }
                assertTrue(rows(upgraded,"binary_goals").isEmpty())
                assertTrue(rows(upgraded,"binary_goal_records").isEmpty())
                upgraded.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            }
            val db = Room.databaseBuilder(context, MoodiaryDatabase::class.java, name).addMigrations(MoodiaryDatabase.MIGRATION_1_2).build()
            try {
                val repository = JournalRepository(db, RestoreState(context))
                val data = repository.snapshot()
                assertEquals("Private synthetic note", data.entries.single().note)
                assertEquals("migration-test.img", data.photos.single().localPath)
                assertEquals("Snapshot mood", data.entries.single().moodName)
                assertEquals("DARK", settings.preferences.first().theme)
                assertEquals("LATEST", settings.preferences.first().aggregation)
                assertArrayEquals(byteArrayOf(1,4,7,9), photo.readBytes())
            } finally { db.close() }
        } finally { settings.update { oldSettings }; photo.delete(); context.deleteDatabase(name) }
    }
}
