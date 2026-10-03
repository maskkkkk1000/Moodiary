package app.moodiary

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.moodiary.core.datastore.DraftStore
import app.moodiary.domain.Entry
import app.moodiary.feature.JournalViewModel
import app.moodiary.feature.entryeditor.EntryDraft
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IterationNavigationTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun tag(name: String) {
        val errors = mutableListOf<String>()
        lateinit var vm: JournalViewModel
        ui.runOnUiThread { vm = ViewModelProvider(ui.activity)[JournalViewModel::class.java] }
        try { ui.waitUntil(30_000) {
            vm.error.value?.let { if (it !in errors) errors.add(it) }
            ui.onAllNodesWithTag(name).fetchSemanticsNodes().isNotEmpty()
        } } catch (failure: Throwable) { throw AssertionError("Missing $name; ready=${vm.ready.value}, recovery=${vm.recoveryRequired.value}, errors=$errors\n${ui.onRoot().printToString()}\n$failure") }
    }
    private fun destination(name: String) {
        // Preserve launcher action/categories, as real notification launch intents do; otherwise
        // ActivityScenario cannot match the recreated Activity to its original launch intent.
        ui.runOnUiThread { ui.activity.startActivity(Intent(ui.activity.intent).setClass(ui.activity, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(StartupNavigation.EXTRA_DESTINATION, name)) }
    }

    @Test fun normalLaunchEditorAndNotificationDestinationsSurviveRecreation() {
        tag("editor_list")
        ui.onNodeWithTag("editor_list").assertExists()
        ui.activityRule.scenario.recreate()
        tag("editor_list")
        destination("goals")
        ui.waitUntil(30_000) { ui.onAllNodesWithText(ui.activity.getString(R.string.ui_small_steps)).fetchSemanticsNodes().isNotEmpty() }
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText(ui.activity.getString(R.string.ui_small_steps)).assertExists()
        destination("binary_goals")
        tag("binary_list")
        destination("entry")
        tag("editor_list")
    }

    @Test fun startupSelectsNewestValidSavedDraftAndPreservesDraftText() = runBlocking {
        tag("editor_list")
        lateinit var vm: JournalViewModel
        ui.runOnUiThread { vm = ViewModelProvider(ui.activity)[JournalViewModel::class.java] }
        val original = vm.repository.snapshot()
        val drafts = DraftStore(ui.activity)
        val mood = original.moods.first()
        val id = vm.repository.saveEntry(Entry(timestamp = System.currentTimeMillis(), moodId = mood.id, note = "saved entry", moodName = mood.name, moodScore = mood.score, moodColor = mood.color, moodIcon = mood.icon), emptySet(), emptyList())
        try {
            drafts.write(id, Json.encodeToString(EntryDraft(id = id, moodId = mood.id, note = "Recovered edit 草稿", loaded = true)))
            assertEquals("editor/$id?date=", vm.recoverableEditorRoute())
            assertTrue(drafts.read(id)!!.contains("Recovered edit 草稿"))
            drafts.write(999999, "{broken")
            assertEquals("editor/$id?date=", vm.recoverableEditorRoute())
            // An invalid draft must not be deleted during startup selection.
            assertEquals("{broken", drafts.read(999999))
        } finally { drafts.delete(id); drafts.delete(999999); vm.repository.replaceAll(original) }
    }

    @Test fun addAndEntryReminderWhileEditingNeverOverwriteExistingEntry(): Unit = runBlocking {
        tag("editor_list")
        lateinit var vm: JournalViewModel
        ui.runOnUiThread { vm = ViewModelProvider(ui.activity)[JournalViewModel::class.java] }
        val original = vm.repository.snapshot()
        val mood = original.moods.first()
        val oldNote = "Original retained navigation test"
        val id = vm.repository.saveEntry(Entry(timestamp = System.currentTimeMillis(), moodId = mood.id, note = oldNote,
            moodName = mood.name, moodScore = mood.score, moodColor = mood.color, moodIcon = mood.icon), emptySet(), emptyList())
        try {
            for ((index, notification) in listOf(false, true).withIndex()) {
                ui.onNodeWithTag("nav_entries").performClick()
                try { ui.waitUntil(30_000) { ui.onAllNodesWithText("Search notes or activities").fetchSemanticsNodes().isNotEmpty() } }
                catch (failure: Throwable) { throw AssertionError("Entry list missing on iteration $index; ${ui.onRoot().printToString()}", failure) }
                ui.onNodeWithText("Search notes or activities").performTextReplacement(oldNote)
                ui.waitUntil(30_000) { ui.onAllNodesWithText(oldNote).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Edit entry").performScrollTo().performClick()
                ui.waitUntil(30_000) { ui.onAllNodesWithText("Edit your moment").fetchSemanticsNodes().isNotEmpty() }
                if (notification) destination("entry") else ui.onNodeWithTag("nav_editor").performClick()
                ui.waitUntil(30_000) { ui.onAllNodesWithText("A moment to remember").fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithTag("mood_${mood.id}").performClick()
                ui.onNodeWithTag("editor_list").performScrollToNode(hasText("Your note"))
                assertEquals("", ui.onNodeWithText("Your note").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
                ui.onNodeWithText("Your note").performTextReplacement("New independent entry $index")
                ui.onNodeWithTag("editor_list").performScrollToNode(hasText("Save entry"))
                ui.onNodeWithText("Save entry").performClick()
                ui.waitUntil(30_000) { vm.data.value.entries.any { it.note == "New independent entry $index" } }
                assertEquals(oldNote, vm.repository.snapshot().entries.single { it.id == id }.note)
                assertEquals(original.entries.size + index + 2, vm.repository.snapshot().entries.size)
            }
        } finally {
            DraftStore(ui.activity).delete(id)
            DraftStore(ui.activity).delete(0)
            vm.repository.replaceAll(original)
        }
    }

    @Test fun pendingRestoreBlocksNewEntryUntilRecoveryCompletes() = runBlocking {
        tag("editor_list")
        lateinit var vm: JournalViewModel
        ui.runOnUiThread { vm = ViewModelProvider(ui.activity)[JournalViewModel::class.java] }
        val marker = java.io.File(ui.activity.filesDir, "restore-in-progress")
        val rollback = java.io.File(ui.activity.filesDir, "restore-rollback.zip")
        check(!marker.exists() && !rollback.exists())
        try {
            marker.writeText("rollback-v1")
            ui.runOnUiThread { vm.initialize() }
            ui.waitUntil(30_000) { vm.recoveryRequired.value }
            ui.onNodeWithTag("editor_list").assertDoesNotExist()
        } finally {
            marker.delete()
            ui.waitUntil(30_000) { !vm.busy.value }
            ui.runOnUiThread { vm.initialize() }
            tag("editor_list")
        }
    }
}
