package app.moodiary.core.datastore

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import app.moodiary.R
import app.moodiary.core.localization.localizedString
import app.moodiary.domain.DataValidation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** App-private disk drafts avoid putting large notes into Android's bounded saved-state Binder. */
@Singleton class DraftStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val directory = File(context.filesDir, "drafts").apply { mkdirs() }
    private val mutex = Mutex()
    private fun file(id: Long) = AtomicFile(File(directory, "entry-$id.json"))
    suspend fun read(id: Long): String? = withContext(Dispatchers.IO) { mutex.withLock {
        val value = file(id)
        if (value.baseFile.exists() || File(value.baseFile.path + ".bak").exists()) value.openRead().bufferedReader().use { it.readText() } else null
    } }
    suspend fun write(id: Long, value: String) = withContext(Dispatchers.IO) { mutex.withLock {
        val target = file(id)
        val stream = target.startWrite()
        try { stream.write(value.toByteArray(Charsets.UTF_8)); target.finishWrite(stream) }
        catch (e: Exception) { target.failWrite(stream); throw e }
    } }
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { mutex.withLock { file(id).delete() } }

    /** Cleanup must preserve every durable draft, regardless of its age. Fail closed on corruption:
     * an unreadable draft may still own photos, so callers must abort cleanup rather than discard it.
     */
    suspend fun photoReferences(): Set<String> = withContext(Dispatchers.IO) { mutex.withLock {
        val pattern = Regex("entry-([0-9]+)\\.json(?:\\.bak)?")
        directory.listFiles().orEmpty().mapNotNull { pattern.matchEntire(it.name)?.groupValues?.get(1)?.toLongOrNull() }
            .distinct().flatMap { id ->
                val value = try {
                    file(id).openRead().bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
                } catch (failure: IllegalArgumentException) {
                    throw IllegalArgumentException(context.localizedString(R.string.editor_invalid_draft_cleanup_stopped), failure)
                }
                value["photoPaths"]?.jsonArray.orEmpty().map { photo -> photo.jsonPrimitive.content.also {
                    require(DataValidation.isManagedPhotoPath(it)) { context.localizedString(R.string.editor_invalid_draft_photo_reference) }
                } }
            }.toSet()
    } }
}
