package com.openfit.mobile.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openfit.mobile.AppContainer
import com.openfit.mobile.model.DailySnapshot
import com.openfit.mobile.model.ExerciseSession
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

// ── View Mode ─────────────────────────────────────────────────────────────────

enum class CalendarViewMode { MONTH, WEEK }

// ── Day indicators: which data types are present for a given calendar day ─────

data class DayIndicators(
    val hasSteps: Boolean = false,
    val hasSleep: Boolean = false,
    val hasHR: Boolean = false,
) {
    val hasAny: Boolean get() = hasSteps || hasSleep || hasHR
}

// ── Calendar UI state ─────────────────────────────────────────────────────────

sealed interface CalendarUiState {
    /** Initial load — show shimmer skeleton. */
    data object Loading : CalendarUiState

    /** Calendar grid is ready. isLoadingMonth = thin progress stripe while a
     *  background month fetch is in flight (grid still fully interactive). */
    data class Ready(
        val indicators: Map<String, DayIndicators>,   // keyed by yyyy-MM-dd
        val isLoadingMonth: Boolean = false,
    ) : CalendarUiState

    data class Error(val message: String) : CalendarUiState
}

// ── Day detail state ──────────────────────────────────────────────────────────

sealed interface DayDetailState {
    data object None : DayDetailState
    data object Loading : DayDetailState
    data class Loaded(
        val snapshot: DailySnapshot,
        val exercises: List<ExerciseSession> = emptyList(),
    ) : DayDetailState
    data class Error(val message: String) : DayDetailState
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class CalendarViewModel(private val container: AppContainer) : ViewModel() {

    private val _calendarState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val calendarState: StateFlow<CalendarUiState> = _calendarState.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    private val _dayDetail = MutableStateFlow<DayDetailState>(DayDetailState.None)
    val dayDetail: StateFlow<DayDetailState> = _dayDetail.asStateFlow()

    private val _viewMode = MutableStateFlow(CalendarViewMode.MONTH)
    val viewMode: StateFlow<CalendarViewMode> = _viewMode.asStateFlow()

    /** date-string → snapshot; persists across month navigations */
    private val snapshotCache = mutableMapOf<String, DailySnapshot>()

    /** Months whose full window has been fetched — avoids redundant API calls */
    private val loadedMonths = mutableSetOf<YearMonth>()

    init {
        loadMonth(YearMonth.now())
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Called by the pager whenever the displayed month settles on a page. */
    fun loadMonth(month: YearMonth) {
        viewModelScope.launch {
            if (loadedMonths.contains(month)) {
                // Already fetched — just refresh the indicator map from cache.
                rebuildIndicators(isLoading = false)
                return@launch
            }

            val curr = _calendarState.value
            _calendarState.value = when (curr) {
                is CalendarUiState.Ready -> curr.copy(isLoadingMonth = true)
                else -> CalendarUiState.Loading
            }

            try {
                val settings = container.settingsRepository.settingsFlow.first()
                val source = container.activeHealthDataSource(settings.dataSourceKind)

                if (!source.isConnected()) {
                    _calendarState.value = CalendarUiState.Error("Not connected to health data")
                    return@launch
                }

                val today = LocalDate.now()
                val endOfMonth = month.atEndOfMonth()
                val clampedEnd = minOf(endOfMonth, today)
                val startOfMonth = month.atDay(1)

                // Nothing to fetch for fully-future months
                if (clampedEnd < startOfMonth) {
                    rebuildIndicators(isLoading = false)
                    return@launch
                }

                // Each sync() call returns a 14-day trend window ending at the
                // anchor date.  Three anchors cover any calendar month.
                val anchors = buildList {
                    var anchor = clampedEnd
                    while (anchor >= startOfMonth) {
                        add(anchor)
                        anchor = anchor.minusDays(14)
                    }
                }

                // Fetch all windows in parallel; individual failures are swallowed
                // so one bad window doesn't abort the whole month load.
                coroutineScope {
                    anchors.map { anchorDate ->
                        async {
                            runCatching {
                                val bundle = source.sync(anchorDate.toString())
                                synchronized(snapshotCache) {
                                    bundle.trend.forEach { snapshotCache[it.date] = it }
                                    snapshotCache[bundle.today.date] = bundle.today
                                }
                            }
                        }
                    }.forEach { it.await() }
                }

                loadedMonths.add(month)
                rebuildIndicators(isLoading = false)
            } catch (e: Exception) {
                val curr2 = _calendarState.value
                if (curr2 is CalendarUiState.Ready) {
                    _calendarState.value = curr2.copy(isLoadingMonth = false)
                } else {
                    _calendarState.value = CalendarUiState.Error(
                        e.message ?: "Failed to load calendar data"
                    )
                }
            }
        }
    }

    /** Toggle selection: tapping an already-selected day deselects it. */
    fun selectDate(date: LocalDate) {
        if (_selectedDate.value == date) {
            _selectedDate.value = null
            _dayDetail.value = DayDetailState.None
        } else {
            _selectedDate.value = date
            loadDayDetail(date)
        }
    }

    fun clearSelection() {
        _selectedDate.value = null
        _dayDetail.value = DayDetailState.None
    }

    fun setViewMode(mode: CalendarViewMode) {
        _viewMode.value = mode
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Load the full detail for a tapped day.  Shows cached snapshot
     *  immediately while a fresh sync runs in the background. */
    private fun loadDayDetail(date: LocalDate) {
        _dayDetail.value = DayDetailState.Loading
        viewModelScope.launch {
            try {
                val settings = container.settingsRepository.settingsFlow.first()
                val source = container.activeHealthDataSource(settings.dataSourceKind)

                if (source.isConnected()) {
                    val bundle = source.sync(date.toString())
                    synchronized(snapshotCache) {
                        bundle.trend.forEach { snapshotCache[it.date] = it }
                        snapshotCache[bundle.today.date] = bundle.today
                    }
                    _dayDetail.value = DayDetailState.Loaded(bundle.today, bundle.exercises)
                    rebuildIndicators(isLoading = false)
                } else {
                    val cached = snapshotCache[date.toString()]
                    _dayDetail.value = if (cached != null)
                        DayDetailState.Loaded(cached)
                    else
                        DayDetailState.Error("Not connected to health data")
                }
            } catch (e: Exception) {
                val cached = snapshotCache[date.toString()]
                _dayDetail.value = if (cached != null)
                    DayDetailState.Loaded(cached)
                else
                    DayDetailState.Error(e.message ?: "Failed to load day data")
            }
        }
    }

    private fun rebuildIndicators(isLoading: Boolean) {
        val indicators = synchronized(snapshotCache) {
            snapshotCache.mapValues { (_, snap) ->
                DayIndicators(
                    hasSteps = (snap.steps ?: 0) > 0,
                    hasSleep = snap.sleep != null && snap.sleep.totalMinutes > 0,
                    hasHR = snap.restingHeartRateBpm != null || snap.heartRateAvgBpm != null,
                )
            }
        }
        _calendarState.value = CalendarUiState.Ready(
            indicators = indicators,
            isLoadingMonth = isLoading,
        )
    }
}
