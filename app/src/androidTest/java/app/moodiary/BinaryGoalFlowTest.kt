package app.moodiary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.moodiary.feature.JournalViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BinaryGoalFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun waitTag(tag: String) = ui.waitUntil(30_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    @Test fun existingFutureOutcomeCanBeCorrectedAfterClockMovesBack(): Unit = runBlocking {
        waitTag("nav_more")
        lateinit var vm: JournalViewModel
        ui.runOnUiThread { vm = ViewModelProvider(ui.activity)[JournalViewModel::class.java] }
        val original = vm.repository.snapshot()
        val future = java.time.LocalDate.now().plusDays(1).toString()
        try {
            val id = vm.repository.saveBinaryGoal(app.moodiary.domain.BinaryGoal(name = "Clock adjustment test"))
            val data = vm.repository.snapshot()
            val now = System.currentTimeMillis()
            vm.repository.replaceAll(data.copy(binaryGoalRecords = data.binaryGoalRecords +
                app.moodiary.domain.BinaryGoalRecord(id = (data.binaryGoalRecords.maxOfOrNull { it.id } ?: 0) + 1,
                    goalId = id, date = future, value = 1, createdAt = now, updatedAt = now)))
            ui.onNodeWithTag("nav_more").performClick()
            ui.onNodeWithTag("more_list").performScrollToNode(hasText(ui.activity.getString(R.string.iter_binary_goals)))
            ui.onNodeWithText(ui.activity.getString(R.string.iter_binary_goals)).performClick()
            waitTag("binary_date")
            ui.onNodeWithTag("binary_date").performTextReplacement(future)
            val failed = "binary_${id}_0"
            ui.onNodeWithTag("binary_list").performScrollToNode(hasTestTag(failed))
            ui.onNodeWithTag(failed).assertIsEnabled().performClick()
            ui.waitUntil(30_000) { ui.onAllNodes(hasTestTag(failed) and isSelected()).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(0, vm.repository.snapshot().binaryGoalRecords.single { it.goalId == id }.value)
            ui.onNodeWithTag(failed).performClick()
            ui.waitUntil(30_000) { ui.onAllNodes(hasTestTag(failed) and isNotEnabled()).fetchSemanticsNodes().isNotEmpty() }
            assertFalse(vm.repository.snapshot().binaryGoalRecords.any { it.goalId == id })
        } finally { vm.repository.replaceAll(original) }
    }
    @Test fun createToggleUnsetEditArchiveRestoreAndRestart() = runBlocking {
        waitTag("nav_more")
        lateinit var vm: JournalViewModel
        ui.runOnUiThread { vm = ViewModelProvider(ui.activity)[JournalViewModel::class.java] }
        val original = vm.repository.snapshot()
        val name = "UI small goal ${System.nanoTime()}"
        try {
            ui.onNodeWithTag("nav_more").performClick()
            ui.onNodeWithTag("more_list").performScrollToNode(hasText(ui.activity.getString(R.string.iter_binary_goals)))
            ui.onNodeWithText(ui.activity.getString(R.string.iter_binary_goals)).performClick()
            waitTag("binary_create")
            ui.onNodeWithTag("binary_create").performClick()
            ui.onNodeWithTag("binary_name").performTextInput(name)
            ui.onNodeWithTag("binary_editor").performScrollToNode(hasTestTag("icon_picker_open"))
            ui.onNodeWithTag("icon_picker_open").performClick()
            ui.onNodeWithTag("icon_custom_input").performScrollTo().performTextReplacement("🥤")
            ui.onNodeWithTag("icon_picker_apply").performClick()
            ui.onNodeWithTag("binary_editor").performScrollToNode(hasTestTag("binary_save"))
            ui.onNodeWithTag("binary_save").performClick()
            ui.waitUntil(30_000) { ui.onAllNodesWithTag("binary_editor").fetchSemanticsNodes().isEmpty() }
            val goal = vm.repository.snapshot().binaryGoals.single { it.name == name }
            assertEquals("🥤", goal.icon)
            val success = "binary_${goal.id}_1"
            val failed = "binary_${goal.id}_0"
            fun show(tag: String) { ui.onNodeWithTag("binary_list").performScrollToNode(hasTestTag(tag)); waitTag(tag) }
            show(success); ui.onNodeWithTag(success).performClick()
            ui.waitUntil(30_000) { ui.onAllNodes(hasTestTag(success) and isSelected()).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, vm.repository.snapshot().binaryGoalRecords.single { it.goalId == goal.id }.value)
            ui.onNodeWithTag(failed).performClick()
            ui.waitUntil(30_000) { ui.onAllNodes(hasTestTag(failed) and isSelected()).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, vm.repository.snapshot().binaryGoalRecords.count { it.goalId == goal.id })
            assertEquals(0, vm.repository.snapshot().binaryGoalRecords.single { it.goalId == goal.id }.value)
            ui.onNodeWithTag(failed).performClick()
            ui.waitUntil(30_000) { ui.onAllNodes(hasTestTag(failed) and isSelected()).fetchSemanticsNodes().isEmpty() }
            assertFalse(vm.repository.snapshot().binaryGoalRecords.any { it.goalId == goal.id })
            ui.onNodeWithTag(success).performClick()
            ui.waitUntil(30_000) { ui.onAllNodes(hasTestTag(success) and isSelected()).fetchSemanticsNodes().isNotEmpty() }
            ui.activityRule.scenario.recreate()
            waitTag("binary_list"); show(success)
            ui.onNodeWithTag(success).assertIsSelected()
            show("binary_edit_${goal.id}");ui.onNodeWithTag("binary_edit_${goal.id}").performClick()
            ui.onNodeWithTag("binary_name").performTextReplacement("$name edited")
            ui.onNodeWithTag("binary_editor").performScrollToNode(hasTestTag("binary_save"))
            ui.onNodeWithTag("binary_save").performClick()
            ui.waitUntil(30_000) { ui.onAllNodesWithTag("binary_editor").fetchSemanticsNodes().isEmpty() }
            show("binary_archive_${goal.id}");ui.onNodeWithTag("binary_archive_${goal.id}").performClick()
            ui.waitUntil(30_000) { ui.onAllNodesWithTag("binary_goal_${goal.id}").fetchSemanticsNodes().isEmpty() }
            ui.onNodeWithTag("binary_list").performScrollToIndex(0)
            ui.onNodeWithTag("binary_list").performScrollToNode(hasText(ui.activity.getString(R.string.binary_archived)))
            ui.onNodeWithText(ui.activity.getString(R.string.binary_archived)).performClick()
            show("binary_archive_${goal.id}");ui.onNodeWithTag("binary_archive_${goal.id}").performClick()
            ui.waitUntil(30_000) { ui.onAllNodesWithTag("binary_goal_${goal.id}").fetchSemanticsNodes().isEmpty() }
            val restored=vm.repository.snapshot()
            assertEquals("$name edited",restored.binaryGoals.single { it.id==goal.id }.name)
            assertFalse(restored.binaryGoals.single { it.id==goal.id }.isArchived)
            assertEquals(1,restored.binaryGoalRecords.single { it.goalId==goal.id }.value)
        } finally { vm.repository.replaceAll(original) }
    }
}
