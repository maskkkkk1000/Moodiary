package app.moodiary.domain

import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.CRC32
import java.util.zip.ZipFile

/** Media is streamed to caller-owned private staging storage, never all retained in the heap. */
class StagedBackup(val data: JournalData, val preferences: AppPreferences, val photos: Map<String, File>, val metadata: BackupMetadata, private val directory: File) : Closeable {
    override fun close() { directory.listFiles()?.forEach { it.delete() }; directory.delete() }
}

@OptIn(ExperimentalSerializationApi::class)
fun stageBackup(archive: File, parent: File, limits: BackupLimits = BackupLimits(totalBytes = 2L * 1024 * 1024 * 1024)): StagedBackup {
    val directory = java.nio.file.Files.createTempDirectory(parent.toPath(), "validated-").toFile()
    try {
        val files = linkedMapOf<String, File>()
        var total = 0L
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val path = entry.name
                val media = path.startsWith("photos/") && DataValidation.isManagedPhotoPath(path.removePrefix("photos/"))
                require(!entry.isDirectory && (media || path in setOf("metadata.json", "database.json", "settings.json"))) { "Unsafe archive path: $path" }
                require(path !in files) { "Duplicate archive path" }
                require(files.size < limits.fileCount) { "Too many files" }
                val maximum = when (path) { "metadata.json" -> 4L * 1024 * 1024; "database.json" -> limits.databaseBytes; "settings.json" -> 65536L; else -> limits.photoBytes }
                val target = File(directory, "part-${files.size}")
                val crc = CRC32()
                var size = 0L
                zip.getInputStream(entry).use { input -> target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) { val n = input.read(buffer); if (n < 0) break; size += n; total += n
                        require(size <= maximum && total <= limits.totalBytes) { "Backup exceeds configured size limit" }
                        crc.update(buffer, 0, n); output.write(buffer, 0, n)
                    }
                } }
                require(entry.crc == crc.value && entry.size == size) { "Archive checksum or length mismatch" }
                files[path] = target
            }
        }
        fun required(path: String) = requireNotNull(files.remove(path)) { "Missing $path" }
        val metadata = required("metadata.json").inputStream().use { BackupCodec.json.decodeFromStream<BackupMetadata>(it) }
        BackupCodec.requireSupportedFormat(metadata.formatVersion)
        Instant.parse(metadata.createdAt)
        require(metadata.platform == "android" && metadata.appVersion.isNotBlank() && metadata.appVersion.length <= 100) { "Invalid metadata" }
        val data = required("database.json").inputStream().use { BackupCodec.json.decodeFromStream<JournalData>(it) }
        val settings = required("settings.json").inputStream().use { BackupCodec.json.decodeFromStream<AppPreferences>(it) }
        DataValidation.requireValid(data, settings)
        val photos = files.mapKeys { it.key.removePrefix("photos/") }
        val referenced = data.photos.mapTo(HashSet()) { it.localPath }
        require(photos.keys == referenced && metadata.photoSha256.keys == referenced) { "Photo manifest does not match references" }
        photos.forEach { (name, file) ->
            require(file.length() > 0) { "Empty photo" }
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
            require(digest.digest().joinToString("") { "%02x".format(it) } == metadata.photoSha256[name]) { "Photo checksum failed" }
        }
        return StagedBackup(data, settings, photos, metadata, directory)
    } catch (failure: Exception) {
        directory.listFiles()?.forEach { it.delete() }; directory.delete()
        if (failure is java.util.zip.ZipException) throw IllegalArgumentException("Backup ZIP is incomplete or corrupted", failure)
        throw failure
    }
}
