package app.moodiary.core.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.moodiary.R
import app.moodiary.core.datastore.SettingsStore
import app.moodiary.core.localization.localizedString
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val backups: BackupService,
    private val settings: SettingsStore
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!settings.preferences.first().automaticBackup) return Result.success()
        return try {
            backups.automaticBackup()
            settings.update { it.copy(lastBackupAt = System.currentTimeMillis(), lastBackupError = "") }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            settings.update { it.copy(lastBackupError = error.message?.take(250) ?: applicationContext.localizedString(R.string.core_automatic_backup_failed)) }
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

@Singleton
class AutoBackupScheduler @Inject constructor(@ApplicationContext context: Context) {
    private val manager = WorkManager.getInstance(context)
    fun schedule(enabled: Boolean) {
        if (!enabled) {
            manager.cancelUniqueWork(NAME)
            return
        }
        // KEEP preserves the schedule across every Activity resume. Local backups work offline;
        // an optional cloud destination handles its own connectivity failure and retry.
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
            .setInitialDelay(24, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
    private companion object { const val NAME = "moodiary-automatic-backup" }
}
