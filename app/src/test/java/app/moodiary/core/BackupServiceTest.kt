package app.moodiary.core

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.moodiary.core.backup.BackupService
import app.moodiary.core.backup.GoogleDriveBackupProvider
import app.moodiary.core.database.MoodiaryDatabase
import app.moodiary.core.datastore.SettingsStore
import app.moodiary.core.media.PhotoStore
import app.moodiary.core.security.PinStore
import app.moodiary.data.repository.JournalRepository
import app.moodiary.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class BackupServiceTest {
    private lateinit var context: Context
    private lateinit var database: MoodiaryDatabase
    private lateinit var repository: JournalRepository
    private lateinit var preferences: SettingsStore
    private lateinit var photos: PhotoStore
    private lateinit var service: BackupService
    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "restore-in-progress").delete()
        File(context.filesDir, "restore-rollback.zip").delete()
        database = Room.inMemoryDatabaseBuilder(context, MoodiaryDatabase::class.java).build()
        repository = JournalRepository(database, app.moodiary.core.backup.RestoreState(context)); repository.seedDefaults()
        preferences = SettingsStore(context); preferences.update { AppPreferences(theme = "DARK", palette = "OCEAN") }
        photos = PhotoStore(context)
        service = BackupService(context, repository, preferences, photos, GoogleDriveBackupProvider(context), app.moodiary.core.backup.RestoreState(context))
        File(context.filesDir, "restore-in-progress").delete()
        File(context.filesDir, "restore-rollback.zip").delete()
        Unit
    }
    @After fun close() { database.close(); File(context.filesDir, "restore-in-progress").delete(); File(context.filesDir, "restore-rollback.zip").delete() }
    private suspend fun entry(note: String, photo: Boolean = false): Long {
        val mood = repository.snapshot().moods.first()
        return repository.saveEntry(Entry(timestamp = System.currentTimeMillis(), moodId = mood.id, note = note, moodName = mood.name, moodScore = mood.score, moodColor = mood.color, moodIcon = mood.icon), emptySet(), if (photo) listOf(EntryPhoto(entryId = 0, localPath = "test.png")) else emptyList())
    }
    @Test fun actualArchiveRestoresDatabaseSettingsAndOwnedImage() = runBlocking {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        photos.file("test.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        entry("Original 你好", true)
        val archive = service.createArchive()
        entry("After backup")
        preferences.update { it.copy(theme = "LIGHT") }
        val prepared = service.prepareRestore(Uri.fromFile(archive))
        assertEquals(1, prepared.entries)
        service.restore(prepared)
        val restored = repository.snapshot()
        assertEquals("Original 你好", restored.entries.single().note)
        assertEquals("DARK", preferences.preferences.first().theme)
        assertFalse(preferences.preferences.first().automaticBackup)
        assertTrue(photos.file(restored.photos.single().localPath).isFile)
        assertNotEquals("test.png", restored.photos.single().localPath)
        assertFalse(File(context.filesDir, "restore-in-progress").exists())
    }
    @Test fun truncatedArchiveNeverChangesLiveData() = runBlocking {
        entry("Keep me")
        val before = repository.snapshot()
        val invalid = File(context.cacheDir, "invalid.zip").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        try { service.prepareRestore(Uri.fromFile(invalid)); fail("Must reject corruption") } catch (_: IllegalArgumentException) { }
        assertEquals(before, repository.snapshot())
    }
    @Test fun interruptedRestoreRollsBackOnNextLaunch() = runBlocking {
        entry("Before interruption")
        val before = repository.snapshot()
        service.createArchive().copyTo(File(context.filesDir, "restore-rollback.zip"), overwrite = true)
        entry("Partial replacement")
        File(context.filesDir, "restore-in-progress").writeText("rollback-v1")
        try { entry("Must not overwrite recovery state"); fail("Writes must be gated") } catch (_: IllegalStateException) { }
        service.recoverInterruptedRestore()
        assertEquals(before, repository.snapshot())
        assertFalse(File(context.filesDir, "restore-in-progress").exists())
    }
    @Test fun pinPersistsAsHashAndThrottlesGuesses() = runBlocking {
        context.getSharedPreferences("private_lock", Context.MODE_PRIVATE).edit().clear().commit()
        val lock = PinStore(context)
        lock.set("735190")
        assertTrue(PinStore(context).enabled)
        assertTrue(lock.verify("735190"))
        assertFalse(context.getSharedPreferences("private_lock", Context.MODE_PRIVATE).all.values.any { it.toString().contains("735190") })
        repeat(5) { assertFalse(lock.verify("000000")) }
        try { lock.verify("735190"); fail("Must throttle") } catch (_: IllegalStateException) { }
    }
    @Test fun backupBeforeUiStartupRecoversInterruptedRestoreFirst() = runBlocking {
        entry("Last safe state")
        service.createArchive().copyTo(File(context.filesDir, "restore-rollback.zip"), overwrite = true)
        entry("Uncommitted import")
        File(context.filesDir, "restore-in-progress").writeText("rollback-v1")
        val archive = service.createArchive()
        stageBackup(archive, context.cacheDir).use { assertEquals("Last safe state", it.data.entries.single().note) }
        assertFalse(File(context.filesDir, "restore-in-progress").exists())
    }
    @Test fun unrecoverableMarkerBlocksWritesAndExportsWithoutChangingData() = runBlocking {
        entry("Keep current state")
        val before = repository.snapshot()
        File(context.filesDir, "restore-in-progress").writeText("rollback-v1")
        File(context.filesDir, "restore-rollback.zip").writeText("truncated")
        try { service.recoverInterruptedRestore(); fail("Must not ignore missing recovery data") } catch (_: IllegalArgumentException) { }
        try { entry("Forbidden"); fail("Must block writes") } catch (_: IllegalStateException) { }
        val destination = File(context.cacheDir, "blocked.json")
        try { app.moodiary.core.export.ExportService(context, repository, photos).export(Uri.fromFile(destination), "JSON", SearchFilter(), false); fail("Must block export") } catch (_: IllegalStateException) { }
        assertFalse(destination.exists())
        assertEquals(before, repository.snapshot())
        assertTrue(app.moodiary.core.backup.RestoreState(context).recoveryRequired.value)
    }

}
