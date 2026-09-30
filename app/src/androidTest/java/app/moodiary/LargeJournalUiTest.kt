package app.moodiary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.moodiary.feature.JournalViewModel
import app.moodiary.domain.Entry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class LargeJournalUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun waitFor(text: String) = ui.waitUntil(60_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    @Test fun fiftyThousandEntriesRenderSearchAndNavigate() = runBlocking {
        waitFor("Your moments")
        lateinit var vm: JournalViewModel
        ui.runOnUiThread { vm = ViewModelProvider(ui.activity)[JournalViewModel::class.java] }
        val original = vm.repository.snapshot()
        try {
            val mood = original.moods.first()
            val now = System.currentTimeMillis()
            val entries = (1L..50_000L).map { id -> Entry(id, now - id * 60_000, mood.id, "Scale record $id", mood.name, mood.score, mood.color, mood.icon, 0, 0) }
            val timeline = measureTimeMillis {
                vm.repository.replaceAll(original.copy(entries = entries, entryActivities = emptyList(), photos = emptyList()))
                waitFor("50000 entries")
            }
            val search = measureTimeMillis {
                ui.onNodeWithText("Search notes or activities").performTextInput("Scale record 49999")
                waitFor(ui.activity.resources.getQuantityString(R.plurals.journal_entry_count, 1, 1))
                ui.onAllNodesWithText("Scale record 49999", substring = false).assertCountEquals(2)
            }
            // Finish the IME transition before navigating to a different lazy layout.
            ui.runOnUiThread {
                androidx.core.view.WindowInsetsControllerCompat(ui.activity.window, ui.activity.window.decorView)
                    .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
            }
            ui.waitForIdle()
            val calendar = measureTimeMillis {
                ui.onNodeWithTag("nav_calendar").performClick(); waitFor("The days, together")
                ui.waitForIdle()
            }
            val statistics = measureTimeMillis {
                ui.onNodeWithTag("nav_statistics").performClick(); waitFor(ui.activity.getString(R.string.ui_patterns_with_perspective))
                ui.waitUntil(60_000) { ui.onAllNodesWithText("entries", substring = true).fetchSemanticsNodes().isNotEmpty() }
            }
            println("ANDROID_UI_PERF rows=50000 timelineIncludingReplaceMs=$timeline searchMs=$search calendarNavigationMs=$calendar statisticsNavigationMs=$statistics")
        } finally { vm.repository.replaceAll(original) }
    }
}
