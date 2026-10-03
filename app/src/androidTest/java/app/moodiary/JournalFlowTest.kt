package app.moodiary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs against the real Hilt/Room app on an emulator or test device. */
@RunWith(AndroidJUnit4::class)
class JournalFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun waitFor(text: String) = ui.waitUntil(30_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun editorScrollTo(text: String) { ui.onNodeWithTag("editor_list").performScrollToNode(hasText(text)) }
    private fun screenshot(name: String) {
        ui.activity.runOnUiThread { ui.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        ui.waitForIdle()
        val directory = File(ui.activity.getExternalFilesDir(null), "verification").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cp ${File(directory, "$name.png").path} /sdcard/Download/moodiary-$name.png").use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes() }
        ui.activity.runOnUiThread { ui.activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    @Test fun createEditSearchDeleteAndCalendar() {
        waitFor("A moment to remember")
        ui.onNodeWithTag("mood_1").performClick()
        editorScrollTo("Your note")
        ui.onNodeWithText("Your note").performTextInput("UI integration moment")
        editorScrollTo("Save entry")
        ui.onNodeWithText("Save entry").performClick()
        waitFor("Your moments")
        waitFor("UI integration moment")
        screenshot("timeline")
        ui.onNodeWithText("Search notes or activities").performTextInput("UI integration")
        ui.onNodeWithText("Edit entry").performScrollTo().performClick()
        waitFor("Edit your moment")
        editorScrollTo("Your note")
        ui.onNodeWithText("Your note").performTextReplacement("UI integration edited")
        editorScrollTo("Save entry")
        ui.onNodeWithText("Save entry").performClick()
        waitFor("Your moments")
        waitFor("UI integration edited")
        ui.onNodeWithText("Delete", substring = false).performScrollTo().performClick()
        ui.onNodeWithText("Keep entry").assertExists()
        ui.onNodeWithText("Delete entry", substring = false).performClick()
        ui.onNodeWithTag("nav_calendar").performClick()
        waitFor("The days, together")
        ui.onNodeWithText("Previous").performClick()
        ui.onNodeWithText("Next").performClick()
        ui.onNodeWithText("Today", substring = false).performClick()
        screenshot("calendar")
        ui.onNodeWithTag("calendar_list").performScrollToNode(hasText("Add entry on this day"))
        ui.onNodeWithText("Add entry on this day").assertExists()
    }
    @Test fun goalsAndBackupDestinationsAreReachable() {
        val goalName = "UI daily goal ${System.nanoTime()}"
        ui.waitUntil(30_000) { ui.onAllNodesWithTag("nav_more").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("nav_more").performClick()
        ui.onNodeWithText("Goals & streaks").performClick()
        ui.onNodeWithText("New goal").performClick()
        ui.onNodeWithText("Goal name").performTextInput(goalName)
        ui.onNodeWithText("Save goal").performClick()
        ui.waitUntil(30_000) { ui.onAllNodesWithText("Save goal").fetchSemanticsNodes().isEmpty() }
        ui.onNode(hasScrollAction()).performScrollToNode(hasText(goalName))
        waitFor(goalName)
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Mark complete"))
        ui.onNodeWithText("Mark complete").performClick()
        waitFor("Undo completion")
        ui.onNodeWithText("Back", substring = false).performClick()
        ui.onNodeWithTag("more_list").performScrollToNode(hasText("Backup & restore"))
        ui.onNodeWithText("Backup & restore").performClick()
        waitFor("Your data, in your hands")
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Create local backup"))
        ui.onNodeWithText("Create local backup").assertIsDisplayed()
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Select backup to restore"))
        ui.onNodeWithText("Select backup to restore").assertIsDisplayed()
    }
    @Test fun managementSettingsAndPrivacyRoutesOpen() {
        ui.waitUntil(30_000) { ui.onAllNodesWithTag("nav_more").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("nav_more").performClick()
        val destinations = listOf(
            "Moods" to "Moods", "Activities" to "Activities", "Activity groups" to "Activity groups",
            "Note templates" to "Note templates", "Important days" to "Important days",
            "Achievements" to "Your journal grows", "Reminders" to "A gentle nudge",
            "Appearance" to "Appearance", "Privacy lock" to "Privacy lock",
            "Export" to "Export your journal", "Data integrity" to "Data integrity",
            "About & statistics methodology" to "Statistics methodology"
        )
        for ((link, heading) in destinations) {
            ui.onNodeWithTag("more_list").performScrollToNode(hasText(link))
            ui.onNodeWithText(link).performClick()
            if (link == "About & statistics methodology") ui.onNode(hasScrollAction()).performScrollToNode(hasText(heading))
            waitFor(heading)
            if (link == "Moods") screenshot("moods")
            ui.onNodeWithText("Back", substring = false).performClick()
        }
    }
}
