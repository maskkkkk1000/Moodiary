package app.moodiary.feature

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalConfiguration
import java.time.format.FormatStyle
import java.time.format.TextStyle
import app.moodiary.R

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.moodiary.domain.*
import coil.compose.AsyncImage
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class JournalDisplayIndex(val activities: Map<Long, List<Activity>> = emptyMap(), val photos: Map<Long, List<EntryPhoto>> = emptyMap())
@Composable private fun displayIndex(data: JournalData): JournalDisplayIndex {
    val index by produceState(JournalDisplayIndex(), data.activities, data.entryActivities, data.photos) {
        value = withContext(Dispatchers.Default) {
            val byId = data.activities.associateBy { it.id }
            JournalDisplayIndex(data.entryActivities.groupBy { it.entryId }.mapValues { (_, links) -> links.mapNotNull { byId[it.activityId] } }, data.photos.groupBy { it.entryId }.mapValues { (_, photos) -> photos.sortedBy { it.sortOrder } })
        }
    }
    return index
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun EntryCard(entry: Entry, index: JournalDisplayIndex, vm: JournalViewModel, edit: (Long) -> Unit) {
    var deleting by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val photos = index.photos[entry.id].orEmpty()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${entry.moodIcon} ${entry.moodName}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm", locale)), style = MaterialTheme.typography.labelLarge)
            }
            val activities = index.activities[entry.id].orEmpty()
            if (activities.isNotEmpty()) Text(activities.joinToString("  ·  ") { "${it.icon} ${it.name}" }, style = MaterialTheme.typography.bodyMedium)
            if (entry.note.isNotBlank()) Text(entry.note, maxLines = 8)
            if (photos.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(photos, key = { it.id }) { photo ->
                AsyncImage(vm.photos.file(photo.localPath), stringResource(R.string.journal_entry_photo), Modifier.size(80.dp).clickable { edit(entry.id) })
            } }
            FlowRow { TextButton({ edit(entry.id) }) { Text(stringResource(R.string.ui_edit_entry)) }; TextButton({ deleting = true }) { Text(stringResource(R.string.ui_delete)) } }
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(stringResource(R.string.ui_delete_this_entry)) }, text = { Text(stringResource(R.string.ui_its_note_and_photo_links_will_be_removed_this_cannot_be_undo)) }, confirmButton = { TextButton({ deleting = false; vm.run { vm.deleteEntry(entry.id) } }) { Text(stringResource(R.string.ui_delete_entry)) } }, dismissButton = { TextButton({ deleting = false }) { Text(stringResource(R.string.ui_keep_entry)) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun EntriesScreen(data: JournalData, vm: JournalViewModel, edit: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var mood by rememberSaveable { mutableStateOf<Long?>(null) }
    var activity by rememberSaveable { mutableStateOf<Long?>(null) }
    var photo by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable { mutableStateOf(false) }
    val from = runCatching { LocalDate.parse(start) }.getOrNull()
    val through = runCatching { LocalDate.parse(end) }.getOrNull()
    val invalid = (start.isNotEmpty() && from == null) || (end.isNotEmpty() && through == null) || (from != null && through != null && from > through)
    val zone = ZoneId.systemDefault()
    val locale = LocalConfiguration.current.locales[0]
    val index = displayIndex(data)
    val entries by produceState<List<Entry>>(emptyList(), data, SearchFilter(query, from, through, mood, activity, photo), zone) {
        value = withContext(Dispatchers.Default) { EntrySearch.filter(data, SearchFilter(query, from, through, mood, activity, photo), zone) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.ui_your_moments), style = MaterialTheme.typography.headlineLarge); Text(stringResource(R.string.ui_a_private_space_for_the_shape_of_your_days), style = MaterialTheme.typography.bodyMedium) }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ui_search_notes_or_activities)) }, singleLine = true) }
        item { TextButton({ filters = !filters }) { Text(stringResource(if (filters) R.string.journal_hide_filters else R.string.journal_filters)) } }
        if (filters) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceRow(stringResource(R.string.journal_mood), listOf(null to stringResource(R.string.journal_all_moods)) + data.moods.map { it.id to it.name }, mood) { mood = it }
                ChoiceRow(stringResource(R.string.journal_activity), listOf(null to stringResource(R.string.journal_all_activities)) + data.activities.map { it.id to it.name }, activity) { activity = it }
                FlowRow { listOf(null to stringResource(R.string.journal_any_photos), true to stringResource(R.string.journal_with_photos), false to stringResource(R.string.journal_without_photos)).forEach { (value, label) -> FilterChip(photo == value, { photo = value }, { Text(label) }) } }
                OutlinedTextField(start, { start = it }, label = { Text(stringResource(R.string.ui_from_date_yyyy_mm_dd)) }, singleLine = true, isError = start.isNotEmpty() && from == null)
                OutlinedTextField(end, { end = it }, label = { Text(stringResource(R.string.ui_through_date_yyyy_mm_dd)) }, singleLine = true, isError = end.isNotEmpty() && through == null)
                if (invalid) Text(stringResource(R.string.ui_enter_a_valid_date_range), color = MaterialTheme.colorScheme.error)
                TextButton({ query = ""; mood = null; activity = null; photo = null; start = ""; end = "" }) { Text(stringResource(R.string.ui_clear_filters)) }
            }
        }
        item { Text(pluralStringResource(R.plurals.journal_entry_count, entries.size, entries.size), style = MaterialTheme.typography.labelLarge) }
        if (entries.isEmpty()) item { Text(stringResource(if (data.entries.isEmpty()) R.string.journal_empty_journal else R.string.journal_empty_filters)) }
        itemsIndexed(entries, key = { _, entry -> entry.id }) { position, entry ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (position == 0 || entries[position - 1].date(zone) != entry.date(zone)) Text(entry.date(zone).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)), style = MaterialTheme.typography.labelLarge)
                EntryCard(entry, index, vm, edit)
            }
        }
    }
}

@Composable fun <T> ChoiceRow(label: String, choices: List<Pair<T, String>>, selected: T, choose: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }) { Text(stringResource(R.string.journal_choice_label, label, choices.find { it.first == selected }?.second ?: stringResource(R.string.journal_choose))) }
        DropdownMenu(expanded, { expanded = false }) { choices.forEach { (value, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { choose(value); expanded = false }) } }
    }
}

@Composable fun CalendarScreen(data: JournalData, vm: JournalViewModel, aggregation: String, edit: (Long) -> Unit, add: (String) -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(selectedText)
    val zone = ZoneId.systemDefault()
    val locale = LocalConfiguration.current.locales[0]
    val index = displayIndex(data)
    val byDay by produceState<Map<LocalDate, List<Entry>>>(emptyMap(), data.entries, zone) { value = withContext(Dispatchers.Default) { data.entries.groupBy { it.date(zone) } } }
    val firstOffset = month.atDay(1).dayOfWeek.value - 1
    val monthPattern = stringResource(R.string.journal_month_pattern)
    LazyColumn(Modifier.fillMaxSize().testTag("calendar_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.ui_the_days_together), style = MaterialTheme.typography.headlineMedium) }
        item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(month.format(DateTimeFormatter.ofPattern(monthPattern, locale)), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ monthText = month.minusMonths(1).toString() }) { Text(stringResource(R.string.ui_previous)) }
                TextButton({ monthText = month.plusMonths(1).toString() }) { Text(stringResource(R.string.ui_next)) }
            }
        } }
        item { TextButton({ monthText = YearMonth.now().toString(); selectedText = LocalDate.now().toString() }) { Text(stringResource(R.string.ui_today)) } }
        item { BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Keep each date target at least 48 dp even on narrow displays. Weekday labels scroll with dates.
            val calendarWidth = maxOf(maxWidth, 364.dp)
            val canScrollCalendar = maxWidth < calendarWidth
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canScrollCalendar) Text(stringResource(R.string.ui_calendar_scroll_hint), style = MaterialTheme.typography.labelMedium)
            Column(Modifier.horizontalScroll(rememberScrollState()).width(calendarWidth)) {
                Row { DayOfWeek.values().forEach { Text(it.getDisplayName(TextStyle.NARROW, locale), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
                repeat((firstOffset + month.lengthOfMonth() + 6) / 7) { row ->
            Row(Modifier.fillMaxWidth()) { (0..6).forEach { column ->
                val day = row * 7 + column - firstOffset + 1
                if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f)) else {
                    val date = month.atDay(day)
                    val entries = byDay[date].orEmpty()
                    val score = Statistics.aggregate(entries, aggregation)
                    val important = data.importantDays.filter { it.date == date.toString() }
                    val moodColor = score?.let { mean -> entries.minByOrNull { kotlin.math.abs(it.moodScore - mean) }?.let { Color(it.moodColor) } }
                    val dateDescription = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
                    val entriesDescription = pluralStringResource(R.plurals.journal_entry_count, entries.size, entries.size)
                    val scoreDescription = score?.let { stringResource(R.string.journal_calendar_mood, it) }.orEmpty()
                    val importantDescription = if (important.isNotEmpty()) stringResource(R.string.journal_calendar_important) else ""
                    val description = stringResource(R.string.journal_calendar_description, dateDescription, entriesDescription, scoreDescription, importantDescription)
                    val color = if (selected == date) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                    Column(Modifier.weight(1f).heightIn(min = 64.dp).padding(2.dp).background(color, MaterialTheme.shapes.small).clickable { selectedText = date.toString() }.semantics { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
                        if (moodColor != null) Box(Modifier.padding(top = 4.dp).size(8.dp).background(moodColor, androidx.compose.foundation.shape.CircleShape))
                        Text(day.toString(), style = MaterialTheme.typography.bodyMedium)
                        Text(score?.let { "%.1f".format(locale, it) } ?: "·", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(if (important.isNotEmpty()) "☆" else if (entries.size > 1) "${entries.size}×" else "", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            } }
                }
            }
            }
        } }
        item { Text(selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)), style = MaterialTheme.typography.titleLarge); TextButton({ add(selected.toString()) }) { Text(stringResource(R.string.ui_add_entry_on_this_day)) } }
        data.importantDays.filter { it.date == selected.toString() }.forEach { day -> item { Text("${day.icon} ${day.title}"); if (day.note.isNotBlank()) Text(day.note) } }
        val selectedEntries = byDay[selected].orEmpty().sortedByDescending { it.timestamp }
        if (selectedEntries.isEmpty()) item { Text(stringResource(R.string.ui_no_moments_recorded_on_this_day)) }
        items(selectedEntries, key = { it.id }) { EntryCard(it, index, vm, edit) }
    }
}

@Composable fun TrendChart(points: List<Pair<LocalDate, Double>>) {
    val color = MaterialTheme.colorScheme.primary
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val description = if (points.isEmpty()) stringResource(R.string.journal_no_recorded_days) else stringResource(R.string.journal_trend_description, pluralStringResource(R.plurals.journal_recorded_days, points.size, points.size), points.first().first.format(dateFormat), points.last().first.format(dateFormat), points.minOf { it.second }, points.maxOf { it.second }, points.last().second)
    val chartDescription = stringResource(R.string.journal_daily_averages, description)
    Column {
    if (points.isNotEmpty()) Text(stringResource(R.string.journal_mood_range, points.minOf { it.second }, points.maxOf { it.second }), style = MaterialTheme.typography.labelMedium)
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = chartDescription }) {
        if (points.isEmpty()) return@Canvas
        val first = points.first().first.toEpochDay()
        val span = (points.last().first.toEpochDay() - first).coerceAtLeast(1)
        val min = points.minOf { it.second } - 0.25
        val max = points.maxOf { it.second } + 0.25
        fun point(item: Pair<LocalDate, Double>) = Offset(8f + (item.first.toEpochDay() - first).toFloat() / span * (size.width - 16), size.height - 8f - ((item.second - min) / (max - min)).toFloat() * (size.height - 16))
        points.forEachIndexed { index, item ->
            val p = point(item)
            drawCircle(color, 4f, p)
            if (index > 0 && item.first.toEpochDay() - points[index - 1].first.toEpochDay() <= 1) drawLine(color, point(points[index - 1]), p, 3f)
        }
    }
    if (points.isNotEmpty()) Text("${points.first().first.format(dateFormat)} — ${points.last().first.format(dateFormat)}", style = MaterialTheme.typography.labelMedium)
    }
}
