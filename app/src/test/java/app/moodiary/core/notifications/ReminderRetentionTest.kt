package app.moodiary.core.notifications

import app.moodiary.domain.Goal
import app.moodiary.domain.Reminder
import app.moodiary.domain.ReminderType
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ReminderRetentionTest {
    private val zone = ZoneId.of("UTC")
    private val due = Instant.parse("2026-09-27T20:00:00Z")
    private val reminder = Reminder(id = 7, hour = 20, createdAt = 1, updatedAt = 1)
    private fun tag(item: Reminder = reminder, goal: Goal? = null) =
        "moodiary-reminder-${item.id}-${due.toEpochMilli()}-${ReminderScheduler.signature(item, goal, zone)}"

    @Test fun delayedUnchangedOccurrenceSurvivesIncludingSixHourBoundary() {
        assertTrue(ReminderScheduler.retainOverdue(tag(), mapOf(7L to reminder), emptyMap(), due, zone))
        assertTrue(ReminderScheduler.retainOverdue(tag(), mapOf(7L to reminder), emptyMap(), due.plusSeconds(60), zone))
        assertTrue(ReminderScheduler.retainOverdue(tag(), mapOf(7L to reminder), emptyMap(), due.plusSeconds(6 * 3600), zone))
        assertFalse(ReminderScheduler.retainOverdue(tag(), mapOf(7L to reminder), emptyMap(), due.plusSeconds(6 * 3600).plusMillis(1), zone))
    }

    @Test fun editsDisableDeletionAndTimezoneChangeInvalidateDelayedWork() {
        val now = due.plusSeconds(60)
        assertFalse(ReminderScheduler.retainOverdue(tag(), mapOf(7L to reminder.copy(message = "New message")), emptyMap(), now, zone))
        assertFalse(ReminderScheduler.retainOverdue(tag(), mapOf(7L to reminder.copy(enabled = false)), emptyMap(), now, zone))
        assertFalse(ReminderScheduler.retainOverdue(tag(), emptyMap(), emptyMap(), now, zone))
        assertFalse(ReminderScheduler.retainOverdue(tag(), mapOf(7L to reminder), emptyMap(), now, ZoneId.of("Asia/Shanghai")))
    }

    @Test fun archivedOrMissingGoalInvalidatesDelayedGoalReminder() {
        val goal = Goal(id = 3, name = "Walk", startDate = "2026-01-01")
        val item = reminder.copy(type = ReminderType.GOAL, targetId = 3)
        val signature = tag(item, goal)
        assertTrue(ReminderScheduler.retainOverdue(signature, mapOf(7L to item), mapOf(3L to goal), due.plusSeconds(60), zone))
        assertFalse(ReminderScheduler.retainOverdue(signature, mapOf(7L to item), mapOf(3L to goal.copy(isArchived = true)), due.plusSeconds(60), zone))
        assertFalse(ReminderScheduler.retainOverdue(signature, mapOf(7L to item), emptyMap(), due.plusSeconds(60), zone))
    }

    @Test fun futureAndMalformedTagsAreNotRetainedAsOverdue() {
        assertFalse(ReminderScheduler.retainOverdue(tag(), mapOf(7L to reminder), emptyMap(), due.minusMillis(1), zone))
        assertFalse(ReminderScheduler.retainOverdue("moodiary-reminder-overflow-xxx", mapOf(7L to reminder), emptyMap(), due, zone))
        val invalidDue = "moodiary-reminder-7-999999999999999999999999-${ReminderScheduler.signature(reminder, null, zone)}"
        assertFalse(ReminderScheduler.retainOverdue(invalidDue, mapOf(7L to reminder), emptyMap(), due, zone))
    }

    @Test fun notificationsDeduplicateRetriesButAlertForDifferentOccurrences() {
        val today = ReminderWorker.notificationTag(7, due.toEpochMilli())
        assertEquals(today, ReminderWorker.notificationTag(7, due.toEpochMilli()))
        assertNotEquals(today, ReminderWorker.notificationTag(7, due.plusSeconds(86400).toEpochMilli()))
        assertNotEquals(today, ReminderWorker.notificationTag(8, due.toEpochMilli()))
    }
}
