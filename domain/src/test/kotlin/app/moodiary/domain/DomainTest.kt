package app.moodiary.domain

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DomainTest {
    private val utc = ZoneId.of("UTC")
    private val mood = Mood(1, "Steady", 3.0, createdAt = 0, updatedAt = 0)
    private val activity = Activity(1, name = "Walking", createdAt = 0, updatedAt = 0)
    private fun entry(id: Long, day: String, score: Double = 3.0, note: String = "") =
        Entry(id, Instant.parse("${day}T12:00:00Z").toEpochMilli(), 1, note, "Steady", score, mood.color, mood.icon, 0, 0)
    private fun data(entries: List<Entry> = emptyList(), links: List<EntryActivity> = emptyList()) = JournalData(moods = listOf(mood), activities = listOf(activity), entries = entries, entryActivities = links)
    private fun goal(type: GoalType = GoalType.DAILY, start: String = "2024-01-01", target: Int = 1) = Goal(1, "Walk", 1, type, target, start, createdAt = 0, updatedAt = 0)
    private fun complete(day: String, id: Long = 1) = GoalCompletion(id, 1, day, 0)

    @Test fun emptyStatisticsAreFiniteAndNullable() {
        val summary = Statistics.summarize(data(), utc)
        assertEquals(0, summary.entryCount); assertNull(summary.average); assertTrue(summary.trend.isEmpty())
        assertNull(summary.sameEntryAssociations.single().difference)
        assertEquals(Confidence.INSUFFICIENT, summary.sameEntryAssociations.single().confidence)
    }

    @Test fun dailyMeanAndEntryFrequencyKeepTheirDenominators() {
        val rows = listOf(entry(1,"2024-02-28",1.0), entry(2,"2024-02-28",5.0), entry(3,"2024-02-29",4.0))
        val summary = Statistics.summarize(data(rows, listOf(EntryActivity(1,1), EntryActivity(2,1))), utc)
        assertEquals(10.0 / 3, summary.average!!, 0.00001)
        assertEquals(3.0, summary.trend.first().average, 0.0)
        assertEquals(2, summary.activityFrequency.single().entries); assertEquals(1, summary.activityFrequency.single().days)
        assertEquals(-1.0, summary.sameEntryAssociations.single().difference!!, 0.0)
        assertEquals(3, summary.moodDistribution.getValue(1))
    }

    @Test fun nextDayDeduplicatesSourceDaysAndRequiresBothDatesInRange() {
        val rows = listOf(entry(1,"2024-12-30",1.0), entry(2,"2024-12-30",3.0), entry(3,"2024-12-31",5.0), entry(4,"2025-01-01",1.0), entry(5,"2025-01-03",4.0))
        val d = data(rows, listOf(EntryActivity(1,1), EntryActivity(2,1), EntryActivity(5,1)))
        val association = Statistics.summarize(d,utc).nextDayAssociations.single()
        assertEquals(1, association.withCount); assertEquals(1, association.withoutCount)
        assertEquals(4.0, association.difference!!, 0.0)
        val restricted = Statistics.summarize(d,utc,LocalDate.parse("2024-12-31"),LocalDate.parse("2025-01-01")).nextDayAssociations.single()
        assertEquals(0, restricted.withCount); assertEquals(1, restricted.withoutCount); assertNull(restricted.difference)
    }

    @Test fun confidenceUsesTheSmallerComparisonGroup() {
        listOf(0 to Confidence.INSUFFICIENT, 4 to Confidence.INSUFFICIENT, 5 to Confidence.LOW, 14 to Confidence.LOW, 15 to Confidence.MEDIUM, 29 to Confidence.MEDIUM, 30 to Confidence.HIGH).forEach { (count, expected) -> assertEquals(expected, Confidence.forSamples(count)) }
        assertEquals(Confidence.INSUFFICIENT, Association(1, 50, 2, 3.0, 2.0).confidence)
    }

    @Test fun composedSearchFindsActivityNamesPhotosAndImportantDates() {
        val rows = listOf(entry(1,"2024-02-29", note="Hello, 世界"), entry(2,"2024-03-01", note="walking"))
        val d = data(rows,listOf(EntryActivity(1,1))).copy(photos=listOf(EntryPhoto(1,1,"photo.jpg",createdAt=0)),importantDays=listOf(ImportantDay(1,"2024-02-29","Leap day")))
        val filter = SearchFilter(query="WALK",from=LocalDate.parse("2024-02-29"),through=LocalDate.parse("2024-03-01"),moodId=1,activityId=1,hasPhoto=true,importantOnly=true)
        assertEquals(listOf(1L), EntrySearch.filter(d,filter,utc).map { it.id })
        assertTrue(EntrySearch.filter(d,filter.copy(hasPhoto=false),utc).isEmpty())
        assertEquals(listOf(1L), EntrySearch.filter(d,SearchFilter(query="世界"),utc).map { it.id })
    }

    @Test fun timezoneAndDstUseLocalDatesNotTwentyFourHourOffsets() {
        val row = entry(1,"2024-03-10").copy(timestamp=Instant.parse("2024-03-10T07:30:00Z").toEpochMilli())
        assertEquals(LocalDate.parse("2024-03-09"),row.date(ZoneId.of("America/Los_Angeles")))
        assertEquals(LocalDate.parse("2024-03-10"),row.date(ZoneId.of("Asia/Shanghai")))
        val after = row.copy(id=2,timestamp=Instant.parse("2024-03-11T06:30:00Z").toEpochMilli())
        assertEquals(1,Statistics.summarize(data(listOf(row,after),listOf(EntryActivity(1,1))),ZoneId.of("America/Los_Angeles")).nextDayAssociations.single().withCount)
    }

    @Test fun aggregationModesPreserveMultipleEntries() {
        val rows=listOf(entry(1,"2024-01-01",1.0),entry(2,"2024-01-01",5.0))
        assertEquals(3.0,Statistics.aggregate(rows,"AVERAGE")!!,0.0)
        assertEquals(5.0,Statistics.aggregate(rows,"LATEST")!!,0.0)
        assertEquals(1.0,Statistics.aggregate(rows,"LOWEST")!!,0.0)
    }

    @Test fun automaticCompletionIsIdempotentAndTracksEditsAndDeletes() {
        val d=data(listOf(entry(1,"2024-01-01"),entry(2,"2024-01-01")),listOf(EntryActivity(1,1),EntryActivity(2,1))).copy(goals=listOf(goal()))
        val expected=setOf(GoalDate(1,"2024-01-01"))
        assertEquals(expected,GoalEngine.desiredLinkedCompletions(d,utc))
        assertEquals(expected,GoalEngine.desiredLinkedCompletions(d.copy(entries=d.entries.drop(1)),utc))
        val moved=d.copy(entries=listOf(entry(1,"2024-01-02")))
        assertEquals(setOf(GoalDate(1,"2024-01-02")),GoalEngine.desiredLinkedCompletions(moved,utc))
        assertTrue(GoalEngine.desiredLinkedCompletions(d.copy(entries=emptyList()),utc).isEmpty())
    }

    @Test fun weekdayGoalIgnoresUnscheduledDays() {
        val goal=goal(GoalType.WEEKDAYS); val schedules=listOf(GoalSchedule(1,1),GoalSchedule(1,3),GoalSchedule(1,5))
        val done=listOf(complete("2024-01-01"),complete("2024-01-03",2),complete("2024-01-05",3))
        val metrics=GoalEngine.metrics(goal,schedules,done,LocalDate.parse("2024-01-07"))
        assertEquals(3,metrics.currentStreak); assertEquals(3,metrics.longestStreak); assertEquals(100.0,metrics.completionRate,0.0)
    }

    @Test fun pendingTodayDoesNotBreakStreakButYesterdayDoes() {
        val done=listOf(complete("2024-01-01"),complete("2024-01-02",2))
        assertEquals(2,GoalEngine.metrics(goal(),emptyList(),done,LocalDate.parse("2024-01-03")).currentStreak)
        assertEquals(0,GoalEngine.metrics(goal(),emptyList(),done,LocalDate.parse("2024-01-04")).currentStreak)
        assertEquals(2,GoalEngine.metrics(goal(),emptyList(),done,LocalDate.parse("2024-01-04")).longestStreak)
    }

    @Test fun weeklyCountUsesDistinctDatesAndIsoMondayWeeks() {
        val goal=goal(GoalType.WEEKLY_COUNT,target=3)
        val done=listOf(complete("2024-01-01"),complete("2024-01-02",2),complete("2024-01-03",3),complete("2024-01-03",4))
        val metrics=GoalEngine.metrics(goal,emptyList(),done,LocalDate.parse("2024-01-09"))
        assertEquals(1,metrics.currentStreak); assertEquals(1,metrics.longestStreak); assertEquals(3,metrics.completed); assertEquals(6,metrics.expected)
        assertEquals(50.0,metrics.completionRate,0.0)
    }

    @Test fun goalEndAndStartBoundariesAndLeapDayAreHonored() {
        val g=goal(start="2024-02-28").copy(endDate="2024-03-01")
        val done=listOf(complete("2024-02-28"),complete("2024-02-29",2),complete("2024-03-01",3),complete("2024-03-02",4))
        assertEquals(3,GoalEngine.metrics(g,emptyList(),done,LocalDate.parse("2024-03-03")).completed)
        assertEquals(0,GoalEngine.metrics(g,emptyList(),done,LocalDate.parse("2024-01-01")).expected)
    }

    @Test fun achievementsUseRecordedDaysRatherThanEntryCountForDayBadges() {
        val rows=(1L..100L).map { entry(it,"2024-01-01") }
        val achieved=JournalAchievements.evaluate(rows,utc,LocalDate.parse("2024-01-02")).associateBy { it.id }
        assertTrue(achieved.getValue("entries100").unlocked); assertFalse(achieved.getValue("days7").unlocked)
        assertEquals(Streak(1,1),JournalAchievements.streak(rows,utc,LocalDate.parse("2024-01-02")))
        assertEquals(0,JournalAchievements.streak(rows,utc,LocalDate.parse("2024-01-03")).current)
    }

    @Test fun backupRoundTripPreservesEveryEntityAndUnicodePhotoBytes() {
        val d=data(listOf(entry(1,"2024-01-01",note="你好\n\"quoted\"")),listOf(EntryActivity(1,1))).copy(
            groups=listOf(ActivityGroup(1,"Outdoors",createdAt=0,updatedAt=0)),activities=listOf(activity.copy(groupId=1)),
            photos=listOf(EntryPhoto(1,1,"picture.jpg",createdAt=0)),goals=listOf(goal(GoalType.WEEKDAYS)),schedules=listOf(GoalSchedule(1,1)),
            completions=listOf(complete("2024-01-01")),reminders=listOf(Reminder(1,ReminderType.GOAL,1,createdAt=0,updatedAt=0)),
            templates=listOf(NoteTemplate(1,"Reflect","今天")),importantDays=listOf(ImportantDay(1,"2024-01-01","New year")))
        val preferences=AppPreferences(theme="DARK",palette="OCEAN")
        val bytes=byteArrayOf(1,2,3,4,5)
        val output=ByteArrayOutputStream(); BackupCodec.write(output,d,preferences,{ByteArrayInputStream(bytes)})
        val restored=BackupCodec.read(ByteArrayInputStream(output.toByteArray()))
        assertEquals(d,restored.data); assertEquals(preferences,restored.preferences); assertArrayEquals(bytes,restored.photos.getValue("picture.jpg"))
    }

    @Test fun backupRejectsTraversalAndUnexpectedPaths() {
        listOf("../database.json","photos/../../secret","photos\\secret","/photos/a.jpg","photos/a/../b.jpg","photos/..jpg").forEach { path ->
            assertFalse(DataValidation.isManagedPhotoPath(path)); rejects { BackupCodec.read(ByteArrayInputStream(archive(mapOf(path to "attack".toByteArray())))) }
        }
    }

    @Test fun backupRejectsBombsUnsupportedVersionsAndMissingFiles() {
        rejects { BackupCodec.read(ByteArrayInputStream(archive(mapOf("database.json" to ByteArray(500)))),BackupLimits(totalBytes=100, databaseBytes=100)) }
        rejects { BackupCodec.read(ByteArrayInputStream(archive(mapOf("metadata.json" to "{\"formatVersion\":999,\"createdAt\":\"2024-01-01T00:00:00Z\"}".toByteArray())))) }
        rejects { BackupCodec.read(ByteArrayInputStream(archive(emptyMap()))) }
    }

    @Test fun backupRejectsMissingReferencedPhotoAndChecksumMismatch() {
        val d=data(listOf(entry(1,"2024-01-01"))).copy(photos=listOf(EntryPhoto(1,1,"a.jpg",createdAt=0)))
        val files=mapOf("metadata.json" to "{\"formatVersion\":1,\"createdAt\":\"2024-01-01T00:00:00Z\",\"photoSha256\":{\"photos/a.jpg\":\"bad\"}}".toByteArray(),"database.json" to ExportCodec.json(d).toByteArray(),"settings.json" to "{}".toByteArray())
        rejects { BackupCodec.read(ByteArrayInputStream(archive(files))) }
        rejects { BackupCodec.read(ByteArrayInputStream(archive(files+("photos/a.jpg" to byteArrayOf(1))))) }
    }

    @Test fun validationRejectsOrphansDuplicatesNonfiniteValuesAndInvalidSettings() {
        val row=entry(1,"2024-01-01")
        assertTrue(DataValidation.validate(data(listOf(row,row))).any { it.contains("Duplicate entry IDs") })
        assertTrue(DataValidation.validate(data(listOf(row.copy(moodId=999)))).any { it.contains("missing mood") })
        assertTrue(DataValidation.validate(data().copy(moods=listOf(mood.copy(score=Double.NaN)))).isNotEmpty())
        assertTrue(DataValidation.validate(data(),AppPreferences(relockMinutes=-1)).isNotEmpty())
        assertTrue(DataValidation.validate(data().copy(importantDays=listOf(ImportantDay(1,"2023-02-29","Invalid")))).isNotEmpty())
        assertTrue(DataValidation.validate(data().copy(goals=listOf(goal()),completions=listOf(complete("2023-12-31").copy(source=CompletionSource.LINKED_ACTIVITY)))).isNotEmpty())
    }

    @Test fun csvEscapesUnicodeQuotesNewlinesAndFormulaCells() {
        val note="=SUM(1,2)\n你好 \"quoted\""
        val d=data(listOf(entry(1,"2024-01-01",note=note)),listOf(EntryActivity(1,1))).copy(activities=listOf(activity.copy(name="walk | run, \"outside\"")))
        val csv=ExportCodec.csv(d,utc)
        assertTrue(csv.startsWith("EntryId,Date,Time,Timestamp")); assertTrue(csv.contains("\"'=SUM(1,2)\n你好 \"\"quoted\"\"\""))
        assertTrue(csv.contains("walk | run,")); assertTrue(csv.endsWith("\r\n"))
        assertEquals(d,BackupCodec.json.decodeFromString<JournalData>(ExportCodec.json(d)))
    }

    private fun archive(files: Map<String,ByteArray>):ByteArray {
        val output=ByteArrayOutputStream()
        ZipOutputStream(output).use { zip -> files.forEach { (name,bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
        return output.toByteArray()
    }
    private fun rejects(block:()->Unit) { try { block(); fail("Expected validation failure") } catch (_: IllegalArgumentException) { } }
}
