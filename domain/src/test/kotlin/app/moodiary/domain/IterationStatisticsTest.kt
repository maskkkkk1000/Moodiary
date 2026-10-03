package app.moodiary.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.system.measureTimeMillis

class IterationStatisticsTest {
    private val zone = ZoneId.of("UTC")
    private val start = LocalDate.of(2024,2,26)
    private fun time(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun entry(id: Long, day: Int, mood: Long) = Entry(id,time(start.plusDays(day.toLong())),mood,"", "Mood",mood.toDouble(),0,"☀",0,0)
    private val data = JournalData(entries = listOf(entry(1,0,1),entry(2,1,2),entry(3,2,2),entry(4,3,1)),
        activities = listOf(Activity(10,name="Study"),Activity(20,name="Exercise")),
        entryActivities = listOf(EntryActivity(1,10),EntryActivity(2,20),EntryActivity(3,10),EntryActivity(3,20)))
    private fun ids(filters: StatisticsFilters, from: LocalDate? = null, through: LocalDate = start.plusDays(3)) = StatisticsEntryFilter.apply(data,filters,StatisticsDates(from,through),zone).map { it.id }
    @Test fun allCombinationsApplyDateMoodAndAnyActivity() {
        assertEquals(listOf(1L,2L,3L,4L),ids(StatisticsFilters()))
        assertEquals(listOf(3L,4L),ids(StatisticsFilters(),start.plusDays(2)))
        assertEquals(listOf(2L,3L),ids(StatisticsFilters(moodIds=setOf(2))))
        assertEquals(listOf(1L,3L),ids(StatisticsFilters(activityIds=setOf(10))))
        assertEquals(listOf(1L,2L,3L),ids(StatisticsFilters(activityIds=setOf(10,20))))
        assertEquals(listOf(3L),ids(StatisticsFilters(moodIds=setOf(2),activityIds=setOf(10))))
        assertEquals(listOf(3L),ids(StatisticsFilters(moodIds=setOf(2)),start.plusDays(2)))
        assertEquals(listOf(3L),ids(StatisticsFilters(activityIds=setOf(20)),start.plusDays(2)))
        assertEquals(listOf(3L),ids(StatisticsFilters(moodIds=setOf(2),activityIds=setOf(10,20)),start.plusDays(2)))
        assertEquals(emptyList<Long>(),ids(StatisticsFilters(moodIds=setOf(99))))
    }
    @Test fun moduleSelectionAvoidsUnselectedOutputsAndUsesFilteredEntries() {
        val result = StatisticsCalculator.calculate(data,StatisticsFilters(range=StatisticsRange.ALL,moodIds=setOf(2),modules=setOf(StatisticsModule.MOOD_TREND)),zone,start.plusDays(3))
        assertEquals(2,result.entries!!.entryCount)
        assertEquals(2,result.entries!!.trend.size)
        assertTrue(result.entries!!.moodDistribution.isEmpty())
        assertTrue(result.entries!!.nextDayAssociations.isEmpty())
        assertTrue(result.entries!!.combinations.isEmpty())
        assertNull(result.binaryGoals)
        assertTrue(result.goals.isEmpty())
        assertNull(StatisticsCalculator.calculate(data,StatisticsFilters(modules=emptySet()),zone,start).entries)
    }
    @Test fun binaryRatesExcludeUnsetAndSupportWeekMonthCustomAll() {
        val goal = BinaryGoal(1,"Read",createdAt=time(start))
        val records = listOf(1,1,1,0).mapIndexed { i,v -> BinaryGoalRecord((i+1).toLong(),1,start.plusDays(i.toLong()).toString(),v) }
        val week = BinaryGoalStatistics.summarize(listOf(goal),records,start,start.plusDays(6),zone)
        assertEquals(75.0,week.total.completionRate!!,0.00001)
        assertEquals(3L,week.total.unset)
        assertEquals(4,week.total.recorded)
        val missing = BinaryGoalStatistics.summarize(listOf(goal),records.filterNot { it.id == 3L },start,start.plusDays(3),zone)
        assertEquals(66.666666,missing.total.completionRate!!,0.00001)
        assertEquals(1L,missing.total.unset)
        assertEquals(75.0,week.weekly.single().completionRate!!,0.00001)
        assertEquals(75.0,week.monthly.first().completionRate!!,0.00001)
        val custom = BinaryGoalStatistics.summarize(listOf(goal),records,start.plusDays(2),start.plusDays(3),zone)
        assertEquals(50.0,custom.total.completionRate!!,0.0001)
        assertEquals(week.total.recorded,BinaryGoalStatistics.summarize(listOf(goal),records,null,start.plusDays(6),zone).total.recorded)
        assertNull(BinaryGoalStatistics.summarize(listOf(goal),emptyList(),start,start.plusDays(6),zone).total.completionRate)
    }
    @Test fun presetsCustomErrorsIndependentGoalFiltersAndLeapDay() {
        val leap=LocalDate.of(2024,2,29)
        assertEquals(LocalDate.of(2024,2,26),StatisticsFilters(range=StatisticsRange.THIS_WEEK).dates(leap).from)
        assertEquals(LocalDate.of(2024,2,1),StatisticsFilters(range=StatisticsRange.THIS_MONTH).dates(leap).from)
        assertEquals(leap.minusDays(6),StatisticsFilters(range=StatisticsRange.DAYS_7).dates(leap).from)
        try {StatisticsFilters(range=StatisticsRange.CUSTOM,customStart="2024-02-30",customEnd="2024-03-01").dates(leap);fail()} catch(_:java.time.DateTimeException){}
        try {StatisticsFilters(range=StatisticsRange.CUSTOM,customStart="2024-03-02",customEnd="2024-03-01").dates(leap);fail()} catch(_:IllegalArgumentException){}
        val goals=listOf(BinaryGoal(1,"A",createdAt=time(start)),BinaryGoal(2,"B",createdAt=time(start)))
        val records=listOf(BinaryGoalRecord(1,1,start.toString(),1),BinaryGoalRecord(2,2,start.toString(),0))
        val filtered=StatisticsCalculator.calculate(data.copy(binaryGoals=goals,binaryGoalRecords=records),StatisticsFilters(range=StatisticsRange.ALL,moodIds=setOf(999),binaryGoalIds=setOf(1),modules=setOf(StatisticsModule.BINARY_GOAL_COMPLETION)),zone,leap)
        assertEquals(100.0,filtered.binaryGoals!!.total.completionRate!!,0.0)
        assertEquals(setOf(1L),filtered.binaryGoals!!.byGoal.keys)
    }
    @Test fun tenHundredAndThousandGoalsWithYearOfOutcomesStayBounded() {
        for (size in listOf(10,100,1000)) {
            val goals=(1..size).map { BinaryGoal(it.toLong(),"Goal $it",createdAt=time(start)) }
            val rows=goals.flatMap { g -> (0 until 365).map { day -> BinaryGoalRecord(g.id*1000+day,g.id,start.plusDays(day.toLong()).toString(),day%2) } }
            val elapsed=measureTimeMillis {
                val stats=BinaryGoalStatistics.summarize(goals,rows,start,start.plusDays(364),zone)
                assertEquals(size*365,stats.total.recorded);assertEquals(0L,stats.total.unset)
                assertEquals(size,stats.byGoal.size)
            }
            println("BINARY_PERF goals=$size rows=${rows.size} elapsedMs=$elapsed")
            assertTrue("Statistics should remain bounded: $elapsed ms",elapsed<15000)
        }
    }
}
