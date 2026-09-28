package app.moodiary.core.backup

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.moodiary.R
import app.moodiary.core.datastore.SettingsStore
import app.moodiary.core.localization.localizedString
import app.moodiary.core.media.PhotoStore
import app.moodiary.data.repository.JournalRepository
import app.moodiary.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PreparedRestore(val file: File, val entries: Int, val photos: Int, val createdAt: String)

/** Managed media is staged under fresh filenames; Room replacement is atomic. A durable rollback
 * archive and marker recover any interrupted cross-store (Room/DataStore) restore on next launch. */
@Singleton class BackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: JournalRepository,
    private val settings: SettingsStore,
    private val photoStore: PhotoStore,
    private val drive: GoogleDriveBackupProvider,
    private val restoreState: RestoreState
) {
    private val automatic = Mutex()
    private val marker get() = File(context.filesDir, "restore-in-progress")
    private val rollback get() = File(context.filesDir, "restore-rollback.zip")
    private val cache get() = File(context.cacheDir, "backups").apply { mkdirs() }
    private val archiveLimits = BackupLimits(totalBytes = 2L * 1024 * 1024 * 1024)

    suspend fun createArchive(): File = repository.withExclusive {
        recoverInterruptedRestore()
        val output = File.createTempFile("moodiary-", ".zip", cache)
        try {
            output.outputStream().use { BackupCodec.write(it, repository.snapshot(), portableSettings(settings.preferences.first()), { path -> photoStore.file(path).inputStream() }, archiveLimits) }
            output
        } catch (failure: Exception) { output.delete(); throw failure }
    }
    private fun portableSettings(value: AppPreferences) = value.copy(automaticBackup = false, backupTreeUri = "", driveBackup = false, biometrics = false, lastBackupAt = 0, lastBackupError = "")
    suspend fun saveLocal(uri: Uri) = withContext(Dispatchers.IO) {
        val archive = createArchive()
        try { requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { context.localizedString(R.string.core_backup_write_unavailable) }.use { output -> archive.inputStream().use { it.copyTo(output) } }; settings.update { it.copy(lastBackupAt = System.currentTimeMillis(), lastBackupError = "") } }
        finally { archive.delete() }
    }
    suspend fun prepareRestore(uri: Uri): PreparedRestore = withContext(Dispatchers.IO) {
        val file = File.createTempFile("restore-", ".zip", cache)
        try {
            requireNotNull(context.contentResolver.openInputStream(uri)) { context.localizedString(R.string.core_backup_read_unavailable) }.use { input ->
                file.outputStream().use { output -> val buffer = ByteArray(8192); var total = 0L; while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= archiveLimits.totalBytes) { context.localizedString(R.string.core_backup_size_limit) }; output.write(buffer, 0, n) } }
            }
            inspect(file)
        } catch (e: Exception) { file.delete(); throw e }
    }
    private fun inspect(file: File): PreparedRestore {
        return stageBackup(file, cache).use { backup ->
            validateImages(backup)
            PreparedRestore(file, backup.data.entries.size, backup.data.photos.size, backup.metadata.createdAt)
        }
    }
    private fun validateImages(backup: StagedBackup) {
        backup.photos.forEach { (_, file) ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, options)
            require(options.outWidth > 0 && options.outHeight > 0 && options.outWidth.toLong() * options.outHeight <= 100_000_000) { context.localizedString(R.string.core_backup_invalid_image) }
        }
    }
    suspend fun restore(prepared: PreparedRestore) = repository.withRestoreAccess {
        require(prepared.file.canonicalFile.parentFile == cache.canonicalFile) { context.localizedString(R.string.core_restore_invalid_staging) }
        check(!marker.exists()) { context.localizedString(R.string.core_restore_recovery_required) }
        stageBackup(prepared.file, cache).use { incoming ->
        validateImages(incoming)
        val before = repository.snapshot()
        val beforePreferences = settings.preferences.first()
        FileOutputStream(rollback).use { output ->
            // ZipOutputStream closes its stream. Sync through a fresh handle afterwards.
            BackupCodec.write(output, before, beforePreferences, { photoStore.file(it).inputStream() }, archiveLimits)
        }
        FileOutputStream(rollback, true).use { it.fd.sync() }
        FileOutputStream(marker).use { it.write("rollback-v1".toByteArray()); it.fd.sync() }
        restoreState.refresh()
        withContext(NonCancellable) {
        try {
            val mapped = incoming.photos.keys.associateWith { "${UUID.randomUUID()}.img" }
            incoming.photos.forEach { (path, file) -> FileOutputStream(photoStore.file(mapped.getValue(path))).use { output -> file.inputStream().use { it.copyTo(output) }; output.fd.sync() } }
            repository.replaceAll(incoming.data.copy(photos = incoming.data.photos.map { it.copy(localPath = mapped.getValue(it.localPath)) }))
            settings.update { portableSettings(incoming.preferences) }
            check(marker.delete()) { context.localizedString(R.string.core_restore_finish_failed) }
            rollback.delete()
            prepared.file.delete()
        } catch (failure: Exception) {
            try { repository.replaceAll(before); settings.update { beforePreferences }; if (marker.delete()) rollback.delete() }
            catch (rollbackFailure: Exception) { failure.addSuppressed(rollbackFailure) }
            throw failure
        } finally { restoreState.refresh() }
        }
        }
    }
    suspend fun recoverInterruptedRestore() = repository.withRestoreAccess {
        restoreState.refresh()
        if (!marker.exists()) return@withRestoreAccess
        try {
        stageBackup(rollback, cache).use { previous ->
        // Old media is never deleted during restore, so its original references remain valid.
        previous.photos.forEach { (path, file) -> if (!photoStore.file(path).exists()) file.copyTo(photoStore.file(path)) }
        repository.replaceAll(previous.data)
        settings.update { previous.preferences }
        check(marker.delete()) { context.localizedString(R.string.core_recovery_finish_failed) }
        rollback.delete()
        }
        } finally { restoreState.refresh() }
    }
    suspend fun uploadDrive() = withContext(Dispatchers.IO) {
        val archive = createArchive()
        try { drive.upload(archive); settings.update { it.copy(lastBackupAt = System.currentTimeMillis(), lastBackupError = "") } } finally { archive.delete() }
    }
    suspend fun driveBackups(): List<CloudBackup> = drive.list()
    suspend fun prepareDriveRestore(id: String): PreparedRestore = withContext(Dispatchers.IO) {
        val file = File.createTempFile("restore-drive-", ".zip", cache)
        try { drive.download(id, file); inspect(file) } catch (e: Exception) { file.delete(); throw e }
    }
    suspend fun automaticBackup() = withContext(Dispatchers.IO) { automatic.withLock {
        recoverInterruptedRestore()
        val preferences = settings.preferences.first()
        require(preferences.backupTreeUri.isNotBlank() || preferences.driveBackup) { context.localizedString(R.string.core_backup_destination_required) }
        if (preferences.backupTreeUri.isNotBlank()) {
            val folder = requireNotNull(DocumentFile.fromTreeUri(context, Uri.parse(preferences.backupTreeUri))) { context.localizedString(R.string.core_backup_folder_permission_missing) }
            require(folder.canWrite()) { context.localizedString(R.string.core_backup_folder_write_required) }
            val name = "moodiary-auto-${java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString().replace("-", "")}.zip"
            // One verified local archive per UTC date: retrying a failed cloud upload must
            // not create duplicate local copies or consume the seven-day retention window.
            if (folder.findFile(name) == null) {
            val archive = createArchive()
            val document = requireNotNull(folder.createFile("application/zip", "moodiary-pending-${UUID.randomUUID()}.zip")) { context.localizedString(R.string.core_backup_create_failed) }
            try {
                requireNotNull(context.contentResolver.openOutputStream(document.uri, "wt")).use { output -> archive.inputStream().use { it.copyTo(output) } }
                // Verify bytes actually reached the provider before retiring older successful backups.
                // A byte hash comparison verifies provider writes without loading image payloads.
                fun hash(input: java.io.InputStream): ByteArray = input.use { stream -> val digest = java.security.MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536); while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }; digest.digest() }
                require(hash(archive.inputStream()).contentEquals(hash(requireNotNull(context.contentResolver.openInputStream(document.uri))))) { context.localizedString(R.string.core_backup_verification_failed) }
                require(document.renameTo(name)) { context.localizedString(R.string.core_backup_finalize_failed) }
                folder.listFiles().filter { it.name?.matches(Regex("moodiary-auto-[0-9]+\\.zip")) == true }.sortedByDescending { it.name }.drop(7).forEach { it.delete() }
            } catch (e: Exception) { document.delete(); throw e } finally { archive.delete() }
            }
        }
        if (preferences.driveBackup) uploadDrive()
    } }
}
