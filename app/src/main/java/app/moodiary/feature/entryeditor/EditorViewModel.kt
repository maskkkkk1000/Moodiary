package app.moodiary.feature.entryeditor

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.moodiary.core.media.PhotoStore
import app.moodiary.core.datastore.DraftStore
import app.moodiary.core.localization.localizedString
import app.moodiary.R
import app.moodiary.data.repository.JournalRepository
import app.moodiary.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject

@Serializable data class EntryDraft(val id: Long = 0, val moodId: Long? = null, val note: String = "", val localDateTime: String = LocalDateTime.now().withSecond(0).withNano(0).toString(), val activities: Set<Long> = emptySet(), val photoPaths: List<String> = emptyList(), val loaded: Boolean = false, val originalTimestamp: Long? = null, val originalUpdatedAt: Long? = null, val originalLocalDateTime: String? = null) {
    /** Compare the field with what was originally displayed, rather than redisplaying the instant
     * in a possibly different device timezone. Untouched dates preserve seconds and DST overlap offset.
     */
    fun timestamp(zone: ZoneId): Long {
        val local = LocalDateTime.parse(localDateTime)
        val originalDisplay = originalLocalDateTime?.let(LocalDateTime::parse)
            ?: originalTimestamp?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime().withSecond(0).withNano(0) }
        if (originalTimestamp != null && local == originalDisplay) return originalTimestamp
        if (zone.rules.getValidOffsets(local).isEmpty()) throw NonexistentLocalTimeException()
        return local.atZone(zone).toInstant().toEpochMilli()
    }
}

/** A typed validation failure keeps date rules independent of Android's current language. */
class NonexistentLocalTimeException : IllegalArgumentException()

@HiltViewModel class EditorViewModel @Inject constructor(
    private val saved: SavedStateHandle, private val repository: JournalRepository, val photos: PhotoStore,
    private val diskDrafts: DraftStore, @ApplicationContext private val context: Context
) : ViewModel() {
    private val json = Json { encodeDefaults = true }
    private val _draft = MutableStateFlow(saved.get<String>("draft")?.let { json.decodeFromString<EntryDraft>(it) } ?: EntryDraft())
    val draft = _draft.asStateFlow()
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val savedEntry = MutableStateFlow(false)
    private var draftWrite: Job? = null
    fun change(transform: (EntryDraft) -> EntryDraft) {
        if (busy.value || savedEntry.value) return
        updateDraft(transform)
    }
    private fun updateDraft(transform: (EntryDraft) -> EntryDraft) {
        _draft.value = transform(_draft.value)
        val serialized = json.encodeToString(_draft.value)
        // Small drafts also use saved state for immediate recreation; long notes stay on disk.
        saved["draft"] = serialized.takeIf { it.length <= 32_000 }
        val previous = draftWrite
        val id = _draft.value.id
        draftWrite = viewModelScope.launch {
            previous?.join()
            try { diskDrafts.write(id, serialized) } catch (e: Exception) { if (e is CancellationException) throw e; error.value = context.localizedString(R.string.editor_could_not_preserve_draft, e.message.orEmpty()) }
        }
    }
    fun load(id: Long, date: String?) {
        if (_draft.value.loaded || busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                val stored = diskDrafts.read(id)?.let { json.decodeFromString<EntryDraft>(it) }
                if (stored != null) {
                    updateDraft { stored.copy(loaded = true) }
                    return@launch
                }
                val data = repository.snapshot()
                val entry = data.entries.find { it.id == id }
                if (id != 0L) requireNotNull(entry) { context.localizedString(R.string.editor_entry_no_longer_exists) }
                updateDraft {
                    if (entry == null) it.copy(loaded = true, localDateTime = date?.let { day -> "${day}T12:00" } ?: it.localDateTime)
                    else {
                        val originalDisplay = Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime().withSecond(0).withNano(0).toString()
                        EntryDraft(entry.id, entry.moodId, entry.note, originalDisplay, data.entryActivities.filter { it.entryId == id }.map { it.activityId }.toSet(), data.photos.filter { it.entryId == id }.sortedBy { it.sortOrder }.map { it.localPath }, true, entry.timestamp, entry.updatedAt, originalDisplay)
                    }
                }
            } catch (e: Exception) { if (e is CancellationException) throw e; error.value = userMessage(e, R.string.editor_could_not_open_entry) }
            finally { busy.value = false }
        }
    }
    fun import(uris: List<Uri>) {
        if (busy.value || savedEntry.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                require(draft.value.photoPaths.size + uris.size <= 12) { context.localizedString(R.string.editor_photo_limit) }
                uris.forEach { uri -> val path = photos.import(uri); updateDraft { it.copy(photoPaths = it.photoPaths + path) } }
            } catch (e: Exception) { if (e is CancellationException) throw e; error.value = userMessage(e, R.string.editor_could_not_import_photos) }
            finally { busy.value = false }
        }
    }
    fun save() {
        if (busy.value || savedEntry.value || !_draft.value.loaded) return
        busy.value = true
        viewModelScope.launch {
            try {
                val d = draft.value
                repository.withExclusive {
                    d.photoPaths.forEach { path -> require(photos.file(path).let { it.isFile && it.length() > 0 }) { context.localizedString(R.string.editor_attached_photo_missing) } }
                    val data = repository.snapshot()
                    val mood = requireNotNull(data.moods.find { it.id == d.moodId }) { context.localizedString(R.string.editor_choose_mood) }
                    val old = data.entries.find { it.id == d.id }
                    require(d.id == 0L || old != null) { context.localizedString(R.string.editor_entry_removed_keep_draft) }
                    val entry = Entry(d.id, d.timestamp(ZoneId.systemDefault()), mood.id, d.note, mood.name, mood.score, mood.color, mood.icon, old?.createdAt ?: System.currentTimeMillis())
                    repository.saveEntry(entry, d.activities, d.photoPaths.mapIndexed { index, path -> EntryPhoto(entryId = d.id, localPath = path, sortOrder = index) }, expectedUpdatedAt = d.originalUpdatedAt)
                }
                draftWrite?.join()
                diskDrafts.delete(d.id)
                savedEntry.value = true
            } catch (e: Exception) { if (e is CancellationException) throw e; error.value = userMessage(e, R.string.editor_could_not_save_entry) }
            finally { busy.value = false }
        }
    }
    fun discard(done: () -> Unit) {
        if (busy.value || savedEntry.value) return
        busy.value = true
        viewModelScope.launch {
            try { draftWrite?.join(); diskDrafts.delete(draft.value.id); done() }
            catch (e: Exception) { if (e is CancellationException) throw e; error.value = userMessage(e, R.string.editor_could_not_discard_draft) }
            finally { busy.value = false }
        }
    }

    private fun userMessage(failure: Exception, @StringRes fallback: Int): String = when (failure) {
        is NonexistentLocalTimeException -> context.localizedString(R.string.editor_nonexistent_time)
        is DateTimeParseException -> context.localizedString(R.string.editor_invalid_date_time)
        is SerializationException -> context.localizedString(R.string.editor_unreadable_draft)
        else -> failure.message ?: context.localizedString(fallback)
    }
}
