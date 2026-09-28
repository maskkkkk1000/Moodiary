package app.moodiary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.moodiary.core.security.PinStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class PrivacyAccessibilityTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun waitFor(text: String) = ui.waitUntil(30_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun pinSetupSurvivesActivityRecreationAndUnlocksWithCorrectPin() {
        waitFor("Your moments")
        val store = PinStore(ui.activity)
        check(!store.enabled) { "This test requires a dedicated device without an existing diary PIN" }
        try {
            ui.onNodeWithTag("nav_more").performClick()
            ui.onNodeWithTag("more_list").performScrollToNode(hasText("Privacy lock"))
            ui.onNodeWithText("Privacy lock").performClick()
            ui.onNodeWithText("Set up PIN").performClick()
            ui.onNodeWithText("New PIN (6–12 digits)").performTextInput("735190")
            ui.onNodeWithText("Confirm new PIN").performTextInput("735190")
            ui.onNodeWithText("Save", substring = false).performClick()
            waitFor("Change or remove PIN")
            ui.activityRule.scenario.recreate()
            waitFor("Your private space")
            ui.onNodeWithText("PIN", substring = false).performTextInput("000000")
            ui.onNodeWithText("Unlock", substring = false).performClick()
            waitFor("Incorrect PIN")
            ui.onNodeWithText("PIN", substring = false).performTextReplacement("735190")
            ui.onNodeWithText("Unlock", substring = false).performClick()
            ui.waitUntil(30_000) { ui.onAllNodesWithText("Your private space").fetchSemanticsNodes().isEmpty() }
        } finally {
            if (store.enabled) runBlocking { store.remove("735190") }
        }
    }
}
