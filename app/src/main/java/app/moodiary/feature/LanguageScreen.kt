package app.moodiary.feature

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import app.moodiary.R

/** Android/AppCompat owns persistence so the in-app and system language pickers share one value. */
@Composable fun LanguageScreen() {
    // Observe configuration so a system language change refreshes this screen as well.
    val configuration = LocalConfiguration.current
    val requested = AppCompatDelegate.getApplicationLocales()[0]?.language
    val selected = when (requested) { "zh" -> "zh-CN"; "en" -> "en"; else -> "" }
    val options = listOf("" to stringResource(R.string.language_system), "zh-CN" to "简体中文", "en" to "English")
    LazyColumn(Modifier.testTag("language_list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.language_title), style = MaterialTheme.typography.headlineLarge) }
        item { Text(stringResource(R.string.language_description)) }
        item {
            Column(Modifier.selectableGroup()) {
                options.forEach { (tag, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("language_${tag.ifEmpty { "system" }}")
                        .selectable(selected = selected == tag, role = Role.RadioButton, onClick = {
                            if (selected != tag) AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                        }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == tag, onClick = null)
                        Text(label, Modifier.weight(1f).padding(start = 12.dp))
                    }
                }
            }
        }
        item { Text(stringResource(R.string.language_current, configuration.locales[0].getDisplayName(configuration.locales[0])), style = MaterialTheme.typography.bodySmall) }
        item { Text(stringResource(R.string.language_content_kept), style = MaterialTheme.typography.bodySmall) }
    }
}
