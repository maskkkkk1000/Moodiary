package app.moodiary.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object DataValidation {
    private val photoPath = Regex("[A-Za-z0-9_-]{1,180}\\.[A-Za-z0-9]{1,12}")
    fun isManagedPhotoPath(path: String): Boolean = photoPath.matches(path)

    fun validate(data: JournalData, preferences: AppPreferences = AppPreferences()): List<String> {
        val errors = mutableListOf<String>()
        fun check(valid: Boolean, message: String) { if (!valid && errors.size < 100) errors += message }
        fun ids(name: String, values: List<Long>): Set<Long> {
            check(values.all { it > 0 }, "$name IDs must be positive")
            check(values.size == values.toSet().size, "Duplicate $name IDs")
            return values.toSet()
        }
        fun date(value: String, label: String): LocalDate? = try {
            LocalDate.parse(value).also { check(it.toString() == value && it.year in 1..9999, "$label must be an ISO date in years 0001–9999") }
        } catch (_: Exception) { check(false, "$label is not a valid ISO date"); null }
        fun name(value: String, label: String) = check(value.isNotBlank() && value.length <= 200, "$label must contain 1–200 characters")
        fun text(value: String, label: String) = check(value.length <= 1_000_000, "$label exceeds one million characters")
        fun icon(value: String, label: String) = check(value.isNotBlank() && value.length <= 64, "$label must contain 1–64 characters")
        fun color(value: Long?, label: String) = check(value == null || value in 0..0xFFFFFFFFL, "$label must be an ARGB color")
        fun time(created: Long, updated: Long, label: String) = check(created in MIN_TIMESTAMP..MAX_TIMESTAMP && updated in MIN_TIMESTAMP..MAX_TIMESTAMP && updated >= created, "$label timestamps are invalid")
        val moods = ids("mood", data.moods.map { it.id }); val groups = ids("group", data.groups.map { it.id })
        val activities = ids("activity", data.activities.map { it.id }); val entries = ids("entry", data.entries.map { it.id })
        val goals = ids("goal", data.goals.map { it.id }); ids("photo", data.photos.map { it.id })
        ids("completion", data.completions.map { it.id }); ids("reminder", data.reminders.map { it.id })
        ids("template", data.templates.map { it.id }); ids("important day", data.importantDays.map { it.id })
        val binaryGoals = ids("binary goal", data.binaryGoals.map { it.id })
        ids("binary goal record", data.binaryGoalRecords.map { it.id })
        check(data.moods.isNotEmpty() && data.moods.any { !it.isArchived }, "At least one active mood is required")
        data.moods.forEach { name(it.name, "Mood name"); icon(it.icon, "Mood icon"); color(it.color, "Mood color"); check(it.score.isFinite() && it.score in -1_000.0..1_000.0, "Mood score must be finite and between -1000 and 1000"); check(it.sortOrder >= 0, "Mood order cannot be negative"); time(it.createdAt, it.updatedAt, "Mood") }
        data.groups.forEach { name(it.name, "Group name"); check(it.sortOrder >= 0, "Group order cannot be negative"); time(it.createdAt, it.updatedAt, "Group") }
        data.activities.forEach { name(it.name, "Activity name"); icon(it.icon, "Activity icon"); color(it.color, "Activity color"); check(it.groupId == null || it.groupId in groups, "Activity refers to a missing group"); check(it.sortOrder >= 0, "Activity order cannot be negative"); time(it.createdAt, it.updatedAt, "Activity") }
        data.entries.forEach { check(it.moodId in moods, "Entry refers to a missing mood"); name(it.moodName, "Historical mood name"); icon(it.moodIcon, "Historical mood icon"); color(it.moodColor, "Historical mood color"); check(it.moodScore.isFinite() && it.moodScore in -1_000.0..1_000.0, "Historical mood score is invalid"); text(it.note, "Entry note"); check(it.timestamp in MIN_TIMESTAMP..MAX_TIMESTAMP, "Entry timestamp is outside supported years"); time(it.createdAt, it.updatedAt, "Entry") }
        check(data.entryActivities.size == data.entryActivities.toSet().size, "Duplicate entry/activity relation")
        data.entryActivities.forEach { check(it.entryId in entries && it.activityId in activities, "Entry/activity relation has a missing reference") }
        check(data.photos.map { it.localPath }.distinct().size == data.photos.size, "Managed photo paths cannot be shared")
        data.photos.forEach { check(it.entryId in entries, "Photo refers to a missing entry"); check(isManagedPhotoPath(it.localPath), "Photo path is not an app-managed relative path"); check(it.sortOrder >= 0, "Photo order cannot be negative"); time(it.createdAt, it.createdAt, "Photo") }
        val schedules = data.schedules.groupBy { it.goalId }
        data.goals.forEach {
            name(it.name, "Goal name"); check(it.linkedActivityId == null || it.linkedActivityId in activities, "Goal refers to a missing activity")
            check(it.targetCount in 1..7 && (it.goalType == GoalType.WEEKLY_COUNT || it.targetCount == 1), "Goal target must be 1 for daily/weekday goals or 1–7 for weekly goals")
            val start = date(it.startDate, "Goal start"); val end = it.endDate?.let { value -> date(value, "Goal end") }
            check(start == null || end == null || end >= start, "Goal ends before it starts")
            check(it.goalType != GoalType.WEEKDAYS || !schedules[it.id].isNullOrEmpty(), "Weekday goal requires at least one selected day")
            time(it.createdAt, it.updatedAt, "Goal")
        }
        check(data.schedules.size == data.schedules.toSet().size, "Duplicate goal schedule")
        data.schedules.forEach { check(it.goalId in goals && it.dayOfWeek in 1..7, "Invalid goal schedule") }
        check(data.completions.map { it.goalId to it.date }.distinct().size == data.completions.size, "Duplicate goal/date completion")
        val goalById = data.goals.associateBy { it.id }
        data.completions.forEach {
            check(it.goalId in goals, "Completion refers to a missing goal"); val day = date(it.date, "Completion date")
            val goal = goalById[it.goalId]
            if (it.source == CompletionSource.LINKED_ACTIVITY && day != null && goal != null && runCatching { LocalDate.parse(goal.startDate); goal.endDate?.let(LocalDate::parse) }.isSuccess) check(GoalEngine.isScheduled(goal, data.schedules, day), "Automatic completion is outside the goal schedule")
            check(it.source != CompletionSource.LINKED_ACTIVITY || goal?.linkedActivityId != null, "Automatic completion requires a linked activity")
            time(it.completedAt, it.completedAt, "Completion")
        }
        data.reminders.forEach { check(it.hour in 0..23 && it.minute in 0..59, "Reminder time is invalid"); check(it.daysOfWeek.isNotEmpty() && it.daysOfWeek.all { day -> day in 1..7 }, "Reminder weekdays are invalid"); check((it.type == ReminderType.DIARY && it.targetId == null) || (it.type == ReminderType.GOAL && it.targetId in goals), "Reminder target is invalid"); text(it.message, "Reminder message"); time(it.createdAt, it.updatedAt, "Reminder") }
        data.templates.forEach { name(it.name, "Template name"); text(it.content, "Template content"); check(it.sortOrder >= 0, "Template order cannot be negative") }
        data.importantDays.forEach { date(it.date, "Important day"); name(it.title, "Important day title"); icon(it.icon, "Important day icon"); text(it.note, "Important day note") }
        data.binaryGoals.forEach {
            name(it.name, "Small goal name"); icon(it.icon, "Small goal icon"); text(it.description, "Small goal description")
            check(it.sortOrder >= 0, "Small goal order cannot be negative"); time(it.createdAt, it.updatedAt, "Small goal")
        }
        check(data.binaryGoalRecords.map { it.goalId to it.date }.distinct().size == data.binaryGoalRecords.size, "Duplicate small goal/date result")
        data.binaryGoalRecords.forEach {
            check(it.goalId in binaryGoals, "Small goal result refers to a missing goal")
            date(it.date, "Small goal result date"); check(it.value in 0..1, "Small goal result must be 0 or 1")
            time(it.createdAt, it.updatedAt, "Small goal result")
        }
        check(preferences.theme in setOf("SYSTEM", "LIGHT", "DARK"), "Unsupported theme")
        check(preferences.palette in setOf("FOREST", "OCEAN", "PLUM", "ROSE", "SUNSET"), "Unsupported palette")
        check(preferences.aggregation in setOf("AVERAGE", "LATEST", "HIGHEST", "LOWEST"), "Unsupported daily aggregation")
        check(preferences.relockMinutes in setOf(0, 1, 5, 30), "Unsupported relock delay")
        check(preferences.lastBackupAt == 0L || preferences.lastBackupAt in MIN_TIMESTAMP..MAX_TIMESTAMP, "Backup timestamp is invalid")
        check(preferences.backupTreeUri.isEmpty() || (preferences.backupTreeUri.startsWith("content://") && preferences.backupTreeUri.length <= 8192), "Backup destination must be an Android content URI")
        check(preferences.lastBackupError.length <= 8192, "Backup error message is too long")
        return errors
    }

    fun requireValid(data: JournalData, preferences: AppPreferences = AppPreferences()) {
        val errors = validate(data, preferences)
        require(errors.isEmpty()) { errors.joinToString("; ") }
    }
    private const val MIN_TIMESTAMP = -62135596800000L
    private const val MAX_TIMESTAMP = 253402300799999L
}

@Serializable data class BackupMetadata(val formatVersion: Int, val createdAt: String, val appVersion: String = "0.2.0", val platform: String = "android", val photoSha256: Map<String, String> = emptyMap())
data class BackupLimits(val totalBytes: Long = 256L * 1024 * 1024, val photoBytes: Long = 32L * 1024 * 1024, val databaseBytes: Long = 64L * 1024 * 1024, val fileCount: Int = 20_003)
data class ValidatedBackup(val data: JournalData, val preferences: AppPreferences, val photos: Map<String, ByteArray>, val metadata: BackupMetadata)

object BackupCodec {
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false; allowSpecialFloatingPointValues = false; prettyPrint = true }
    // Archive size limits apply to uncompressed content. Keep indentation out of the database
    // member so large journals fit without weakening those limits; standalone JSON stays readable.
    private val archiveDatabaseJson = Json(json) { prettyPrint = false }
    const val FORMAT_VERSION = 2
    // Version 1 has no binary goals; JournalData defaults those two missing arrays to empty.
    fun requireSupportedFormat(version: Int) {
        require(version in 1..FORMAT_VERSION) { "Unsupported backup format $version; supported versions are 1–$FORMAT_VERSION" }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun write(output: OutputStream, data: JournalData, preferences: AppPreferences, openPhoto: (String) -> InputStream, limits: BackupLimits = BackupLimits()) {
        DataValidation.requireValid(data, preferences)
        require(data.photos.size + 3 <= limits.fileCount) { "Too many files for one backup" }
        var total = 0L
        val hashes = linkedMapOf<String, String>()
        ZipOutputStream(output).use { zip ->
            fun put(path: String, bytes: ByteArray, maximum: Long) {
                require(bytes.size.toLong() <= maximum) { "$path is too large" }
                total += bytes.size; require(total <= limits.totalBytes) { "Backup exceeds configured size limit" }
                zip.putNextEntry(ZipEntry(path).apply { time = 0L }); zip.write(bytes); zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("database.json").apply { time = 0L })
            var databaseSize = 0L
            val bounded = object : OutputStream() {
                override fun write(value: Int) { write(byteArrayOf(value.toByte()), 0, 1) }
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    databaseSize += length; total += length
                    require(databaseSize <= limits.databaseBytes && total <= limits.totalBytes) { "Database or backup exceeds configured size limit" }
                    zip.write(bytes, offset, length)
                }
            }
            archiveDatabaseJson.encodeToStream(data, bounded)
            zip.closeEntry()
            put("settings.json", json.encodeToString(preferences).toByteArray(Charsets.UTF_8), 65536)
            data.photos.sortedBy { it.localPath }.forEach { photo ->
                zip.putNextEntry(ZipEntry("photos/${photo.localPath}").apply { time = 0L })
                val digest = MessageDigest.getInstance("SHA-256")
                var photoSize = 0L
                openPhoto(photo.localPath).use { input ->
                    val buffer = ByteArray(65536)
                    while (true) { val n = input.read(buffer); if (n < 0) break; photoSize += n; total += n
                        require(photoSize <= limits.photoBytes && total <= limits.totalBytes) { "Photo or backup exceeds configured size limit" }
                        digest.update(buffer, 0, n); zip.write(buffer, 0, n)
                    }
                }
                require(photoSize > 0) { "Photo is empty: ${photo.localPath}" }
                zip.closeEntry()
                hashes[photo.localPath] = digest.digest().joinToString("") { "%02x".format(it) }
            }
            put("metadata.json", json.encodeToString(BackupMetadata(formatVersion = FORMAT_VERSION, createdAt = Instant.now().toString(), photoSha256 = hashes)).toByteArray(Charsets.UTF_8), 4L * 1024 * 1024)
        }
    }

    /** Reads into a bounded, validated staging value. This function never touches the active database. */
    fun read(input: InputStream, limits: BackupLimits = BackupLimits()): ValidatedBackup {
        require(limits.totalBytes > 0 && limits.photoBytes > 0 && limits.databaseBytes > 0 && limits.fileCount >= 3)
        val files = linkedMapOf<String, ByteArray>(); var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val path = entry.name
                require(!entry.isDirectory && (path in setOf("metadata.json", "database.json", "settings.json") || (path.startsWith("photos/") && DataValidation.isManagedPhotoPath(path.removePrefix("photos/"))))) { "Unexpected or unsafe archive path: $path" }
                require(path !in files) { "Duplicate archive path: $path" }
                require(files.size < limits.fileCount) { "Too many files in backup" }
                val maximum = when (path) { "metadata.json" -> 4L * 1024 * 1024; "database.json" -> limits.databaseBytes; "settings.json" -> 65536L; else -> limits.photoBytes }
                val bytes = readBounded(zip, minOf(maximum, limits.totalBytes - total), path)
                total += bytes.size; files[path] = bytes; zip.closeEntry()
            }
        }
        fun required(path: String): String = requireNotNull(files.remove(path)) { "Missing $path" }.toString(Charsets.UTF_8)
        val metadata = json.decodeFromString<BackupMetadata>(required("metadata.json"))
        requireSupportedFormat(metadata.formatVersion)
        Instant.parse(metadata.createdAt)
        require(metadata.platform == "android" && metadata.appVersion.isNotBlank() && metadata.appVersion.length <= 100) { "Invalid backup metadata" }
        val data = json.decodeFromString<JournalData>(required("database.json"))
        val preferences = json.decodeFromString<AppPreferences>(required("settings.json"))
        DataValidation.requireValid(data, preferences)
        val photos = files.mapKeys { (path, _) -> path.removePrefix("photos/") }
        val referenced = data.photos.mapTo(HashSet()) { it.localPath }
        require(referenced == photos.keys) { "Backup contains missing or unreferenced photos" }
        require(metadata.photoSha256.keys == referenced) { "Photo checksum manifest does not match references" }
        photos.forEach { (path, bytes) -> require(bytes.isNotEmpty() && sha256(bytes) == metadata.photoSha256[path]) { "Photo checksum failed: $path" } }
        return ValidatedBackup(data, preferences, photos, metadata)
    }

    private fun readBounded(input: InputStream, maximum: Long, label: String): ByteArray {
        require(maximum >= 0) { "Backup exceeds configured size limit" }
        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count; require(total <= maximum) { "$label exceeds the allowed uncompressed size" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

object ExportCodec {
    fun json(data: JournalData): String = BackupCodec.json.encodeToString(data)

    /** RFC 4180 CSV. Activities is a JSON string array inside one CSV cell (unambiguous delimiters).
     * Every text cell starting a spreadsheet formula is prefixed with an apostrophe. */
    fun csv(data: JournalData, zone: ZoneId, filter: SearchFilter = SearchFilter()): String {
        val activities = data.activities.associateBy { it.id }
        val links = data.entryActivities.groupBy { it.entryId }
        val photos = data.photos.groupingBy { it.entryId }.eachCount()
        fun cell(value: String, protectFormula: Boolean = false): String {
            val safe = if (protectFormula && value.trimStart().firstOrNull() in setOf('=', '+', '-', '@', '\t', '\r', '\n')) "'$value" else value
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        return buildString {
            append("EntryId,Date,Time,Timestamp,Mood,MoodScore,Activities,Note,PhotoCount,CreatedAt,UpdatedAt\r\n")
            EntrySearch.filter(data, filter, zone).forEach { entry ->
                val local = Instant.ofEpochMilli(entry.timestamp).atZone(zone)
                val names = links[entry.id].orEmpty().mapNotNull { activities[it.activityId]?.name }.distinct()
                append(listOf(cell(entry.id.toString()), cell(local.toLocalDate().toString()), cell(local.toLocalTime().toString()),
                    cell(Instant.ofEpochMilli(entry.timestamp).toString()), cell(entry.moodName, true), cell(entry.moodScore.toString()),
                    cell(BackupCodec.json.encodeToString(names)), cell(entry.note, true), cell((photos[entry.id] ?: 0).toString()),
                    cell(Instant.ofEpochMilli(entry.createdAt).toString()), cell(Instant.ofEpochMilli(entry.updatedAt).toString())).joinToString(","))
                append("\r\n")
            }
        }
    }
}
