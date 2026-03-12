package com.apptracker.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptracker.data.db.dao.AppOpsDao
import com.apptracker.data.db.dao.BatteryHistoryDao
import com.apptracker.data.db.dao.NetworkHistoryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appOpsDao: AppOpsDao,
    private val batteryHistoryDao: BatteryHistoryDao,
    private val networkHistoryDao: NetworkHistoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun clearHistory(daysToKeep: Int) {
        viewModelScope.launch {
            val cutoff = System.currentTimeMillis() - (daysToKeep.toLong() * 24 * 60 * 60 * 1000)
            appOpsDao.deleteOlderThan(cutoff)
            batteryHistoryDao.deleteOlderThan(cutoff)
            networkHistoryDao.deleteOlderThan(cutoff)
            _uiState.value = _uiState.value.copy(
                lastCleanup = "Cleared data older than $daysToKeep days"
            )
        }
    }

    fun toggleRefreshInterval(hours: Int) {
        _uiState.value = _uiState.value.copy(refreshIntervalHours = hours)
    }
}

data class SettingsUiState(
    val refreshIntervalHours: Int = 6,
    val lastCleanup: String? = null,
    val showSystemApps: Boolean = false
)
