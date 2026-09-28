package app.moodiary.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.moodiary.R
import app.moodiary.core.localization.localizedString
import app.moodiary.data.repository.JournalRepository
import app.moodiary.domain.ReminderSchedule
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted parameters: WorkerParameters,
    private val repository: JournalRepository,
    private val scheduler: ReminderScheduler
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return try {
            val id = inputData.getLong(ReminderScheduler.KEY_ID, -1)
            val due = Instant.ofEpochMilli(inputData.getLong(ReminderScheduler.KEY_DUE, 0))
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            val (reminders, goals) = repository.reminderState()
            val reminder = reminders.find { it.id == id }
            val goal = goals.find { it.id == reminder?.targetId }
            val signatureMatches = reminder != null && inputData.getString(ReminderScheduler.KEY_SIGNATURE) == ReminderScheduler.signature(reminder, goal, zone)
            val occurrenceValid = reminder != null && ReminderSchedule.next(reminder, due.minusMillis(1), zone, goal) == due
            // Do not deliver obsolete messages after a date/time edit, timezone change, or a
            // long offline period. A delayed reminder up to six hours is still useful.
            val age = Duration.between(due, now).toMillis()
            if (signatureMatches && occurrenceValid && inputData.getString(ReminderScheduler.KEY_ZONE) == zone.id && age in 0..ReminderScheduler.LATE_WINDOW.toMillis()) {
                if (notificationsAllowed()) notify(id, due.toEpochMilli(), reminder!!.message)
            }
            scheduler.reconcile()
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Notification ids are stable per occurrence; retries replace the same notification.
            // The independent periodic reconciliation also restores the chain after exhaustion.
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun notificationsAllowed(): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun notify(id: Long, due: Long, message: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.localizedString(R.string.notification_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP) ?: return
        val action = PendingIntent.getActivity(context, id.hashCode(), launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_moodiary)
            .setContentTitle("Moodiary").setContentText(context.localizedString(R.string.notification_public)).build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_moodiary).setContentTitle("Moodiary")
            .setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(action).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public).build()
        // Permission can be revoked between the check and notify(). The next occurrence remains
        // scheduled, and a denied permission never causes repeated background retries.
        try {
            val tag = notificationTag(id, due)
            // Retire an earlier date's notification without suppressing today's new alert.
            manager.activeNotifications.filter { it.tag == "reminder:$id" || (it.tag?.startsWith("reminder:$id:") == true && it.tag != tag) }
                .forEach { manager.cancel(it.tag, it.id) }
            manager.notify(tag, 0, notification)
        } catch (_: SecurityException) { }
    }

    internal companion object {
        const val CHANNEL = "journal_reminders"
        fun notificationTag(id: Long, due: Long) = "reminder:$id:$due"
    }
}
