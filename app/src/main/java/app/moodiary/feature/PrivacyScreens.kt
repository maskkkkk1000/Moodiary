package app.moodiary.feature

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import app.moodiary.R

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.moodiary.core.security.PinStore
import app.moodiary.domain.AppPreferences
import kotlinx.coroutines.launch

fun biometricAvailable(activity: FragmentActivity) = BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
fun showBiometric(activity: FragmentActivity, success: () -> Unit, error: (String) -> Unit) {
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { success() }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { error(errString.toString()) }
    })
    prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(activity.getString(R.string.privacy_biometric_title)).setSubtitle(activity.getString(R.string.privacy_biometric_subtitle))
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).setNegativeButtonText(activity.getString(R.string.privacy_use_pin)).build())
}

@Composable fun UnlockScreen(pinStore: PinStore, preferences: AppPreferences, activity: FragmentActivity, unlocked: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.ui_your_private_space), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.ui_enter_your_pin_to_open_moodiary))
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(pin, { pin = it.take(12) }, enabled = !busy, label = { Text(stringResource(R.string.ui_pin)) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true)
        Button(enabled = !busy, onClick = { scope.launch { busy = true; try { if (pinStore.verify(pin)) { pin = ""; unlocked() } else error = context.getString(R.string.privacy_incorrect_pin) } catch (e: Exception) { error = e.message } finally { busy = false; pin = "" } } }) { Text(stringResource(R.string.ui_unlock)) }
        if (preferences.biometrics && biometricAvailable(activity)) OutlinedButton({ showBiometric(activity, unlocked) { error = it } }, enabled = !busy) { Text(stringResource(R.string.ui_use_biometrics)) }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
    }
}

@Composable fun PrivacyScreen(pinStore: PinStore, preferences: AppPreferences, activity: FragmentActivity, vm: JournalViewModel) {
    val context = LocalContext.current
    val relockOptions = listOf(0 to stringResource(R.string.privacy_immediate)) + listOf(1, 5, 30).map { it to pluralStringResource(R.plurals.privacy_minutes, it, it) }
    var enabled by remember { mutableStateOf(pinStore.enabled) }
    var changing by remember { mutableStateOf(false) }
    var old by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.ui_privacy_lock), style = MaterialTheme.typography.headlineLarge); Text(stringResource(R.string.ui_your_pin_is_salted_and_hashed_on_this_device_the_app_lock_su)) }
        item { Button({ changing = true }) { Text(stringResource(if (enabled) R.string.privacy_change_pin else R.string.privacy_setup_pin)) } }
        if (enabled) {
            item { Row { Checkbox(preferences.biometrics, { value -> vm.run { vm.settings.update { it.copy(biometrics = value) } } }, enabled = biometricAvailable(activity)); Text(stringResource(R.string.ui_allow_biometric_unlock)) }; if (!biometricAvailable(activity)) Text(stringResource(R.string.ui_set_up_a_strong_biometric_in_android_device_settings_to_enab)) }
            item { ChoiceRow(stringResource(R.string.privacy_relock_after), relockOptions, preferences.relockMinutes) { value -> vm.run { vm.settings.update { it.copy(relockMinutes = value) } } } }
        }
        item { Text(stringResource(R.string.ui_pins_are_never_included_in_backups_a_fresh_process_always_re)) }
    }
    if (changing) AlertDialog({ changing = false; pin = ""; old = ""; confirm = "" }, title = { Text(stringResource(R.string.ui_pin_settings)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (enabled) OutlinedTextField(old, { old = it.take(12) }, label = { Text(stringResource(R.string.ui_current_pin)) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            OutlinedTextField(pin, { pin = it.take(12) }, label = { Text(stringResource(R.string.ui_new_pin_6_12_digits)) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            OutlinedTextField(confirm, { confirm = it.take(12) }, label = { Text(stringResource(R.string.ui_confirm_new_pin)) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            if (enabled) Text(stringResource(R.string.ui_to_remove_the_lock_enter_your_current_pin_and_leave_the_new_))
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton({ vm.run {
        try {
            if (enabled) check(pinStore.verify(old)) { context.getString(R.string.privacy_incorrect_current_pin) }
            if (pin.isEmpty() && enabled) { pinStore.remove(old); vm.settings.update { it.copy(biometrics = false) } }
            else { require(pin == confirm) { context.getString(R.string.privacy_pin_mismatch) }; pinStore.set(pin) }
            enabled = pinStore.enabled; changing = false; old = ""; pin = ""; confirm = ""
        } catch (e: Exception) { error = e.message }
    } }) { Text(stringResource(R.string.ui_save)) } }, dismissButton = { TextButton({ changing = false; old = ""; pin = ""; confirm = "" }) { Text(stringResource(R.string.ui_cancel)) } })
}

@Composable fun AppearanceScreen(preferences: AppPreferences, vm: JournalViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.ui_appearance), style = MaterialTheme.typography.headlineLarge)
        ChoiceRow(stringResource(R.string.appearance_theme), listOf("SYSTEM" to R.string.appearance_system, "LIGHT" to R.string.appearance_light, "DARK" to R.string.appearance_dark).map { it.first to stringResource(it.second) }, preferences.theme) { value -> vm.run { vm.settings.update { it.copy(theme = value) } } }
        ChoiceRow(stringResource(R.string.appearance_palette), listOf("FOREST" to R.string.appearance_forest, "OCEAN" to R.string.appearance_ocean, "PLUM" to R.string.appearance_plum, "ROSE" to R.string.appearance_rose, "SUNSET" to R.string.appearance_sunset).map { it.first to stringResource(it.second) }, preferences.palette) { value -> vm.run { vm.settings.update { it.copy(palette = value) } } }
        ChoiceRow(stringResource(R.string.appearance_calendar), listOf("AVERAGE" to R.string.appearance_average, "LATEST" to R.string.appearance_latest, "HIGHEST" to R.string.appearance_highest, "LOWEST" to R.string.appearance_lowest).map { it.first to stringResource(it.second) }, preferences.aggregation) { value -> vm.run { vm.settings.update { it.copy(aggregation = value) } } }
    }
}
