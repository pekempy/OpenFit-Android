package com.openfit.mobile.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openfit.mobile.AppContainer
import com.openfit.mobile.data.health.BundleCache
import com.openfit.mobile.data.health.CalendarCache
import com.openfit.mobile.model.DailySnapshot
import com.openfit.mobile.model.ExerciseSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

// ── View Mode ─────────────────────────────────────────────────────────────────

enum class CalendarViewMode { MONTH, WEEK }

// ── Day indicators ────────────────────────────────────────────────────────────

data class DayIndicators(
    val hasSteps: Boolean = false,
    val hasSleep: Boolean = false,
    val hasHR: Boolean = false,
) {
    val hasAny: Boolean get() = hasSteps || hasSleep || hasHR
    fun toCacheEntry() = CalendarCache.DayEntry(hasSteps, hasSleep, hasHR)
}

fun CalendarCache.DayEntry.toIndicators() = DayIndicators(hasSteps, hasSleep, hasHR)

// ── Calendar UI state ─────────────────────────────────────────────────────────

sealed interface CalendarUiState {
    data object Loading : CalendarUiState
    data class Ready(
        val indicators: Map<String, DayIndicators>,
        val isLoadingMonth: Boolean = false,
        val dataAvailableSince: LocalDate? = null,
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

    /** Persisted indicator dots — survives process restarts via CalendarCache. */
    private val indicatorCache = mutableMapOf<String, DayIndicators>()

    /** Full DailySnapshot objects — in-memory only, used for day detail. */
    private val snapshotCache = mutableMapOf<String, DailySnapshot>()

    /** Months whose indicators have been fully loaded from Health Connect.
     *  Past months are never re-fetched; current month is always refreshed. */
    private val loadedMonths = mutableSetOf<YearMonth>()

    /** Earliest date Health Connect actually has data for (null = unknown / Google Health API path). */
    private var dataAvailableSince: LocalDate? = null

    private var backfillJob: Job? = null
    private val _isBackfilling = MutableStateFlow(false)
    /** True while the background historical fetch is walking backwards through HC data. */
    val isBackfilling: StateFlow<Boolean> = _isBackfilling.asStateFlow()


    init {
        viewModelScope.launch {
            // 1. Restore persisted indicators from disk (instant, no HC call).
            withContext(Dispatchers.IO) { loadDiskCache() }

            // 2. Seed from the existing TodayViewModel bundle cache — gives the
            //    last 14 days for free without any additional Health Connect query.
            withContext(Dispatchers.IO) { seedFromBundleCache() }

            // 3. Probe HC for its earliest available date so we know which
            //    months to skip and can show an informational banner to the user.
            withContext(Dispatchers.IO) { probeEarliestDate() }

            // 4. Fetch current month fresh (today's data changes).
            loadMonth(YearMonth.now())

            // 5. Walk backwards through all available HC history in the background.
            //    Each 14-day window is fetched with a 1.5 s delay to be gentle on HC.
            startHistoricalBackfill()
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun loadMonth(month: YearMonth) {
        viewModelScope.launch {
            val thisMonth = YearMonth.now()

            // Don't attempt to fetch months that pre-date HC's earliest record.
            dataAvailableSince?.let { earliest ->
                if (month < YearMonth.from(earliest)) {
                    rebuildIndicators(isLoading = false)
                    return@launch
                }
            }

            // Past months that are already in the disk-backed set are done.
            if (month < thisMonth && loadedMonths.contains(month)) {
                rebuildIndicators(isLoading = false)
                return@launch
            }
            // Current month: always refetch (skip only if still in same launch).
            if (month == thisMonth && loadedMonths.contains(month) && indicatorCache.isNotEmpty()) {
                rebuildIndicators(isLoading = false)
                return@launch
            }

            val curr = _calendarState.value
            _calendarState.value = when (curr) {
                is CalendarUiState.Ready -> curr.copy(isLoadingMonth = true)
                else -> if (indicatorCache.isEmpty()) CalendarUiState.Loading
                        else curr  // keep showing cached data while loading
            }

            try {
                val settings = container.settingsRepository.settingsFlow.first()
                val source = container.activeHealthDataSource(settings.dataSourceKind)

                if (!source.isConnected()) {
                    // Offline — show whatever we have from cache
                    rebuildIndicators(isLoading = false)
                    if (indicatorCache.isEmpty())
                        _calendarState.value = CalendarUiState.Error("Not connected to health data")
                    return@launch
                }

                val today = LocalDate.now()
                val endOfMonth = month.atEndOfMonth()
                val clampedEnd = minOf(endOfMonth, today)
                val startOfMonth = month.atDay(1)

                if (clampedEnd < startOfMonth) {
                    rebuildIndicators(isLoading = false)
                    return@launch
                }

                // Each sync() returns a 14-day trend; 2–3 anchors cover a full month.
                val anchors = buildList {
                    var anchor = clampedEnd
                    while (anchor >= startOfMonth) {
                        add(anchor)
                        anchor = anchor.minusDays(14)
                    }
                }

                coroutineScope {
                    anchors.map { anchorDate ->
                        async {
                            runCatching {
                                val bundle = source.sync(anchorDate.toString())
                                synchronized(snapshotCache) {
                                    bundle.trend.forEach { snapshotCache[it.date] = it }
                                    snapshotCache[bundle.today.date] = bundle.today
                                }
                                // Update indicator cache from freshly loaded snapshots
                                synchronized(indicatorCache) {
                                    (bundle.trend + bundle.today).forEach { snap ->
                                        indicatorCache[snap.date] = DayIndicators(
                                            hasSteps = (snap.steps ?: 0) > 0,
                                            hasSleep = snap.sleep != null && snap.sleep.totalMinutes > 0,
                                            hasHR = snap.restingHeartRateBpm != null || snap.heartRateAvgBpm != null,
                                        )
                                    }
                                }
                            }
                        }
                    }.forEach { it.await() }
                }

                loadedMonths.add(month)
                rebuildIndicators(isLoading = false)

                // Persist updated indicators to disk (IO thread, best-effort).
                withContext(Dispatchers.IO) { persistDiskCache() }

            } catch (e: Exception) {
                val curr2 = _calendarState.value
                if (curr2 is CalendarUiState.Ready) {
                    _calendarState.value = curr2.copy(isLoadingMonth = false)
                } else {
                    rebuildIndicators(isLoading = false)
                    if (indicatorCache.isEmpty())
                        _calendarState.value = CalendarUiState.Error(e.message ?: "Failed to load calendar")
                }
            }
        }
    }

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

    private fun loadDayDetail(date: LocalDate) {
        // Show cached snapshot immediately if available.
        val cached = synchronized(snapshotCache) { snapshotCache[date.toString()] }
        _dayDetail.value = if (cached != null) DayDetailState.Loaded(cached)
                           else DayDetailState.Loading

        viewModelScope.launch {
            try {
                val settings = container.settingsRepository.settingsFlow.first()
                val source = container.activeHealthDataSource(settings.dataSourceKind)

                if (!source.isConnected()) {
                    if (cached == null)
                        _dayDetail.value = DayDetailState.Error("Not connected to health data")
                    return@launch
                }

                val bundle = source.sync(date.toString())
                synchronized(snapshotCache) {
                    bundle.trend.forEach { snapshotCache[it.date] = it }
                    snapshotCache[bundle.today.date] = bundle.today
                }
                synchronized(indicatorCache) {
                    (bundle.trend + bundle.today).forEach { snap ->
                        indicatorCache[snap.date] = DayIndicators(
                            hasSteps = (snap.steps ?: 0) > 0,
                            hasSleep = snap.sleep != null && snap.sleep.totalMinutes > 0,
                            hasHR = snap.restingHeartRateBpm != null || snap.heartRateAvgBpm != null,
                        )
                    }
                }
                _dayDetail.value = DayDetailState.Loaded(bundle.today, bundle.exercises)
                rebuildIndicators(isLoading = false)
                withContext(Dispatchers.IO) { persistDiskCache() }
            } catch (e: Exception) {
                if (cached == null)
                    _dayDetail.value = DayDetailState.Error(e.message ?: "Failed to load day data")
                // If we already showed cached data, leave it visible.
            }
        }
    }

    private fun rebuildIndicators(isLoading: Boolean) {
        val snapshot = synchronized(indicatorCache) { indicatorCache.toMap() }
        _calendarState.value = CalendarUiState.Ready(
            indicators = snapshot,
            isLoadingMonth = isLoading,
            dataAvailableSince = dataAvailableSince,
        )
    }

    /** Loads persisted indicators and marks past months as already loaded. */
    private fun loadDiskCache() {
        val loaded = CalendarCache.load(container.appContext) ?: return
        val thisMonth = YearMonth.now()
        synchronized(indicatorCache) {
            loaded.days.forEach { (date, entry) ->
                indicatorCache[date] = entry.toIndicators()
            }
        }
        // Only treat past months as fully loaded; always re-fetch current.
        loaded.fullMonths.forEach { s ->
            runCatching { YearMonth.parse(s) }.getOrNull()
                ?.takeIf { it < thisMonth }
                ?.let { loadedMonths.add(it) }
        }
    }

    /** Seeds indicator + snapshot cache from the existing BundleCache so the
     *  last 14 days appear immediately without any Health Connect query. */
    private fun seedFromBundleCache() {
        val bundle = BundleCache.load(container.appContext, LocalDate.now().toString()) ?: return
        synchronized(snapshotCache) {
            bundle.trend.forEach { snapshotCache[it.date] = it }
            snapshotCache[bundle.today.date] = bundle.today
        }
        synchronized(indicatorCache) {
            (bundle.trend + bundle.today).forEach { snap ->
                indicatorCache[snap.date] = DayIndicators(
                    hasSteps = (snap.steps ?: 0) > 0,
                    hasSleep = snap.sleep != null && snap.sleep.totalMinutes > 0,
                    hasHR = snap.restingHeartRateBpm != null || snap.heartRateAvgBpm != null,
                )
            }
        }
    }

    /** Persists indicatorCache + loadedMonths to disk. Called after every
     *  successful Health Connect fetch. Best-effort — failures are silent. */
    private fun persistDiskCache() {
        val daysCopy = synchronized(indicatorCache) {
            indicatorCache.mapValues { (_, v) -> v.toCacheEntry() }
        }
        val monthsCopy = loadedMonths.map { it.toString() }.toSet()
        CalendarCache.save(container.appContext, daysCopy, monthsCopy)
    }

    private suspend fun probeEarliestDate() {
        val settings = container.settingsRepository.settingsFlow.first()
        // Only meaningful for Health Connect; Google Health API has full history.
        if (settings.dataSourceKind != com.openfit.mobile.data.settings.HealthDataSourceKind.HEALTH_CONNECT) return
        dataAvailableSince = container.healthConnectRepository.getEarliestDataDate()
    }

    /**
     * Walks backwards through Health Connect data in 14-day windows with a
     * 1.5-second delay between each fetch. Stops when:
     *  - 3 consecutive windows return zero data (HC has no more history)
     *  - We've reached [dataAvailableSince] (we know where HC starts)
     *  - We've gone back 10 years (hard ceiling)
     * Updates the calendar dots progressively as each window loads.
     */
    private fun startHistoricalBackfill() {
        backfillJob?.cancel()
        backfillJob = viewModelScope.launch(Dispatchers.IO) {
            val settings = container.settingsRepository.settingsFlow.first()
            val source = container.activeHealthDataSource(settings.dataSourceKind)
            if (!source.isConnected()) return@launch

            _isBackfilling.value = true
            try {
                val today = LocalDate.now()
                // The current month's 14-day window is already loaded by loadMonth(now).
                // Begin backfill from the window immediately before it.
                var anchor = today.minusDays(14)
                var consecutiveEmpty = 0
                val hardStop  = today.minusYears(10)
                // If we know HC's earliest record, stop just before it (nothing older exists).
                val softStop  = dataAvailableSince?.minusDays(1)

                while (isActive && anchor > hardStop && consecutiveEmpty < 3) {
                    // Honour the known HC data boundary.
                    if (softStop != null && anchor < softStop) break

                    // Skip windows already fully covered by the indicator cache.
                    val alreadyCached = synchronized(indicatorCache) {
                        (0..13).any { d ->
                            indicatorCache[anchor.minusDays(d.toLong()).toString()]?.hasAny == true
                        }
                    }

                    if (alreadyCached) {
                        consecutiveEmpty = 0 // existing data resets the empty streak
                    } else {
                        val hasNewData = runCatching {
                            val bundle = source.sync(anchor.toString())
                            val days = bundle.trend + bundle.today
                            val any = days.any { snap ->
                                (snap.steps ?: 0) > 0 ||
                                snap.sleep != null ||
                                snap.restingHeartRateBpm != null ||
                                snap.heartRateAvgBpm != null
                            }
                            if (any) {
                                synchronized(snapshotCache) {
                                    bundle.trend.forEach { snapshotCache[it.date] = it }
                                    snapshotCache[bundle.today.date] = bundle.today
                                }
                                synchronized(indicatorCache) {
                                    days.forEach { snap ->
                                        indicatorCache[snap.date] = DayIndicators(
                                            hasSteps = (snap.steps ?: 0) > 0,
                                            hasSleep = snap.sleep != null && snap.sleep.totalMinutes > 0,
                                            hasHR    = snap.restingHeartRateBpm != null || snap.heartRateAvgBpm != null,
                                        )
                                    }
                                }
                                rebuildIndicators(isLoading = true)
                                // Persist to disk every ~4 weeks
                                if (anchor.dayOfMonth <= 14) persistDiskCache()
                            }
                            any
                        }.getOrElse { false }

                        if (hasNewData) consecutiveEmpty = 0 else consecutiveEmpty++
                    }

                    anchor = anchor.minusDays(14)
                    delay(1_500) // gentle — 1.5 s between 14-day HC reads
                }
            } finally {
                rebuildIndicators(isLoading = false)
                persistDiskCache()
                _isBackfilling.value = false
            }
        }
    }
}
