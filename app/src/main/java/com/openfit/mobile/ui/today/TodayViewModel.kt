package com.openfit.mobile.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openfit.mobile.AppContainer
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
    data class Success(val bundle: HealthSnapshotBundle, val accountEmail: String?) : TodayUiState
}

/** Reads from whichever [com.openfit.mobile.data.health.HealthDataSource]
 * the user picked in Settings (Health Connect by default, or the Google
 * Health API cloud path) - the rest of the UI is identical either way. */
class TodayViewModel(private val container: AppContainer) : ViewModel() {
    private val _uiState = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh(date: String = LocalDate.now().toString()) {
        viewModelScope.launch {
            _uiState.value = TodayUiState.Loading
            val settings = container.settingsRepository.settingsFlow.first()
            val source = container.activeHealthDataSource(settings.dataSourceKind)
            if (!source.isConnected()) {
                _uiState.value = TodayUiState.NotConnected
                return@launch
            }
            try {
                val bundle = source.sync(date)
                val accountLabel = when (settings.dataSourceKind) {
                    HealthDataSourceKind.HEALTH_CONNECT -> "Health Connect"
                    HealthDataSourceKind.GOOGLE_HEALTH_API -> container.authManager.currentAccountEmail()
                }
                _uiState.value = TodayUiState.Success(bundle, accountLabel)
            } catch (e: Exception) {
                _uiState.value = TodayUiState.Error(e.message ?: "Failed to sync health data.")
            }
        }
    }
}
