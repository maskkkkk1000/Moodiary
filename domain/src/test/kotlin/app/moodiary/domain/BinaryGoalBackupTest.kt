package app.moodiary.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BinaryGoalBackupTest {
    private val goal = BinaryGoal(9, "每天读书", "📚", "Read one page", 2, false, 0, 1)
    private val records = listOf(BinaryGoalRecord(11, 9, "2024-02-28", 1, 10, 20), BinaryGoalRecord(12, 9, "2024-02-29", 0, 30, 40))
    private val data = JournalData(moods = listOf(Mood(1, "Good", 4.0, createdAt = 0)), binaryGoals = listOf(goal), binaryGoalRecords = records)

    @Test fun formatTwoRoundTripsGoalsIconsOutcomesAndJsonExport() {
        val output = ByteArrayOutputStream()
        BackupCodec.write(output, data, AppPreferences(), { error("No photos") })
        val restored = BackupCodec.read(ByteArrayInputStream(output.toByteArray()))
        assertEquals(2, restored.metadata.formatVersion)
        assertEquals(data, restored.data)
        assertEquals(data, BackupCodec.json.decodeFromString<JournalData>(ExportCodec.json(data)))
        assertTrue(ExportCodec.json(data).contains("\"binaryGoalRecords\""))
        staged(output.toByteArray()) { assertEquals(data, it.data) }
    }

    @Test fun realFormatOneWithoutNewArraysLoadsAsEmptyInBothReaders() {
        val legacy = JsonObject(BackupCodec.json.parseToJsonElement(ExportCodec.json(data)).let { it as JsonObject }
            .filterKeys { it != "binaryGoals" && it != "binaryGoalRecords" }).toString()
        val bytes = archive(1, legacy)
        val expected = data.copy(binaryGoals = emptyList(), binaryGoalRecords = emptyList())
        assertEquals(expected, BackupCodec.read(ByteArrayInputStream(bytes)).data)
        staged(bytes) { assertEquals(expected, it.data); assertEquals(1, it.metadata.formatVersion) }
    }

    @Test fun invalidBinaryDataIsRejectedBeforeAnyRestore() {
        val invalid = listOf(
            data.copy(binaryGoals = listOf(goal, goal)),
            data.copy(binaryGoals = listOf(goal.copy(name = " "))),
            data.copy(binaryGoals = listOf(goal.copy(icon = ""))),
            data.copy(binaryGoals = listOf(goal.copy(sortOrder = -1))),
            data.copy(binaryGoals = listOf(goal.copy(updatedAt = -1))),
            data.copy(binaryGoalRecords = records + records.first().copy(id = 13)),
            data.copy(binaryGoalRecords = listOf(records.first().copy(goalId = 99))),
            data.copy(binaryGoalRecords = listOf(records.first().copy(value = -1))),
            data.copy(binaryGoalRecords = listOf(records.first().copy(value = 2))),
            data.copy(binaryGoalRecords = listOf(records.first().copy(date = "2023-02-29"))),
            data.copy(binaryGoalRecords = listOf(records.first().copy(date = "2024-2-28"))),
            data.copy(binaryGoalRecords = listOf(records.first().copy(updatedAt = 9))),
            data.copy(binaryGoalRecords = listOf(records.first().copy(id = 0)))
        )
        invalid.forEach { value ->
            assertTrue(DataValidation.validate(value).toString(), DataValidation.validate(value).isNotEmpty())
            rejects { BackupCodec.read(ByteArrayInputStream(archive(2, ExportCodec.json(value)))) }
            rejects { BackupCodec.write(ByteArrayOutputStream(), value, AppPreferences(), { error("No photos") }) }
        }
        rejects { staged(archive(2, ExportCodec.json(invalid.first()))) { fail("Invalid staging must fail") } }
    }

    @Test fun unsetRemainsAbsentAndArchivedHistoryIsValid() {
        val archived = data.copy(binaryGoals = listOf(goal.copy(isArchived = true)))
        DataValidation.requireValid(archived)
        val restored = BackupCodec.read(ByteArrayInputStream(archive(2, ExportCodec.json(archived))))
        assertEquals(2, restored.data.binaryGoalRecords.size)
        assertFalse(restored.data.binaryGoalRecords.any { it.date == "2024-03-01" })
        assertEquals(listOf(1, 0), restored.data.binaryGoalRecords.map { it.value })
    }

    @Test fun unsupportedVersionIsRejectedByBothReaders() {
        listOf(0, 3, 999).forEach { version ->
            val bytes = archive(version, ExportCodec.json(data))
            rejects { BackupCodec.read(ByteArrayInputStream(bytes)) }
            rejects { staged(bytes) { fail("Unsupported staging must fail") } }
        }
    }

    @Test fun timezoneOrClockChangesDoNotRejectValidImportedCalendarDates() {
        val future = data.copy(binaryGoalRecords = listOf(records.first().copy(date = "9999-12-31")))
        DataValidation.requireValid(future)
        assertEquals(future, BackupCodec.read(ByteArrayInputStream(archive(2, ExportCodec.json(future)))).data)
    }

    private fun archive(version: Int, json: String): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            mapOf("metadata.json" to BackupCodec.json.encodeToString(BackupMetadata(version, "2024-03-01T00:00:00Z", "0.1.0")),
                "database.json" to json, "settings.json" to "{}").forEach { (path, value) ->
                zip.putNextEntry(ZipEntry(path)); zip.write(value.toByteArray()); zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun staged(bytes: ByteArray, check: (StagedBackup) -> Unit) {
        val directory = Files.createTempDirectory("binary-backup-test-").toFile()
        try {
            val file = directory.resolve("archive.zip").apply { writeBytes(bytes) }
            stageBackup(file, directory).use(check)
        } finally { directory.deleteRecursively() }
    }

    private fun rejects(block: () -> Unit) { try { block(); fail("Expected validation failure") } catch (_: IllegalArgumentException) { } }
}
