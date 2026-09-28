package app.moodiary

import androidx.compose.ui.res.stringResource
import app.moodiary.R

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import app.moodiary.core.designsystem.MoodiaryTheme
import app.moodiary.core.security.PinStore
import app.moodiary.domain.DataValidation
import app.moodiary.feature.*
import app.moodiary.feature.entryeditor.EditorScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint class MainActivity : AppCompatActivity() {
    @Inject lateinit var pinStore: PinStore
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val vm: JournalViewModel = hiltViewModel()
            val preferences by vm.preferences.collectAsStateWithLifecycle()
            var locked by remember { mutableStateOf(pinStore.enabled) }
            var backgroundAt by remember { mutableLongStateOf(0) }
            val currentPreferences by rememberUpdatedState(preferences)
            DisposableEffect(Unit) {
                val observer = object : DefaultLifecycleObserver {
                    override fun onStop(owner: LifecycleOwner) { backgroundAt = SystemClock.elapsedRealtime() }
                    override fun onStart(owner: LifecycleOwner) {
                        if (backgroundAt != 0L && pinStore.enabled && SystemClock.elapsedRealtime() - backgroundAt >= currentPreferences.relockMinutes * 60_000L) locked = true
                    }
                }
                ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
                onDispose { ProcessLifecycleOwner.get().lifecycle.removeObserver(observer) }
            }
            MoodiaryTheme(preferences) {
                Surface(Modifier.fillMaxSize()) {
                    if (locked) UnlockScreen(pinStore, preferences, this) { locked = false }
                    else MoodiaryApp(vm, pinStore, this)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MoodiaryApp(vm: JournalViewModel, pinStore: PinStore, activity: FragmentActivity) {
    val context = LocalContext.current
    val data by vm.data.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val initialized by vm.ready.collectAsStateWithLifecycle()
    val recoveryRequired by vm.recoveryRequired.collectAsStateWithLifecycle()
    val ready = initialized && !recoveryRequired
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val tools: DataToolsViewModel = hiltViewModel()
    val backgroundStatus by tools.status.collectAsStateWithLifecycle()
    val dataBusy by tools.busy.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val route by nav.currentBackStackEntryAsState()
    val routeName = route?.destination?.route ?: "entries"
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); vm.clearError() } }
    LaunchedEffect(backgroundStatus) { if (backgroundStatus.isNotBlank()) snackbar.showSnackbar(backgroundStatus) }
    LaunchedEffect(ready, data.reminders, data.goals, preferences.automaticBackup) {
        if (ready) try { tools.reminders.reconcile(); tools.automatic.schedule(preferences.automaticBackup) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { tools.status.value = context.getString(R.string.app_scheduling_failed, failure.message.orEmpty()) }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { if (vm.ready.value) vm.run { vm.repository.recalculateLinkedCompletions(); tools.reminders.reconcile() } }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val topRoutes = listOf("entries", "calendar", "statistics", "more")
    val showNavigationLabels = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value / fontScale >= 300 }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        if (ready && routeName !in topRoutes) TopAppBar(title = { Text(stringResource(R.string.ui_moodiary)) }, navigationIcon = { TextButton({ if (routeName.startsWith("editor/")) activity.onBackPressedDispatcher.onBackPressed() else nav.popBackStack() }) { Text(stringResource(R.string.ui_back)) } })
    }, bottomBar = {
        if (ready && routeName in topRoutes) NavigationBar {
            val destinations = listOf("entries" to Icons.AutoMirrored.Filled.List, "calendar" to Icons.Default.DateRange, "editor" to Icons.Default.Add, "statistics" to Icons.Default.BarChart, "more" to Icons.Default.MoreHoriz)
            destinations.forEach { (name, icon) ->
                val label = stringResource(when (name) { "entries" -> R.string.nav_entries; "calendar" -> R.string.nav_calendar; "statistics" -> R.string.nav_statistics; "more" -> R.string.nav_more; else -> R.string.nav_add })
                val description = if (name == "editor") stringResource(R.string.nav_add_entry) else label
                NavigationBarItem(modifier = Modifier.testTag("nav_$name"), selected = routeName == name, onClick = {
                if (name == "editor") nav.navigate("editor/0?date=") else nav.navigate(name) { popUpTo(nav.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true }
            }, icon = { Icon(icon, description) }, label = if (showNavigationLabels) ({ Text(label) }) else null) }
        }
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (!ready) Column(Modifier.padding(24.dp)) { Text(stringResource(R.string.ui_opening_your_journal)); if (busy || dataBusy) CircularProgressIndicator() else Button(vm::initialize) { Text(stringResource(R.string.ui_retry_opening_journal)) } }
            else NavHost(nav, "entries") {
                composable("entries") { EntriesScreen(data, vm) { nav.navigate("editor/$it?date=") } }
                composable("calendar") { CalendarScreen(data, vm, preferences.aggregation, { nav.navigate("editor/$it?date=") }, { nav.navigate("editor/0?date=$it") }) }
                composable("statistics") { StatisticsScreen(data) }
                composable("more") { MoreScreen { nav.navigate(it) } }
                composable("editor/{id}?date={date}", arguments = listOf(navArgument("id") { type = NavType.LongType }, navArgument("date") { type = NavType.StringType; defaultValue = "" })) { entry -> EditorScreen(entry.arguments?.getLong("id") ?: 0, entry.arguments?.getString("date")?.takeIf { it.isNotEmpty() }, data, { nav.popBackStack() }) }
                listOf("moods", "activities", "groups", "templates", "important").forEach { kind -> composable(kind) { ManagementScreen(kind, data, vm) } }
                composable("goals") { GoalsScreen(data, vm) }
                composable("achievements") { AchievementsScreen(data) }
                composable("reminders") { ReminderScreen(data, vm) }
                composable("appearance") { AppearanceScreen(preferences, vm) }
                composable("language") { LanguageScreen() }
                composable("privacy") { PrivacyScreen(pinStore, preferences, activity, vm) }
                composable("backup") { BackupScreen(preferences, vm) }
                composable("export") { ExportScreen() }
                composable("audit") {
                    var report by remember { mutableStateOf(context.getString(R.string.audit_description)) }
                    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Text(stringResource(R.string.ui_data_integrity), style = MaterialTheme.typography.headlineMedium) }
                        item { Button({ vm.run {
                            val snapshot = vm.repository.snapshot()
                            val problems = DataValidation.validate(snapshot, preferences) + snapshot.photos.filter { !vm.photos.file(it.localPath).isFile }.map { context.getString(R.string.audit_missing_photo, it.localPath) } + vm.repository.audit()
                            report = if (problems.isEmpty()) context.getString(R.string.audit_success, snapshot.entries.size, snapshot.photos.size) else problems.joinToString("\n")
                        } }) { Text(stringResource(R.string.ui_check_data_integrity)) } }
                        item { Text(report) }
                        item { OutlinedButton({ vm.run { vm.cleanPhotos(); report = context.getString(R.string.audit_cleaned) } }) { Text(stringResource(R.string.ui_clean_abandoned_photo_imports)) } }
                    }
                }
                composable("about") { LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text(stringResource(R.string.ui_moodiary), style = MaterialTheme.typography.headlineLarge); Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME)) }
                    item { Text(stringResource(R.string.ui_no_advertising_tracking_or_mandatory_account_journal_content)) }
                    item { Text(stringResource(R.string.ui_statistics_methodology), style = MaterialTheme.typography.titleLarge); Text(stringResource(R.string.ui_entries_retain_their_mood_score_when_you_customize_moods_tre)) }
                    item { Text(stringResource(R.string.ui_dates_and_privacy), style = MaterialTheme.typography.titleLarge); Text(stringResource(R.string.ui_entry_timestamps_are_absolute_instants_shown_in_the_current_)) }
                } }
            }
            if (busy && ready) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}
