package app.moodiary

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.moodiary.feature.JournalViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class IterationAppearanceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText() }
    private fun tag(value: String) = ui.waitUntil(30_000) { ui.onAllNodesWithTag(value).fetchSemanticsNodes().isNotEmpty() }
    private fun capture(name: String) {
        ui.runOnUiThread { ui.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        try {
            ui.waitForIdle()
            val file=File(ui.activity.getExternalFilesDir(null),"$name.png")
            file.outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
            shell("cp ${file.path} /sdcard/Download/$name.png")
            check(shell("ls /sdcard/Download/$name.png").trim() == "/sdcard/Download/$name.png") { "Could not retain synthetic UI screenshot" }
        } finally { ui.runOnUiThread { ui.activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) } }
    }
    @Test fun chineseEditorAndPickerRemainUsableWithDarkThemeAndLargeText(): Unit = runBlocking {
        tag("editor_list")
        lateinit var vm: JournalViewModel
        ui.runOnUiThread { vm=ViewModelProvider(ui.activity)[JournalViewModel::class.java] }
        val old=vm.settings.preferences.first()
        val language=AppCompatDelegate.getApplicationLocales()
        val font=shell("settings get system font_scale").trim()
        try {
            ui.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN")) }
            ui.waitUntil(30_000) { ui.activity.resources.configuration.locales[0].language == "zh" }
            tag("editor_list")
            capture("iteration-editor-light")
            vm.settings.update { it.copy(theme="DARK") }
            shell("settings put system font_scale 1.8")
            ui.waitUntil(30_000) { ui.activity.resources.configuration.fontScale >= 1.7f }
            tag("editor_list")
            ui.onNodeWithTag("editor_list").performScrollToNode(hasTestTag("mood_1"))
            ui.onNodeWithTag("mood_1").assertIsDisplayed().performClick().assertIsSelected()
            ui.onNodeWithTag("editor_list").performScrollToIndex(0)
            capture("iteration-editor-dark-large")
            val save=ui.activity.getString(R.string.journal_save_entry)
            ui.onNodeWithTag("editor_list").performScrollToNode(hasText(save))
            ui.onNodeWithText(save).assertIsDisplayed().assertIsEnabled()
            ui.onNodeWithTag("nav_more").assertIsDisplayed().performClick()
            ui.onNodeWithTag("more_list").performScrollToNode(hasText(ui.activity.getString(R.string.iter_binary_goals)))
            ui.onNodeWithText(ui.activity.getString(R.string.iter_binary_goals)).performClick()
            tag("binary_create");ui.onNodeWithTag("binary_create").performClick()
            ui.onNodeWithTag("binary_editor").performScrollToNode(hasTestTag("icon_picker_open"))
            ui.onNodeWithTag("icon_picker_open").performClick()
            ui.onNodeWithTag("icon_picker_list").performScrollToNode(hasTestTag("icon_choice_😀"))
            ui.onNodeWithTag("icon_choice_😀").assertIsDisplayed().performClick()
            ui.onNodeWithTag("icon_picker_apply").assertIsDisplayed().performClick()
        } finally {
            shell("settings put system font_scale ${font.toFloatOrNull() ?: 1.0f}")
            vm.settings.update { old }
            ui.runOnUiThread { AppCompatDelegate.setApplicationLocales(language) }
            ui.waitUntil(30_000) { AppCompatDelegate.getApplicationLocales().toLanguageTags()==language.toLanguageTags() }
        }
    }
}
