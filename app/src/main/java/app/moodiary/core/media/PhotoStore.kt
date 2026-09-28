package app.moodiary.core.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import app.moodiary.R
import app.moodiary.core.localization.localizedString
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class PhotoStore @Inject constructor(@ApplicationContext private val context: Context) {
    val directory: File get() = File(context.filesDir, "photos").apply { mkdirs() }
    fun file(path: String): File {
        require(path.matches(Regex("[a-zA-Z0-9_-]+\\.[a-zA-Z0-9]+"))) { context.localizedString(R.string.core_invalid_photo_path) }
        return File(directory, path)
    }
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val name = "${UUID.randomUUID()}.img"
        val destination = file(name)
        try {
            context.contentResolver.openInputStream(uri).use { source ->
                requireNotNull(source) { context.localizedString(R.string.core_photo_unavailable) }
                destination.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 30L * 1024 * 1024) { context.localizedString(R.string.core_photo_size_limit) }
                        output.write(buffer, 0, count)
                    }
                }
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(destination.path, options)
            require(options.outWidth > 0 && options.outHeight > 0) { context.localizedString(R.string.core_photo_unsupported) }
            require(options.outWidth.toLong() * options.outHeight <= 100_000_000) { context.localizedString(R.string.core_photo_dimensions_limit) }
            name
        } catch (failure: Exception) { destination.delete(); throw failure }
    }
    suspend fun cleanUnreferenced(references: Set<String>, removedEntryPhotos: Set<String> = emptySet()) = withContext(Dispatchers.IO) {
        // Draft references are protected regardless of age. A grace period covers unfinished imports.
        val cutoff = System.currentTimeMillis() - 86_400_000L
        directory.listFiles()?.filter { it.name !in references && (it.name in removedEntryPhotos || it.lastModified() < cutoff) }?.forEach { it.delete() }
    }
}
