package app.moodiary.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.Executor

/** Keep the broadcast brief; Room writes and rescheduling happen in a durable worker. */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED && intent.action != Intent.ACTION_TIME_CHANGED) return
        val pending = goAsync()
        try {
            val request = OneTimeWorkRequestBuilder<ReminderReconcileWorker>()
                .setInputData(workDataOf(ReminderReconcileWorker.RECALCULATE_DATES to true))
                .build()
            val operation = WorkManager.getInstance(context).enqueueUniqueWork(
                CLOCK_WORK, ExistingWorkPolicy.REPLACE, request
            )
            // Finish only after WorkManager persisted the request; returning immediately could
            // let Android end the receiver process before the scheduling transaction commits.
            operation.result.addListener({ pending.finish() }, Executor { it.run() })
        } catch (_: Exception) {
            pending.finish()
            // The persisted hourly worker retries reconciliation if enqueue itself failed.
        }
    }

    companion object { internal const val CLOCK_WORK = "moodiary-clock-reconcile" }
}
