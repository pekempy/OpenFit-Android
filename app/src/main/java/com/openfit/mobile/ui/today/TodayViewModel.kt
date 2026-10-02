package com.openfit.mobile.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openfit.mobile.AppContainer
import com.openfit.mobile.data.health.BundleCache
import com.openfit.mobile.data.settings.HealthDataSourceKind
import com.openfit.mobile.model.HealthSnapshotBundle
import com.openfit.mobile.model.DailySnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data object NotConnected : TodayUiState
    data class Error(val message: String) : TodayUiState
    data class Success(val bundle: HealthSnapshotBundle, val accountEmail: String?, val isRefreshing: Boolean = false) : TodayUiState
}

/** Reads from whichever [com.openfit.mobile.data.health.HealthDataSource]
 * the user picked in Settings (Health Connect by default, or the Google
 * Health API cloud path) - the rest of the UI is identical either way.
 * On first open after a background worker run, the last synced bundle is
 * shown from [BundleCache] instantly while a fresh sync runs behind it. */
class TodayViewModel(private val container: AppContainer) : ViewModel() {
    private val _uiState = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh(date: String = LocalDate.now().toString()) {
        viewModelScope.launch {
            val settings = container.settingsRepository.settingsFlow.first()
            val source = container.activeHealthDataSource(settings.dataSourceKind)

            val cached = BundleCache.load(container.appContext, date)
            val alreadyHasData = _uiState.value is TodayUiState.Success

            val accountLabel = when (settings.dataSourceKind) {
                HealthDataSourceKind.HEALTH_CONNECT -> "Health Connect"
                HealthDataSourceKind.GOOGLE_HEALTH_API -> container.authManager.currentAccountEmail()
            }

            if (!alreadyHasData) {
                // Never block the UI with a spinner on launch.
                // Show cached data instantly if available, otherwise an empty bundle
                // so the layout appears immediately while the background sync runs.
                val initialBundle = cached ?: emptyBundle(date)
                _uiState.value = TodayUiState.Success(
                    bundle = initialBundle,
                    accountEmail = if (cached != null) accountLabel else null,
                    isRefreshing = true,
                )
            } else {
                _uiState.value = (_uiState.value as TodayUiState.Success).copy(isRefreshing = true)
            }

            if (!source.isConnected()) {
                // Show NotConnected only when genuinely no data has ever been seen.
                val hasRealData = (_uiState.value as? TodayUiState.Success)
                    ?.bundle?.fetchedAtEpochMillis?.let { it > 0L } == true
                if (!hasRealData) _uiState.value = TodayUiState.NotConnected
                return@launch
            }

            try {
                val bundle = source.sync(date)
                BundleCache.save(container.appContext, bundle)
                _uiState.value = TodayUiState.Success(bundle, accountLabel)
            } catch (e: Exception) {
                // Don't clobber real data with an error on a background refresh.
                val hasRealData = (_uiState.value as? TodayUiState.Success)
                    ?.bundle?.fetchedAtEpochMillis?.let { it > 0L } == true
                if (!hasRealData) {
                    _uiState.value = TodayUiState.Error(e.message ?: "Failed to sync health data.")
                }
            }
        }
    }

    private fun emptyBundle(date: String) = HealthSnapshotBundle(
        selectedDate          = date,
        today                 = DailySnapshot(date = date),
        trend                 = emptyList(),
        exercises             = emptyList(),
        fetchedAtEpochMillis  = 0L,
        partial               = false,
    )
}
