package app.moodiary.feature

import androidx.lifecycle.ViewModel
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import app.moodiary.R
import app.moodiary.core.localization.localizedString
import androidx.lifecycle.viewModelScope
import app.moodiary.core.datastore.SettingsStore
import app.moodiary.core.media.PhotoStore
import app.moodiary.core.backup.BackupService
import app.moodiary.data.repository.JournalRepository
import app.moodiary.domain.AppPreferences
import app.moodiary.domain.JournalData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel class JournalViewModel @Inject constructor(
    val repository: JournalRepository,
    val settings: SettingsStore,
    val photos: PhotoStore,
    private val backups: BackupService,
    private val drafts: app.moodiary.core.datastore.DraftStore,
    restoreState: app.moodiary.core.backup.RestoreState,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val recoveryRequired = restoreState.recoveryRequired
    suspend fun recoverableEditorRoute(): String {
        val snapshot = repository.snapshot()
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val id = drafts.candidates().firstOrNull { (id, text) ->
            runCatching {
                val draft = json.decodeFromString<app.moodiary.feature.entryeditor.EntryDraft>(text)
                draft.id == id && draft.loaded && (id == 0L || snapshot.entries.any { it.id == id }) &&
                    (draft.note.isNotBlank() || draft.moodId != null || draft.activities.isNotEmpty() || draft.photoPaths.isNotEmpty()) &&
                    java.time.LocalDateTime.parse(draft.localDateTime) != null &&
                    (draft.moodId == null || snapshot.moods.any { it.id == draft.moodId }) &&
                    draft.photoPaths.all { app.moodiary.domain.DataValidation.isManagedPhotoPath(it) && photos.file(it).isFile }
            }.getOrDefault(false)
        }?.first ?: 0L
        return "editor/$id?date="
    }
    suspend fun cleanPhotos(removedEntryPhotos: Set<String> = emptySet()) = repository.withExclusive {
        repository.requireAvailable()
        photos.cleanUnreferenced(repository.snapshot().photos.map { it.localPath }.toSet() + drafts.photoReferences(), removedEntryPhotos)
    }
    suspend fun deleteEntry(id: Long) = repository.withExclusive {
        val owned = repository.snapshot().photos.filter { it.entryId == id }.map { it.localPath }.toSet()
        repository.deleteEntry(id)
        cleanPhotos(owned)
    }
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    val ready = MutableStateFlow(false)
    private val refresh = MutableStateFlow(0)
    val data = refresh.flatMapLatest { repository.data.catch { failure ->
        ready.value = false; _error.value = context.localizedString(R.string.app_read_journal_failed, failure.message.orEmpty())
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JournalData())
    val preferences = refresh.flatMapLatest { settings.preferences.catch { failure ->
        _error.value = context.localizedString(R.string.app_read_preferences_failed, failure.message.orEmpty())
        emit(AppPreferences()) // Display fallback only; never overwrite unreadable stored settings.
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPreferences())
    init { initialize() }
    fun initialize() = run { refresh.value += 1; backups.recoverInterruptedRestore(); repository.seedDefaults(); repository.recalculateLinkedCompletions(); ready.value = true }
    fun clearError() { _error.value = null }
    fun run(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _error.value = failure.message ?: context.localizedString(R.string.app_action_failed) }
            finally { _busy.value = false }
        }
    }
}
