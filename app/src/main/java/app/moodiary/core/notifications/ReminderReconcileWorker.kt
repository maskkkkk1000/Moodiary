package app.moodiary.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.moodiary.data.repository.JournalRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

/** Independent periodic repair plus immediate wall-clock/timezone change reconciliation. */
@HiltWorker
class ReminderReconcileWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val repository: JournalRepository,
    private val scheduler: ReminderScheduler
) : CoroutineWorker(context, parameters) {
    private val clockState = context.getSharedPreferences("reminder_clock", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result = try {
        val zone = ZoneId.systemDefault()
        // Rebuild date-based automatic completions on a clock broadcast or a missed timezone
        // event. Ordinary hourly repairs need only narrow reminder/goal reads, not journal scans.
        if (inputData.getBoolean(RECALCULATE_DATES, false) || clockState.getString(LAST_ZONE, null) != zone.id) {
            repository.recalculateLinkedCompletions(zone)
            check(clockState.edit().putString(LAST_ZONE, zone.id).commit())
        }
        scheduler.reconcile()
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Periodic work remains scheduled even if an individual execution fails. Immediate
        // clock work retries transient storage errors and is also covered by periodic repair.
        Result.retry()
    }

    companion object {
        internal const val RECALCULATE_DATES = "recalculateDates"
        private const val LAST_ZONE = "lastLinkedCompletionZone"
    }
}
