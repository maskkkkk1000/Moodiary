package app.moodiary.core.notifications

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.moodiary.data.repository.JournalRepository
import app.moodiary.domain.Goal
import app.moodiary.domain.Reminder
import app.moodiary.domain.ReminderSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Approximate wall-clock reminders. WorkManager may defer them for battery restrictions. */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: JournalRepository
) {
    private val manager = WorkManager.getInstance(context)
    private val scheduling = Mutex()

    /** Call after reminder/goal edits, restore, and activity resume (including timezone changes). */
    suspend fun reconcile() = reconcileAt(Instant.now(), ZoneId.systemDefault())

    /** Explicit clock is used by schedule regression tests without changing the device clock. */
    internal suspend fun reconcileAt(now: Instant, zone: ZoneId) = withContext(Dispatchers.IO) {
        scheduling.withLock {
            // Persist the independent recovery anchor before accessing Room. A failed one-shot
            // reminder must not permanently disable subsequent reminders while the app is closed.
            manager.enqueueUniquePeriodicWork(RECOVERY_NAME, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReminderReconcileWorker>(1, TimeUnit.HOURS)
                    .setInitialDelay(1, TimeUnit.HOURS).addTag(RECOVERY_NAME).build()).result.get()
            val (reminders, goals) = repository.reminderState()
            val goalById = goals.associateBy { it.id }
            val reminderById = reminders.associateBy { it.id }
            val desired = reminders.mapNotNull { reminder ->
                val goal = goalById[reminder.targetId]
                ReminderSchedule.next(reminder, now, zone, goal)?.let { due ->
                    Planned(reminder, due, signature(reminder, goal, zone))
                }
            }
            val stamps = desired.map { it.stamp }.toSet()
            val current = manager.getWorkInfosByTag(TAG).get()
            current.filter { work ->
                !work.state.isFinished && work.state != WorkInfo.State.RUNNING && work.tags.none(stamps::contains) &&
                    !(work.state == WorkInfo.State.ENQUEUED && work.tags.any { tag ->
                        retainOverdue(tag, reminderById, goalById, now, zone)
                    })
            }
                .forEach { manager.cancelWorkById(it.id).result.get() }
            desired.forEach { planned ->
                // KEEP and a deterministic occurrence identity avoid duplicate notifications on
                // resume, repeated saves, process restart, and concurrent schedule requests.
                val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInputData(workDataOf(KEY_ID to planned.reminder.id, KEY_DUE to planned.due.toEpochMilli(), KEY_SIGNATURE to planned.signature, KEY_ZONE to zone.id))
                    .setInitialDelay(Duration.between(now, planned.due).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    .addTag(TAG).addTag(planned.stamp).build()
                manager.enqueueUniqueWork(planned.stamp, ExistingWorkPolicy.KEEP, request).result.get()
            }
        }
    }

    private data class Planned(val reminder: Reminder, val due: Instant, val signature: String) {
        val stamp = "moodiary-reminder-${reminder.id}-${due.toEpochMilli()}-$signature"
    }

    companion object {
        internal const val TAG = "moodiary-reminders"
        internal const val RECOVERY_NAME = "moodiary-reminder-recovery"
        internal val LATE_WINDOW: Duration = Duration.ofHours(6)
        internal const val KEY_ID = "reminderId"
        internal const val KEY_DUE = "dueAt"
        internal const val KEY_SIGNATURE = "signature"
        internal const val KEY_ZONE = "zone"
        private val occurrencePattern = Regex("^moodiary-reminder-([0-9]+)-([0-9]+)-([0-9a-f]{64})$")

        /** Only an unchanged, still-qualifying occurrence within its delivery window survives. */
        internal fun retainOverdue(tag: String, reminders: Map<Long, Reminder>, goals: Map<Long, Goal>, now: Instant, zone: ZoneId): Boolean {
            val parts = occurrencePattern.matchEntire(tag)?.groupValues ?: return false
            val id = parts[1].toLongOrNull() ?: return false
            val due = parts[2].toLongOrNull()?.let(Instant::ofEpochMilli) ?: return false
            val age = Duration.between(due, now)
            if (age.isNegative || age > LATE_WINDOW) return false
            val reminder = reminders[id] ?: return false
            val goal = goals[reminder.targetId]
            return parts[3] == signature(reminder, goal, zone) &&
                ReminderSchedule.next(reminder, due.minusMillis(1), zone, goal) == due
        }

        internal fun signature(reminder: Reminder, goal: Goal?, zone: ZoneId): String {
            val value = listOf(reminder.id, reminder.type, reminder.targetId, reminder.hour, reminder.minute,
                reminder.daysOfWeek.sorted().joinToString(","), reminder.message, reminder.enabled,
                reminder.updatedAt, goal?.id, goal?.isArchived, goal?.startDate, goal?.endDate, zone.id).joinToString("\u0000")
            return MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
