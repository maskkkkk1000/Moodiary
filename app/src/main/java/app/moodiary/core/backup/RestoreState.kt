package app.moodiary.core.backup

import android.content.Context
import app.moodiary.R
import app.moodiary.core.localization.localizedString
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The durable marker is authoritative before UI initialization and after a process restart. */
@Singleton class RestoreState @Inject constructor(@ApplicationContext private val context: Context) {
    private val marker = File(context.filesDir, "restore-in-progress")
    private val required = MutableStateFlow(marker.exists())
    val recoveryRequired = required.asStateFlow()
    fun refresh() { required.value = marker.exists() }
    fun requireAvailable() {
        refresh()
        check(!required.value) { context.localizedString(R.string.editor_recovery_required) }
    }
}
