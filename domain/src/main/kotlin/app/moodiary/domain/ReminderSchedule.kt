package app.moodiary.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reminders follow the device's current wall clock, not a fixed 24-hour interval.
 * A DST gap shifts to the first corresponding valid time; an overlap uses its earlier offset
 * and fires at most once for that local date. A missing/archived goal disables its reminders.
 */
object ReminderSchedule {
    fun next(reminder: Reminder, now: Instant, zone: ZoneId, goal: Goal? = null): Instant? {
        if (!reminder.enabled || reminder.hour !in 0..23 || reminder.minute !in 0..59 ||
            reminder.daysOfWeek.isEmpty() || reminder.daysOfWeek.any { it !in 1..7 }) return null
        if (reminder.type == ReminderType.GOAL &&
            (goal == null || goal.id != reminder.targetId || goal.isArchived)) return null
        val from = if (reminder.type == ReminderType.GOAL) {
            runCatching { LocalDate.parse(goal!!.startDate) }.getOrNull() ?: return null
        } else null
        val through = if (reminder.type == ReminderType.GOAL && goal?.endDate != null) {
            runCatching { LocalDate.parse(goal.endDate) }.getOrNull() ?: return null
        } else null
        val today = now.atZone(zone).toLocalDate()
        val firstDate = if (from != null && from > today) from else today
        for (offset in 0L..7L) {
            val date = firstDate.plusDays(offset)
            if (through != null && date > through) return null
            if (date.dayOfWeek.value !in reminder.daysOfWeek) continue
            val candidate = date.atTime(reminder.hour, reminder.minute).atZone(zone).toInstant()
            if (candidate > now) return candidate
        }
        return null
    }
}
