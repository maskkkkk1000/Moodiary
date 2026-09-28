package app.moodiary.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.moodiary.domain.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore("settings")

@Singleton class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val key = stringPreferencesKey("preferences")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val preferences: Flow<AppPreferences> = context.settingsDataStore.data.map { values ->
        values[key]?.let { json.decodeFromString<AppPreferences>(it) } ?: AppPreferences()
    }
    suspend fun update(transform: (AppPreferences) -> AppPreferences) {
        context.settingsDataStore.edit { values ->
            val current = values[key]?.let { json.decodeFromString<AppPreferences>(it) } ?: AppPreferences()
            values[key] = json.encodeToString(transform(current))
        }
    }
}
