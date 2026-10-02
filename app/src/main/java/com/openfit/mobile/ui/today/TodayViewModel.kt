package com.openfit.mobile.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openfit.mobile.AppContainer
import com.openfit.mobile.data.health.BundleCache
import com.openfit.mobile.data.settings.HealthDataSourceKind
import com.openfit.mobile.model.HealthSnapshotBundle
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

            // Show cache immediately so the user sees data right after tapping
            // a summary notification, without waiting for a fresh sync.
            val cached = BundleCache.load(container.appContext, date)
            val alreadyHasData = _uiState.value is TodayUiState.Success
            if (!alreadyHasData) {
                if (cached != null) {
                    val label = when (settings.dataSourceKind) {
                        HealthDataSourceKind.HEALTH_CONNECT -> "Health Connect"
                        HealthDataSourceKind.GOOGLE_HEALTH_API -> container.authManager.currentAccountEmail()
                    }
                    _uiState.value = TodayUiState.Success(cached, label, isRefreshing = true)
                } else {
                    _uiState.value = TodayUiState.Loading
                }
            } else {
                // Already showing data — mark as refreshing without clearing the screen.
                _uiState.value = (_uiState.value as TodayUiState.Success).copy(isRefreshing = true)
            }

            if (!source.isConnected()) {
                if (!alreadyHasData && cached == null) _uiState.value = TodayUiState.NotConnected
                return@launch
            }
            try {
                val bundle = source.sync(date)
                BundleCache.save(container.appContext, bundle)
                val accountLabel = when (settings.dataSourceKind) {
                    HealthDataSourceKind.HEALTH_CONNECT -> "Health Connect"
                    HealthDataSourceKind.GOOGLE_HEALTH_API -> container.authManager.currentAccountEmail()
                }
                _uiState.value = TodayUiState.Success(bundle, accountLabel)
            } catch (e: Exception) {
                // Don't clobber cached/existing data with an error on a background refresh.
                if (_uiState.value !is TodayUiState.Success) {
                    _uiState.value = TodayUiState.Error(e.message ?: "Failed to sync health data.")
                }
            }
        }
    }
}
