package app.moodiary

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import app.moodiary.core.database.MoodiaryDatabase
import app.moodiary.core.notifications.ReminderScheduler
import app.moodiary.data.repository.JournalRepository
import app.moodiary.domain.Reminder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@RunWith(AndroidJUnit4::class)
class ReminderSchedulingDeviceTest {
    @Test fun editingReplacesAndDisablingCancelsFutureWork() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = Room.inMemoryDatabaseBuilder(context, MoodiaryDatabase::class.java).build()
        val manager = WorkManager.getInstance(context)
        try {
            val repository = JournalRepository(database, app.moodiary.core.backup.RestoreState(context)); repository.seedDefaults()
            val scheduler = ReminderScheduler(context, repository)
            val time = LocalTime.now().plusHours(2)
            val id = repository.saveReminder(Reminder(hour = time.hour, minute = time.minute, message = "Test scheduling"))
            scheduler.reconcile(); scheduler.reconcile()
            val first = manager.getWorkInfosByTag("moodiary-reminders").get().filter { !it.state.isFinished }
            assertEquals(1, first.size)
            val original = repository.reminders().single()
            repository.saveReminder(original.copy(minute = (original.minute + 1) % 60))
            scheduler.reconcile()
            val second = manager.getWorkInfosByTag("moodiary-reminders").get().filter { !it.state.isFinished }
            assertEquals(1, second.size)
            assertNotEquals(first.single().id, second.single().id)
            repository.saveReminder(repository.reminders().single().copy(enabled = false))
            scheduler.reconcile()
            assertTrue(manager.getWorkInfosByTag("moodiary-reminders").get().none { !it.state.isFinished })
            repository.deleteReminder(id)
        } finally { manager.cancelAllWorkByTag("moodiary-reminders").result.get(); manager.cancelUniqueWork("moodiary-reminder-recovery").result.get(); database.close() }
    }

    @Test fun overdueWorkSurvivesReconciliationAndDurableRecoveryIsUnique() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = Room.inMemoryDatabaseBuilder(context, MoodiaryDatabase::class.java).build()
        val manager = WorkManager.getInstance(context)
        manager.cancelAllWorkByTag("moodiary-reminders").result.get()
        manager.cancelUniqueWork("moodiary-reminder-recovery").result.get()
        try {
            val repository = JournalRepository(database, app.moodiary.core.backup.RestoreState(context)); repository.seedDefaults()
            val scheduler = ReminderScheduler(context, repository)
            val zone = ZoneId.systemDefault()
            val before = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MINUTES)
            val due = before.plusSeconds(3600)
            val wallTime = due.atZone(zone)
            repository.saveReminder(Reminder(hour = wallTime.hour, minute = wallTime.minute, message = "Delayed reminder"))
            scheduler.reconcileAt(before, zone)
            val original = manager.getWorkInfosByTag("moodiary-reminders").get().single { !it.state.isFinished }

            scheduler.reconcileAt(due.plusSeconds(60), zone)
            val late = manager.getWorkInfosByTag("moodiary-reminders").get().filter { !it.state.isFinished }
            assertEquals(2, late.size) // Delayed occurrence plus the following date.
            assertTrue(late.any { it.id == original.id })
            assertEquals(1, manager.getWorkInfosForUniqueWork("moodiary-reminder-recovery").get().count { !it.state.isFinished })

            scheduler.reconcileAt(due.plusSeconds(6 * 3600).plusMillis(1), zone)
            val afterWindow = manager.getWorkInfosByTag("moodiary-reminders").get().filter { !it.state.isFinished }
            assertEquals(1, afterWindow.size)
            assertFalse(afterWindow.any { it.id == original.id })

            // Simulate a terminated one-shot chain. Its independent persisted periodic task
            // remains available to run ReminderReconcileWorker without opening the app.
            manager.cancelAllWorkByTag("moodiary-reminders").result.get()
            assertEquals(1, manager.getWorkInfosForUniqueWork("moodiary-reminder-recovery").get().count { !it.state.isFinished })
            scheduler.reconcileAt(due.plusSeconds(6 * 3600).plusMillis(1), zone)
            assertEquals(1, manager.getWorkInfosByTag("moodiary-reminders").get().count { !it.state.isFinished })
        } finally {
            manager.cancelAllWorkByTag("moodiary-reminders").result.get()
            manager.cancelUniqueWork("moodiary-reminder-recovery").result.get()
            database.close()
        }
    }
}
