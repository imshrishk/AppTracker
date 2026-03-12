package com.apptracker.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptracker.data.db.dao.WatchedAppDao
import com.apptracker.data.model.AppInfo
import com.apptracker.domain.usecase.GetInstalledAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getInstalledApps: GetInstalledAppsUseCase,
    private val watchedAppDao: WatchedAppDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    val watchedApps = watchedAppDao.getAllWatched()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val apps = getInstalledApps(includeSystem = false)
                val totalApps = apps.size
                val highRiskApps = apps.filter { it.riskScore >= 45 }
                val topBatteryApps = apps
                    .filter { it.batteryUsage != null }
                    .sortedByDescending { it.batteryUsage!!.foregroundTimeMs + it.batteryUsage!!.backgroundTimeMs }
                    .take(5)
                val topNetworkApps = apps
                    .filter { it.networkUsage != null }
                    .sortedByDescending { it.networkUsage!!.totalBytes }
                    .take(5)
                val dangerousPermissionCount = apps.sumOf { app ->
                    app.permissions.count { it.isDangerous && it.isGranted }
                }
                val avgRiskScore = if (apps.isNotEmpty()) {
                    apps.sumOf { it.riskScore } / apps.size
                } else 0

                _uiState.value = DashboardUiState(
                    isLoading = false,
                    allApps = apps,
                    totalApps = totalApps,
                    highRiskApps = highRiskApps,
                    topBatteryApps = topBatteryApps,
                    topNetworkApps = topNetworkApps,
                    totalDangerousPermissions = dangerousPermissionCount,
                    averageRiskScore = avgRiskScore,
                    recentlyUpdatedApps = apps
                        .sortedByDescending { it.lastUpdateTime }
                        .take(5)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load data"
                )
            }
        }
    }
}

data class DashboardUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val allApps: List<AppInfo> = emptyList(),
    val totalApps: Int = 0,
    val highRiskApps: List<AppInfo> = emptyList(),
    val topBatteryApps: List<AppInfo> = emptyList(),
    val topNetworkApps: List<AppInfo> = emptyList(),
    val totalDangerousPermissions: Int = 0,
    val averageRiskScore: Int = 0,
    val recentlyUpdatedApps: List<AppInfo> = emptyList()
)
