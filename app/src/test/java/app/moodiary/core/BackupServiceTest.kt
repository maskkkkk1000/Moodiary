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
    @Test fun smallGoalsArchiveRoundTripPreservesIconsResultsAndStatistics() = runBlocking {
        entry("Existing journal remains")
        val id = repository.saveBinaryGoal(BinaryGoal(name = "算法题", icon = "🧠", description = "A little each day"))
        repository.setBinaryGoalRecord(id, "2024-01-01", 1)
        repository.setBinaryGoalRecord(id, "2024-01-02", 0)
        val before = repository.snapshot()
        val archive = service.createArchive()
        val prepared = service.prepareRestore(Uri.fromFile(archive))
        assertEquals(1, prepared.binaryGoals); assertEquals(2, prepared.binaryGoalRecords)
        repository.replaceAll(JournalData(moods = before.moods))
        service.restore(prepared)
        val restored = repository.snapshot()
        assertEquals(before, restored)
        val metrics = BinaryGoalStatistics.summarize(restored.binaryGoals, restored.binaryGoalRecords,
            java.time.LocalDate.parse("2024-01-01"), java.time.LocalDate.parse("2024-01-03"), java.time.ZoneId.of("UTC"))
        assertEquals(50.0, metrics.total.completionRate!!, 0.0)
        assertEquals(1L, metrics.total.unset)
        val exported = BackupCodec.json.decodeFromString<JournalData>(ExportCodec.json(restored))
        assertEquals(restored.binaryGoals, exported.binaryGoals)
        assertEquals(restored.binaryGoalRecords, exported.binaryGoalRecords)
    }

    @Test fun legacyVersionOneArchiveRestoresWithEmptySmallGoals() = runBlocking {
        entry("Legacy version one note")
        val original = repository.snapshot()
        val archive = File(context.cacheDir, "legacy-format-one.zip")
        val fields = kotlinx.serialization.json.Json.parseToJsonElement(ExportCodec.json(original)).let { it as kotlinx.serialization.json.JsonObject }
        val legacy = kotlinx.serialization.json.JsonObject(fields.filterKeys { it != "binaryGoals" && it != "binaryGoalRecords" }).toString()
        java.util.zip.ZipOutputStream(archive.outputStream()).use { zip ->
            val files=mapOf("database.json" to legacy,"settings.json" to "{}","metadata.json" to "{\"formatVersion\":1,\"createdAt\":\"2024-01-01T00:00:00Z\",\"appVersion\":\"0.1.0\",\"platform\":\"android\",\"photoSha256\":{}}")
            files.forEach { (name,value) -> zip.putNextEntry(java.util.zip.ZipEntry(name));zip.write(value.toByteArray(Charsets.UTF_8));zip.closeEntry() }
        }
        repository.saveBinaryGoal(BinaryGoal(name="Newer goal to replace"))
        val prepared=service.prepareRestore(Uri.fromFile(archive))
        service.restore(prepared)
        assertEquals(original.entries,repository.snapshot().entries)
        assertTrue(repository.snapshot().binaryGoals.isEmpty())
        assertTrue(repository.snapshot().binaryGoalRecords.isEmpty())
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
