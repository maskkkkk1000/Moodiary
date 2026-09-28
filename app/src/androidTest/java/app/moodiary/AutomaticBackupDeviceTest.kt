package app.moodiary

import android.content.Context
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.moodiary.core.backup.*
import app.moodiary.core.database.MoodiaryDatabase
import app.moodiary.core.datastore.SettingsStore
import app.moodiary.core.media.PhotoStore
import app.moodiary.data.repository.JournalRepository
import app.moodiary.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AutomaticBackupDeviceTest {
    @Test fun safArchiveIsVerifiedRetainedDeduplicatedAndRestorable() = runBlocking {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun grant(id: String) {
            automation.executeShellCommand("am start -W -n app.moodiary.test/app.moodiary.TestGrantActivity --es documentId $id").use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
            }
        }
        val context: Context = ApplicationProvider.getApplicationContext()
        val settings = SettingsStore(context); val previous = settings.preferences.first()
        val database = Room.inMemoryDatabaseBuilder(context, MoodiaryDatabase::class.java).build()
        var folder: DocumentFile? = null
        try {
            val tree = DocumentsContract.buildTreeDocumentUri("app.moodiary.test.documents", "root")
            grant("root")
            val root = requireNotNull(DocumentFile.fromTreeUri(context, tree))
            folder = requireNotNull(root.createDirectory("run-${UUID.randomUUID()}"))
            val folderTree = DocumentsContract.buildTreeDocumentUri("app.moodiary.test.documents", DocumentsContract.getDocumentId(folder.uri))
            grant(DocumentsContract.getDocumentId(folder.uri))
            val repository = JournalRepository(database, RestoreState(context)); repository.seedDefaults()
            val service = BackupService(context, repository, settings, PhotoStore(context), GoogleDriveBackupProvider(context), RestoreState(context))
            val mood = repository.snapshot().moods.first()
            repository.saveEntry(Entry(timestamp = 1, moodId = mood.id, note = "SAF round-trip", moodName = mood.name, moodScore = mood.score, moodColor = mood.color, moodIcon = mood.icon), emptySet(), emptyList())
            settings.update { it.copy(backupTreeUri = folderTree.toString(), driveBackup = false) }
            // Existing successful dated copies simulate older backup days for retention.
            repeat(9) { requireNotNull(folder.createFile("application/zip", "moodiary-auto-2000010$it.zip")) }
            service.automaticBackup(); service.automaticBackup()
            val documents = folder.listFiles()
            assertEquals(7, documents.size)
            assertTrue(documents.none { it.name?.startsWith("moodiary-pending-") == true })
            val newest = documents.maxBy { it.name.orEmpty() }
            val prepared = service.prepareRestore(newest.uri)
            assertEquals(1, prepared.entries)
            repository.deleteEntry(repository.snapshot().entries.single().id)
            service.restore(prepared)
            assertEquals("SAF round-trip", repository.snapshot().entries.single().note)
        } finally { folder?.delete(); settings.update { previous }; database.close() }
    }
}
