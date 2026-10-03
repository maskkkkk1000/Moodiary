package app.moodiary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.moodiary.core.backup.RestoreState
import app.moodiary.core.database.MoodiaryDatabase
import app.moodiary.data.repository.JournalRepository
import app.moodiary.domain.Entry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Unsupported future schemas must still fail without deletion after the additive 1-to-2 migration. */
@RunWith(AndroidJUnit4::class)
class MigrationSafetyTest {
    @Test fun unsupportedVersionNeverDestroysExistingDiary() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "migration-safety-test.db"
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, MoodiaryDatabase::class.java, name).build()
        try {
            val repository = JournalRepository(db, RestoreState(context)); repository.seedDefaults()
            val mood = repository.snapshot().moods.first()
            repository.saveEntry(Entry(timestamp = 1, moodId = mood.id, note = "Preserve across unsupported version", moodName = mood.name, moodScore = mood.score, moodColor = mood.color, moodIcon = mood.icon), emptySet(), emptyList())
        } finally { db.close() }
        val path = context.getDatabasePath(name).path
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 3 }
        val unsupported = Room.databaseBuilder(context, MoodiaryDatabase::class.java, name).build()
        try {
            try { unsupported.journalDao().entries(); fail("Unsupported downgrade must not erase data") } catch (_: IllegalStateException) { }
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
                assertEquals(3, sqlite.version)
                sqlite.rawQuery("SELECT note FROM entries", null).use { row -> assertTrue(row.moveToFirst()); assertEquals("Preserve across unsupported version", row.getString(0)) }
            }
        } finally { unsupported.close(); context.deleteDatabase(name) }
    }
}
