package app.moodiary

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.moodiary.core.backup.PreparedRestore
import app.moodiary.feature.RestoreConfirmation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RestoreConfirmationTest {
    @get:Rule val ui = createComposeRule()
    @Test fun replacementRequiresExplicitConfirmationAndExplainsImpact() {
        var replacements = 0
        var cancellations = 0
        ui.setContent { MaterialTheme { RestoreConfirmation(PreparedRestore(File("unused"), 12, 3, "2026-09-27"), false, { replacements++ }, { cancellations++ }) } }
        ui.onNodeWithText("All current entries, customizations and goals will be replaced.", substring = true).assertExists()
        assertEquals(0, replacements)
        ui.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancellations)
        assertEquals(0, replacements)
        ui.onNodeWithText("Replace local data").performClick()
        assertEquals(1, replacements)
    }
    @Test fun inProgressRestoreCannotBeTriggeredTwice() {
        ui.setContent { MaterialTheme { RestoreConfirmation(PreparedRestore(File("unused"), 12, 3, "2026-09-27"), true, {}, {}) } }
        ui.onNodeWithText("Replace local data").assertIsNotEnabled()
        ui.onNodeWithText("Cancel").assertIsNotEnabled()
    }
}
