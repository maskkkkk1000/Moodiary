package app.moodiary

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.moodiary.core.designsystem.IconPicker
import app.moodiary.core.designsystem.MoodiaryTheme
import app.moodiary.domain.AppPreferences
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real picker state and scrolling independently of the journal database. */
@RunWith(AndroidJUnit4::class)
class IconPickerUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun customEmojiPreviewValidationApplyAndCancel() {
        ui.setContent {
            MoodiaryTheme(AppPreferences()) {
                var icon by remember { mutableStateOf("☀") }
                Column {
                    IconPicker(icon, { icon = it })
                    Text(icon, Modifier.testTag("selected_icon"))
                }
            }
        }
        ui.onNodeWithTag("icon_picker_open").performClick()
        ui.onNodeWithTag("icon_picker_preview").assertExists()
        ui.onNodeWithTag("icon_custom_input").performScrollTo().performTextReplacement("   ")
        ui.onNodeWithTag("icon_picker_apply").assertIsNotEnabled()
        ui.onNodeWithTag("icon_custom_input").performTextReplacement("🦊")
        ui.onNodeWithTag("icon_picker_apply").performClick()
        ui.onNodeWithTag("selected_icon").assertTextEquals("🦊")
        ui.onNodeWithTag("icon_picker_open").performClick()
        ui.onNodeWithTag("icon_choice_😀").performScrollTo().performClick()
        ui.onNodeWithTag("icon_picker_cancel").performClick()
        ui.onNodeWithTag("selected_icon").assertTextEquals("🦊")
    }

    @Test fun activityBuiltInIconHasStableIdAndSelectionState() {
        ui.setContent {
            MoodiaryTheme(AppPreferences(theme = "DARK")) {
                var icon by remember { mutableStateOf("📚") }
                Column {
                    IconPicker(icon, { icon = it }, allowMaterial = true)
                    Text(icon, Modifier.testTag("selected_icon"))
                }
            }
        }
        ui.onNodeWithTag("icon_picker_open").performClick()
        ui.onNodeWithTag("icon_picker_list").performScrollToNode(hasTestTag("icon_choice_material:book"))
        ui.onNodeWithTag("icon_choice_material:book").performClick().assertIsOn()
        ui.onNodeWithTag("icon_picker_apply").performClick()
        ui.onNodeWithTag("selected_icon").assertTextEquals("material:book")
        ui.onNodeWithTag("icon_picker_open").performClick()
        ui.onNodeWithTag("icon_picker_list").performScrollToNode(hasTestTag("icon_choice_material:book"))
        ui.onNodeWithTag("icon_choice_material:book").assertIsOn()
    }
}
