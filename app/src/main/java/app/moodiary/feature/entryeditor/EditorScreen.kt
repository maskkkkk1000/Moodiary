package app.moodiary.feature.entryeditor

import androidx.compose.ui.res.stringResource
import app.moodiary.R

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.moodiary.domain.JournalData
import app.moodiary.core.designsystem.JournalIcon
import coil.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable fun EditorScreen(id: Long, date: String?, data: JournalData, back: () -> Unit, vm: EditorViewModel = hiltViewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val complete by vm.savedEntry.collectAsStateWithLifecycle()
    var discard by remember { mutableStateOf(false) }
    var fullPhoto by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(id) { vm.load(id, date) }
    LaunchedEffect(complete) { if (complete) back() }
    androidx.activity.compose.BackHandler { if (!busy && !complete) discard = true }
    val otherLabel = stringResource(R.string.journal_other)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(12)) { vm.import(it) }
    LazyColumn(Modifier.fillMaxSize().testTag("editor_list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (id == 0L) R.string.journal_new_moment else R.string.journal_edit_moment), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.iter_editor_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { Text(stringResource(R.string.ui_how_are_you_feeling), style = MaterialTheme.typography.titleMedium) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                data.moods.filter { !it.isArchived || it.id == draft.moodId }.sortedBy { it.sortOrder }.forEach { mood ->
                    val selected = draft.moodId == mood.id
                    val scale by animateFloatAsState(if (selected) 1.08f else 1f, tween(160), label = "moodSelection")
                    Surface(shape = MaterialTheme.shapes.medium, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.widthIn(min = 88.dp, max = 140.dp).clip(MaterialTheme.shapes.medium).selectable(selected, enabled = !busy && !complete, role = Role.RadioButton) { vm.change { it.copy(moodId = mood.id) } }.testTag("mood_${mood.id}")) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            JournalIcon(mood.icon, modifier = Modifier.scale(scale), size = 40.dp)
                            Text(mood.name, style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
            if (data.moods.none { !it.isArchived || it.id == draft.moodId }) Text(stringResource(R.string.iter_no_moods), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Text(stringResource(R.string.ui_what_was_part_of_this_moment), style = MaterialTheme.typography.titleMedium) }
        val groups = data.groups.sortedBy { it.sortOrder }
        (groups.map { it.id to it.name } + (null to otherLabel)).forEach { (groupId, name) ->
            val activities = data.activities.filter { it.groupId == groupId && ((!it.isArchived && groups.none { group -> group.id == groupId && group.isArchived }) || it.id in draft.activities) }.sortedBy { it.sortOrder }
            if (activities.isNotEmpty()) item {
                Text(name, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    activities.forEach { activity -> FilterChip(activity.id in draft.activities, {
                        vm.change { it.copy(activities = if (activity.id in it.activities) it.activities - activity.id else it.activities + activity.id) }
                    }, { Text(activity.name) }, modifier = Modifier.heightIn(min = 48.dp).testTag("activity_${activity.id}"),
                        leadingIcon = { JournalIcon(activity.icon, size = 22.dp) }, border = null, enabled = !busy && !complete) }
                }
            }
        }
        if (data.activities.none { !it.isArchived || it.id in draft.activities }) item { Text(stringResource(R.string.iter_no_activities), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { TextField(draft.note, { value -> vm.change { it.copy(note = value) } }, Modifier.fillMaxWidth(), enabled = !busy && !complete, label = { Text(stringResource(R.string.ui_your_note)) }, minLines = 4,
            shape = MaterialTheme.shapes.medium, colors = TextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow, focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow, unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor = Color.Transparent)) }
        if (data.templates.any { !it.isArchived }) item {
            Text(stringResource(R.string.ui_add_a_prompt), style = MaterialTheme.typography.labelLarge)
            FlowRow { data.templates.filter { !it.isArchived }.sortedBy { it.sortOrder }.forEach { template ->
                TextButton({ vm.change { it.copy(note = it.note + (if (it.note.isBlank()) "" else "\n\n") + template.content) } }, enabled = !busy && !complete) { Text(template.name) }
            } }
        }
        item { OutlinedTextField(draft.localDateTime, { value -> vm.change { it.copy(localDateTime = value) } }, Modifier.fillMaxWidth(), enabled = !busy && !complete, label = { Text(stringResource(R.string.ui_date_and_time)) }, supportingText = { Text(stringResource(R.string.ui_yyyy_mm_ddthh_mm_device_timezone)) }, singleLine = true) }
        item { OutlinedButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy && draft.photoPaths.size < 12) { Text(stringResource(R.string.ui_add_photos)) } }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(draft.photoPaths, key = { it }) { path ->
                Column { AsyncImage(vm.photos.file(path), stringResource(R.string.journal_attached_photo), Modifier.size(112.dp).clip(MaterialTheme.shapes.medium).clickable { fullPhoto = vm.photos.file(path) }, contentScale = ContentScale.Crop)
                    TextButton({ vm.change { it.copy(photoPaths = it.photoPaths - path) } }, enabled = !busy && !complete) { Text(stringResource(R.string.ui_remove_photo)) } }
            } }
        }
        if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(vm::save, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), enabled = !busy && !complete && draft.loaded && draft.moodId != null) {
                if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(stringResource(if (busy) R.string.journal_saving else R.string.journal_save_entry))
            }
            TextButton({ discard = true }, enabled = !busy) { Text(stringResource(R.string.ui_cancel)) }
        } }
    }
    if (discard) AlertDialog(onDismissRequest = { if (!busy) discard = false }, title = { Text(stringResource(R.string.ui_leave_this_draft)) }, text = { Text(stringResource(R.string.ui_unsaved_changes_will_be_discarded)) }, confirmButton = { TextButton({ vm.discard(back) }, enabled = !busy && !complete) { Text(stringResource(R.string.ui_discard_draft)) } }, dismissButton = { TextButton({ discard = false }, enabled = !busy) { Text(stringResource(R.string.ui_keep_writing)) } })
    fullPhoto?.let { photo -> androidx.compose.ui.window.Dialog({ fullPhoto = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) { Column { TextButton({ fullPhoto = null }) { Text(stringResource(R.string.ui_close_photo)) }; AsyncImage(photo, stringResource(R.string.journal_full_photo), Modifier.fillMaxSize()) } }
    } }
}
