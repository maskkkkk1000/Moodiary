package app.moodiary.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.moodiary.core.datastore.StatisticsFilterStore
import app.moodiary.data.repository.JournalRepository
import app.moodiary.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class StatisticsUiState(
    val data: JournalData = JournalData(),
    val filters: StatisticsFilters = StatisticsFilters(),
    val result: SelectedStatistics? = null,
    val loading: Boolean = true,
    val error: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel class StatisticsViewModel @Inject constructor(
    repository: JournalRepository,
    private val store: StatisticsFilterStore
) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    private val _writeError = MutableStateFlow(false)
    val writeError = _writeError.asStateFlow()
    // Re-evaluate after midnight or a time-zone change even when Room has not changed.
    private val clock = flow {
        while (true) { emit(ZoneId.systemDefault().let { it to LocalDate.now(it) }); delay(30_000) }
    }.distinctUntilChanged()

    val state = refresh.flatMapLatest {
        combine(repository.data, store.filters, clock) { data, filters, now -> Triple(data, filters, now) }
            .transformLatest { (data, filters, now) ->
                emit(StatisticsUiState(data = data, filters = filters, loading = true))
                delay(100) // Coalesce rapid multi-select taps; calculation is cancellable on new state.
                try {
                    val result = withContext(Dispatchers.Default) { StatisticsCalculator.calculate(data, filters, now.first, now.second) }
                    emit(StatisticsUiState(data, filters, result, loading = false))
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { emit(StatisticsUiState(data, filters, loading = false, error = true)) }
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                emit(StatisticsUiState(loading = false, error = true))
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState())

    fun update(transform: (StatisticsFilters) -> StatisticsFilters) = viewModelScope.launch {
        try { store.update(transform); _writeError.value = false }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _writeError.value = true }
    }
    fun reset() = viewModelScope.launch {
        try { store.reset(); _writeError.value = false; refresh.value += 1 }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _writeError.value = true }
    }
    fun retry() { refresh.value += 1 }
}
