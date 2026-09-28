package app.moodiary.feature

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.moodiary.R
import app.moodiary.core.backup.*
import app.moodiary.core.export.ExportService
import app.moodiary.core.notifications.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel class DataToolsViewModel @Inject constructor(val backups: BackupService, val drive: GoogleDriveBackupProvider, val exports: ExportService, val reminders: ReminderScheduler, val automatic: AutoBackupScheduler, @ApplicationContext private val context: Context) : ViewModel() {
    val busy = MutableStateFlow(false)
    val status = MutableStateFlow("")
    val prepared = MutableStateFlow<PreparedRestore?>(null)
    val cloud = MutableStateFlow<List<CloudBackup>>(emptyList())
    fun run(message: String? = null, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch { try { block(); status.value = message ?: ContextCompat.getContextForLanguage(context).getString(R.string.data_tools_done) } catch (e: Exception) { if (e is CancellationException) throw e; status.value = e.message ?: ContextCompat.getContextForLanguage(context).getString(R.string.data_tools_operation_failed) } finally { busy.value = false } }
    }
}
