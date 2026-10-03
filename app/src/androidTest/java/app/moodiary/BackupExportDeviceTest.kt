package app.moodiary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.moodiary.core.backup.BackupService
import app.moodiary.core.backup.GoogleDriveBackupProvider
import app.moodiary.core.database.MoodiaryDatabase
import app.moodiary.core.datastore.SettingsStore
import app.moodiary.core.export.ExportService
import app.moodiary.core.media.PhotoStore
import app.moodiary.data.repository.JournalRepository
import app.moodiary.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupExportDeviceTest {
    @Test fun realPhotosRestoreAndUnicodePdfPaginate() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = Room.inMemoryDatabaseBuilder(context, MoodiaryDatabase::class.java).build()
        try {
            val repo = JournalRepository(database, app.moodiary.core.backup.RestoreState(context)); repo.seedDefaults()
            val settings = SettingsStore(context); settings.update { AppPreferences() }
            val photos = PhotoStore(context)
            val photo = photos.file("device-test.png")
            val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GREEN) }
            photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            val mood = repo.snapshot().moods.first()
            val note = (1..100).joinToString("\n") { "Line $it · 你好，世界 · café · mood journal" }
            repo.saveEntry(Entry(timestamp = System.currentTimeMillis(), moodId = mood.id, note = note, moodName = mood.name, moodScore = mood.score, moodColor = mood.color, moodIcon = mood.icon), emptySet(), listOf(EntryPhoto(entryId = 0, localPath = photo.name)))
            val binaryId = repo.saveBinaryGoal(BinaryGoal(name = "设备小目标", icon = "📚"))
            repo.setBinaryGoalRecord(binaryId, "2024-01-01", 1)
            repo.setBinaryGoalRecord(binaryId, "2024-01-02", 0)
            val backup = BackupService(context, repo, settings, photos, GoogleDriveBackupProvider(context), app.moodiary.core.backup.RestoreState(context))
            val archive = backup.createArchive()
            val original = repo.snapshot()
            repo.deleteEntry(original.entries.single().id)
            repo.setBinaryGoalRecord(binaryId, "2024-01-01", null)
            repo.setBinaryGoalRecord(binaryId, "2024-01-02", null)
            repo.deleteBinaryGoalIfUnused(binaryId)
            val prepared = backup.prepareRestore(Uri.fromFile(archive))
            backup.restore(prepared)
            assertEquals(note, repo.snapshot().entries.single().note)
            assertTrue(photos.file(repo.snapshot().photos.single().localPath).isFile)
            assertEquals(original.binaryGoals, repo.snapshot().binaryGoals)
            assertEquals(original.binaryGoalRecords, repo.snapshot().binaryGoalRecords)
            val json = File(context.cacheDir, "device-report.json")
            ExportService(context, repo, photos).export(Uri.fromFile(json), "JSON", SearchFilter(), false)
            val exported = BackupCodec.json.decodeFromString<JournalData>(json.readText())
            assertEquals(original.binaryGoalRecords, exported.binaryGoalRecords)
            val pdf = File(context.cacheDir, "device-report.pdf")
            ExportService(context, repo, photos).export(Uri.fromFile(pdf), "PDF", SearchFilter(), true)
            PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                assertTrue(renderer.pageCount >= 2)
                renderer.openPage(0).use { page ->
                    val rendered = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    assertTrue(rendered.width > 0)
                    val output = File(context.getExternalFilesDir(null), "verification").apply { mkdirs() }
                    File(output, "pdf-page1.png").outputStream().use { rendered.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cp ${File(output, "pdf-page1.png").path} /sdcard/Download/moodiary-pdf-page1.png").use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes() }
                    rendered.recycle()
                }
            }
            val csv = File(context.cacheDir, "device-report.csv")
            ExportService(context, repo, photos).export(Uri.fromFile(csv), "CSV", SearchFilter(), false)
            assertTrue(csv.readText().contains("你好，世界"))
            val corrupt = File(context.cacheDir, "device-corrupt.zip").apply { writeBytes(byteArrayOf(0, 1, 2)) }
            val before = repo.snapshot()
            try { backup.prepareRestore(Uri.fromFile(corrupt)); fail("Must reject corrupt archive") } catch (_: IllegalArgumentException) { }
            assertEquals(before, repo.snapshot())
        } finally { database.close() }
    }
}
