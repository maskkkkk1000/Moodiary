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
import app.moodiary.domain.Entry
import app.moodiary.feature.JournalViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LanguageFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun waitFor(text: String) = ui.waitUntil(30_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun waitForTag(tag: String) = ui.waitUntil(30_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test fun languagePickerPersistsAndPreservesJournal() = runBlocking {
        waitForTag("nav_more")
        val originalLanguage = AppCompatDelegate.getApplicationLocales()
        lateinit var vm: JournalViewModel
        ui.runOnUiThread { vm = ViewModelProvider(ui.activity)[JournalViewModel::class.java] }
        val repository = vm.repository
        val mood = repository.snapshot().moods.first()
        val id = repository.saveEntry(Entry(timestamp = System.currentTimeMillis(), moodId = mood.id,
            note = "原文 remains unchanged — 语言测试", moodName = mood.name, moodScore = mood.score,
            moodColor = mood.color, moodIcon = mood.icon), emptySet(), emptyList())
        val original = repository.snapshot().entries.single { it.id == id }
        try {
            ui.onNodeWithTag("nav_more").performClick()
            ui.onNodeWithTag("more_list").performScrollToNode(hasText("Language / 语言"))
            ui.onNodeWithText("Language / 语言").performClick()
            waitForTag("language_zh-CN")
            ui.onNodeWithTag("language_zh-CN").performClick()
            waitFor("跟随系统")
            ui.onNodeWithTag("language_zh-CN").assertIsSelected()
            assertEquals("zh", ui.activity.resources.configuration.locales[0].language)
            capture("language-chinese")
            ui.onNodeWithText(ui.activity.getString(R.string.ui_back)).performClick()
            waitForTag("nav_entries")
            ui.onNodeWithTag("nav_entries").performClick()
            waitFor(ui.activity.getString(R.string.ui_your_moments))
            capture("timeline-chinese")
            ui.onNodeWithTag("nav_calendar").performClick()
            waitFor(ui.activity.getString(R.string.ui_the_days_together))
            capture("calendar-chinese")
            ui.onNodeWithTag("nav_more").performClick()
            ui.onNodeWithTag("more_list").performScrollToNode(hasText("Language / 语言"))
            ui.onNodeWithText("Language / 语言").performClick()
            waitFor("跟随系统")
            ui.activityRule.scenario.recreate()
            waitFor("跟随系统")
            ui.onNodeWithTag("language_zh-CN").assertIsSelected()
            assertEquals("zh-CN", AppCompatDelegate.getApplicationLocales().toLanguageTags())
            assertEquals(original, repository.snapshot().entries.single { it.id == id })

            ui.onNodeWithTag("language_en").performClick()
            waitFor("Follow system")
            ui.onNodeWithTag("language_en").assertIsSelected()
            assertEquals("en", ui.activity.resources.configuration.locales[0].language)
            capture("language-english")
            ui.activityRule.scenario.recreate()
            waitFor("Follow system")
            ui.onNodeWithTag("language_en").assertIsSelected()

            ui.onNodeWithTag("language_system").performClick()
            ui.waitUntil(30_000) { AppCompatDelegate.getApplicationLocales().isEmpty }
            ui.waitUntil(30_000) { ui.onAllNodes(hasTestTag("language_system") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("language_system").assertIsSelected()
            assertEquals(original, repository.snapshot().entries.single { it.id == id })
        } finally {
            repository.deleteEntry(id)
            ui.runOnUiThread { AppCompatDelegate.setApplicationLocales(originalLanguage) }
            // Locale restoration may recreate the Activity. Do not ask Espresso to wait for
            // a frame on the old root during teardown; the ActivityScenario rule closes it.
            ui.waitUntil(30_000) { AppCompatDelegate.getApplicationLocales().toLanguageTags() == originalLanguage.toLanguageTags() }
        }
    }

    @Test fun languageChangeKeepsUnsavedEntryDraft() {
        waitForTag("nav_editor")
        val originalLanguage = AppCompatDelegate.getApplicationLocales()
        val note = "Draft 草稿 must survive a language change"
        ui.onNodeWithTag("nav_editor").performClick()
        waitForTag("editor_list")
        try {
            val label = ui.activity.getString(R.string.ui_your_note)
            ui.onNodeWithTag("editor_list").performScrollToNode(hasText(label))
            ui.onNodeWithText(label).performTextInput(note)
            ui.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN")) }
            ui.waitUntil(30_000) { ui.activity.resources.configuration.locales[0].language == "zh" }
            waitForTag("editor_list")
            val chineseLabel = ui.activity.getString(R.string.ui_your_note)
            ui.onNodeWithTag("editor_list").performScrollToNode(hasText(chineseLabel))
            ui.onNode(hasSetTextAction() and hasText(note)).assertExists()
            ui.activityRule.scenario.recreate()
            waitForTag("editor_list")
            ui.onNodeWithTag("editor_list").performScrollToNode(hasText(chineseLabel))
            ui.onNode(hasSetTextAction() and hasText(note)).assertExists()
        } finally {
            ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
            val discard = ui.activity.getString(R.string.ui_discard_draft)
            waitFor(discard)
            ui.onNodeWithText(discard).performClick()
            waitForTag("nav_more")
            ui.runOnUiThread { AppCompatDelegate.setApplicationLocales(originalLanguage) }
            ui.waitUntil(30_000) { AppCompatDelegate.getApplicationLocales().toLanguageTags() == originalLanguage.toLanguageTags() }
        }
    }

    private fun capture(name: String) {
        ui.runOnUiThread { ui.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        try {
            ui.waitForIdle()
            val file = File(ui.activity.getExternalFilesDir(null), "$name.png")
            file.outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cp ${file.path} /sdcard/Download/moodiary-$name.png").use { ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        } finally { ui.runOnUiThread { ui.activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) } }
    }
}
