package app.moodiary.core.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.moodiary.R

private data class BuiltInIcon(val id: String, val image: ImageVector, val label: Int)
private val builtInIcons = listOf(
    BuiltInIcon("material:book", Icons.Default.MenuBook, R.string.iter_icon_study),
    BuiltInIcon("material:run", Icons.Default.DirectionsRun, R.string.iter_icon_exercise),
    BuiltInIcon("material:moon", Icons.Default.Bedtime, R.string.iter_icon_rest),
    BuiltInIcon("material:mindfulness", Icons.Default.SelfImprovement, R.string.iter_icon_calm),
    BuiltInIcon("material:work", Icons.Default.Work, R.string.iter_icon_work),
    BuiltInIcon("material:fitness", Icons.Default.FitnessCenter, R.string.iter_icon_fitness),
    BuiltInIcon("material:coffee", Icons.Default.LocalCafe, R.string.iter_icon_coffee),
    BuiltInIcon("material:food", Icons.Default.Restaurant, R.string.iter_icon_food),
    BuiltInIcon("material:heart", Icons.Default.Favorite, R.string.iter_icon_heart),
    BuiltInIcon("material:music", Icons.Default.MusicNote, R.string.iter_icon_music),
    BuiltInIcon("material:code", Icons.Default.Code, R.string.iter_icon_code),
    BuiltInIcon("material:nature", Icons.Default.Park, R.string.iter_icon_nature)
)
private val emojiIcons = listOf(
    "😀" to R.string.iter_icon_happy, "😌" to R.string.iter_icon_calm,
    "🌞" to R.string.iter_icon_sun, "🥳" to R.string.iter_icon_celebrate,
    "😔" to R.string.iter_icon_sad, "😴" to R.string.iter_icon_tired,
    "📚" to R.string.iter_icon_study, "🏃" to R.string.iter_icon_exercise,
    "🌙" to R.string.iter_icon_rest, "🧠" to R.string.iter_icon_ideas,
    "💻" to R.string.iter_icon_work, "🌱" to R.string.iter_icon_nature,
    "🎵" to R.string.iter_icon_music, "☕" to R.string.iter_icon_coffee,
    "🥗" to R.string.iter_icon_food, "💛" to R.string.iter_icon_heart
)

/** Stable IDs are persisted in the existing icon field. Legacy emoji/text remain readable. */
@Composable fun JournalIcon(
    icon: String,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color = LocalContentColor.current
) {
    val builtIn = builtInIcons.firstOrNull { it.id == icon }
    if (builtIn != null) Icon(builtIn.image, contentDescription, modifier.size(size), tint = tint)
    else Text(icon, modifier.clearAndSetSemantics { if (contentDescription != null) this.contentDescription = contentDescription },
        fontSize = size.value.sp, textAlign = TextAlign.Center, color = tint)
}

/** Inline preview with a bounded, scrollable picker; changes apply only after confirmation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun IconPicker(
    icon: String,
    onIconChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    allowMaterial: Boolean = false
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf(icon) }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            JournalIcon(icon, stringResource(R.string.iter_icon_preview), size = 36.dp)
            Text(stringResource(R.string.iter_icon_label), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton({ pending = icon; open = true }, Modifier.testTag("icon_picker_open")) { Text(stringResource(R.string.iter_change_icon)) }
        }
    }
    if (open) {
        val valid = pending.isNotBlank() && pending.length <= 64 &&
            (!pending.startsWith("material:") || (allowMaterial && builtInIcons.any { it.id == pending }))
        AlertDialog(onDismissRequest = { open = false }, title = { Text(stringResource(R.string.iter_choose_icon)) }, text = {
            LazyColumn(Modifier.heightIn(max = 440.dp).testTag("icon_picker_list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    JournalIcon(pending, stringResource(R.string.iter_icon_preview), Modifier.testTag("icon_picker_preview"), size = 48.dp)
                } }
                item { Text(stringResource(R.string.iter_emoji), style = MaterialTheme.typography.titleSmall) }
                item { FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    emojiIcons.forEach { (value, label) ->
                        val description = stringResource(label)
                        FilledTonalIconToggleButton(pending == value, { pending = value }, Modifier.size(48.dp).testTag("icon_choice_$value")) {
                            JournalIcon(value, description)
                        }
                    }
                } }
                item { OutlinedTextField(pending, { pending = it.take(64) }, Modifier.fillMaxWidth().testTag("icon_custom_input"),
                    label = { Text(stringResource(R.string.iter_custom_emoji)) }, singleLine = true,
                    isError = !valid, supportingText = { Text(stringResource(R.string.iter_custom_emoji_hint)) }) }
                if (allowMaterial) {
                    item { Text(stringResource(R.string.iter_built_in_icons), style = MaterialTheme.typography.titleSmall) }
                    item { FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        builtInIcons.forEach { option ->
                            FilledTonalIconToggleButton(pending == option.id, { pending = option.id }, Modifier.size(48.dp).testTag("icon_choice_${option.id}")) {
                                Icon(option.image, stringResource(option.label))
                            }
                        }
                    } }
                }
            }
        }, confirmButton = { TextButton({ onIconChange(pending.trim()); open = false }, enabled = valid, modifier = Modifier.testTag("icon_picker_apply")) {
            Text(stringResource(R.string.ui_save))
        } }, dismissButton = { TextButton({ open = false }, Modifier.testTag("icon_picker_cancel")) { Text(stringResource(R.string.ui_cancel)) } })
    }
}
