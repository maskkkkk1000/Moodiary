package app.moodiary.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ReminderScheduleTest {
    private val utc = ZoneId.of("UTC")
    @Test fun selectedWeekdayAndYearBoundary() {
        val reminder = Reminder(hour = 9, daysOfWeek = setOf(1))
        assertEquals(Instant.parse("2026-01-05T09:00:00Z"), ReminderSchedule.next(reminder, Instant.parse("2025-12-31T23:00:00Z"), utc))
    }
    @Test fun exactMinuteMovesToNextSelectedDate() {
        val reminder = Reminder(hour = 9)
        assertEquals(Instant.parse("2024-02-29T09:00:00Z"), ReminderSchedule.next(reminder, Instant.parse("2024-02-28T09:00:00Z"), utc))
    }
    @Test fun daylightSavingGapMovesForwardAndOverlapDoesNotDoubleFire() {
        val zone = ZoneId.of("America/New_York")
        assertEquals(Instant.parse("2024-03-10T07:30:00Z"), ReminderSchedule.next(Reminder(hour = 2, minute = 30), Instant.parse("2024-03-10T05:00:00Z"), zone))
        assertEquals(Instant.parse("2024-11-04T06:30:00Z"), ReminderSchedule.next(Reminder(hour = 1, minute = 30), Instant.parse("2024-11-03T05:45:00Z"), zone))
    }
    @Test fun timezoneChangeUsesNewWallClock() {
        val now = Instant.parse("2026-09-27T01:00:00Z")
        assertEquals(Instant.parse("2026-09-27T12:00:00Z"), ReminderSchedule.next(Reminder(), now, ZoneId.of("Asia/Shanghai")))
        assertEquals(Instant.parse("2026-09-27T20:00:00Z"), ReminderSchedule.next(Reminder(), now, utc))
    }
    @Test fun disabledInvalidAndMissingGoalsNeverSchedule() {
        val now = Instant.parse("2026-09-27T01:00:00Z")
        listOf(Reminder(enabled = false), Reminder(daysOfWeek = emptySet()), Reminder(hour = 24), Reminder(type = ReminderType.GOAL, targetId = 1)).forEach {
            assertNull(ReminderSchedule.next(it, now, utc))
        }
    }
    @Test fun goalStartEndAndArchiveAreRespected() {
        val now = Instant.parse("2026-09-27T01:00:00Z")
        val reminder = Reminder(type = ReminderType.GOAL, targetId = 2, hour = 9)
        val goal = Goal(id = 2, name = "Walk", startDate = "2026-10-01", endDate = "2026-10-02")
        assertEquals(Instant.parse("2026-10-01T09:00:00Z"), ReminderSchedule.next(reminder, now, utc, goal))
        assertNull(ReminderSchedule.next(reminder, Instant.parse("2026-10-02T09:00:00Z"), utc, goal))
        assertNull(ReminderSchedule.next(reminder, now, utc, goal.copy(isArchived = true)))
    }
}
