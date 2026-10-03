package app.moodiary.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.moodiary.domain.StatisticsFilters
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.statisticsFilterDataStore by preferencesDataStore("statistics_filters")

/** Device-local presentation choices are independent of journal backup data. Atomic edits prevent
 * a rapid second selection overwriting the first while the first write is still in flight. */
@Singleton class StatisticsFilterStore internal constructor(private val store: DataStore<Preferences>) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.statisticsFilterDataStore)
    private val key = stringPreferencesKey("filters")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val filters = store.data.map { values -> values[key]?.let { json.decodeFromString<StatisticsFilters>(it) } ?: StatisticsFilters() }

    suspend fun update(transform: (StatisticsFilters) -> StatisticsFilters) {
        store.edit { values ->
            val current = values[key]?.let { json.decodeFromString<StatisticsFilters>(it) } ?: StatisticsFilters()
            values[key] = json.encodeToString(transform(current))
        }
    }

    /** Also recovers malformed preference JSON without touching journal data. */
    suspend fun reset() { store.edit { it.remove(key) } }
}
