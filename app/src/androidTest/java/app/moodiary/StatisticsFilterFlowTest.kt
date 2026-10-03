package app.moodiary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.moodiary.core.datastore.StatisticsFilterStore
import app.moodiary.domain.*
import app.moodiary.feature.JournalViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatisticsFilterFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun tag(name: String) = ui.waitUntil(30_000) { ui.onAllNodesWithTag(name).fetchSemanticsNodes().isNotEmpty() }
    @Test fun moodAndModuleSelectionsPersistAcrossNavigationAndActivityRestart() = runBlocking {
        tag("nav_statistics")
        lateinit var vm: JournalViewModel
        ui.runOnUiThread { vm = ViewModelProvider(ui.activity)[JournalViewModel::class.java] }
        val original=vm.repository.snapshot()
        val store=StatisticsFilterStore(ui.activity)
        val oldFilters=store.filters.first()
        val first=original.moods[0];val second=original.moods[1]
        try {
            store.reset()
            vm.repository.replaceAll(original.copy(entries=listOf(
                Entry(987601,System.currentTimeMillis(),first.id,"A",first.name,first.score,first.color,first.icon),
                Entry(987602,System.currentTimeMillis(),second.id,"B",second.name,second.score,second.color,second.icon)
            ),entryActivities=emptyList(),photos=emptyList()))
            ui.onNodeWithTag("nav_statistics").performClick();tag("stats_filter_mood")
            ui.onNodeWithTag("stats_filter_mood").performClick();tag("stats_mood_${first.id}")
            ui.onNodeWithTag("stats_mood_${first.id}").performClick()
            ui.waitUntil(30_000) { ui.onAllNodes(hasTestTag("stats_mood_${first.id}") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("stats_close_filters").performClick()
            tag("stats_entry_summary")
            ui.waitUntil(30_000) { ui.onAllNodesWithText(ui.activity.getString(R.string.stats_entry_count,1)).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("stats_filter_modules").performClick();tag("stats_select_MOOD_TREND")
            ui.onNodeWithTag("stats_select_MOOD_TREND").performClick()
            ui.waitUntil(30_000) { ui.onAllNodes(hasTestTag("stats_select_MOOD_TREND") and isSelected()).fetchSemanticsNodes().isEmpty() }
            ui.onNodeWithTag("stats_close_filters").performClick()
            ui.onNodeWithTag("stats_module_MOOD_TREND").assertDoesNotExist()
            ui.onNodeWithTag("nav_entries").performClick()
            ui.onNodeWithTag("nav_statistics").performClick();tag("statistics_list")
            ui.activityRule.scenario.recreate();tag("statistics_list")
            assertEquals(setOf(first.id),store.filters.first().moodIds)
            assertFalse(StatisticsModule.MOOD_TREND in store.filters.first().modules)
            ui.onNodeWithTag("stats_reset").performClick()
            ui.waitUntil(30_000) { ui.onAllNodesWithText(ui.activity.getString(R.string.stats_entry_count,2)).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(StatisticsFilters(),store.filters.first())
        } finally { store.update { oldFilters }; vm.repository.replaceAll(original) }
    }
}
