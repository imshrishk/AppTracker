package com.apptracker.ui.screens.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptracker.data.db.dao.WatchedAppDao
import com.apptracker.data.model.AppInfo
import com.apptracker.data.model.UsageTimeRange
import com.apptracker.domain.usecase.GetInstalledAppsUseCase
import com.apptracker.util.OnboardingPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getInstalledApps: GetInstalledAppsUseCase,
    private val watchedAppDao: WatchedAppDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    val watchedApps = watchedAppDao.getAllWatched()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        observePreferences()
        loadData()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            OnboardingPreferences.beginnerMode(context).collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(isBeginnerMode = enabled)
            }
        }

        viewModelScope.launch {
            OnboardingPreferences.defaultUsageRange(context).collectLatest { range ->
                val previous = _uiState.value.selectedRange
                _uiState.value = _uiState.value.copy(selectedRange = range)
                if (previous != range) {
                    loadData(range)
                }
            }
        }
    }

    fun loadData(range: UsageTimeRange = _uiState.value.selectedRange) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val customStartTime = _uiState.value.customRangeDays?.let { days ->
                    System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
                }
                val apps = getInstalledApps(
                    includeSystem = false,
                    usageTimeRange = range,
                    customStartTimeMillis = customStartTime
                )

                val totalApps = apps.size
                val highRiskApps = apps.filter { it.riskScore >= 45 }
                val topBatteryApps = apps
                    .filter { it.batteryUsage != null }
                    .sortedByDescending {
                        (it.batteryUsage?.foregroundTimeMs ?: 0L) +
                                (it.batteryUsage?.backgroundTimeMs ?: 0L)
                    }
                    .take(5)
                val topNetworkApps = apps
                    .filter { it.networkUsage != null }
                    .sortedByDescending { it.networkUsage?.totalBytes ?: 0L }
                    .take(5)
                val dangerousPermissionCount = apps.sumOf { app ->
                    app.permissions.count { it.isDangerous && it.isGranted }
                }
                val avgRiskScore = if (apps.isNotEmpty()) {
                    apps.sumOf { it.riskScore } / apps.size
                } else 0
                val highBackgroundApps = apps.count {
                    (it.batteryUsage?.backgroundTimeMs ?: 0L) > 60L * 60L * 1000L
                }
                val healthScore = calculateDeviceHealthScore(
                    appCount = apps.size,
                    averageRiskScore = avgRiskScore,
                    highRiskCount = highRiskApps.size,
                    dangerousPermissionCount = dangerousPermissionCount,
                    highBackgroundAppCount = highBackgroundApps
                )

                _uiState.value = DashboardUiState(
                    isLoading = false,
                    allApps = apps,
                    totalApps = totalApps,
                    highRiskApps = highRiskApps,
                    topBatteryApps = topBatteryApps,
                    topNetworkApps = topNetworkApps,
                    totalDangerousPermissions = dangerousPermissionCount,
                    averageRiskScore = avgRiskScore,
                    deviceHealthScore = healthScore,
                    selectedRange = range,
                    customRangeDays = _uiState.value.customRangeDays,
                    isBeginnerMode = _uiState.value.isBeginnerMode,
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

    private fun calculateDeviceHealthScore(
        appCount: Int,
        averageRiskScore: Int,
        highRiskCount: Int,
        dangerousPermissionCount: Int,
        highBackgroundAppCount: Int
    ): Int {
        if (appCount <= 0) return 100

        val riskPenalty = (averageRiskScore * 0.45f).toInt()
        val highRiskPenalty = ((highRiskCount.toFloat() / appCount) * 30f).toInt()
        val dangerousPenalty = (dangerousPermissionCount / appCount).coerceAtMost(15)
        val backgroundPenalty = ((highBackgroundAppCount.toFloat() / appCount) * 10f).toInt()

        return (100 - (riskPenalty + highRiskPenalty + dangerousPenalty + backgroundPenalty))
            .coerceIn(0, 100)
    }

    fun onRangeSelected(range: UsageTimeRange) {
        if (range == _uiState.value.selectedRange) return
        _uiState.value = _uiState.value.copy(selectedRange = range, customRangeDays = null)
        viewModelScope.launch {
            OnboardingPreferences.setDefaultUsageRange(context, range)
        }
        loadData(range)
    }

    fun onCustomRangeDaysSelected(days: Int) {
        if (days <= 0) return
        _uiState.value = _uiState.value.copy(customRangeDays = days)
        loadData(_uiState.value.selectedRange)
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
    val deviceHealthScore: Int = 100,
    val selectedRange: UsageTimeRange = UsageTimeRange.LAST_24_HOURS,
    val customRangeDays: Int? = null,
    val isBeginnerMode: Boolean = true,
    val recentlyUpdatedApps: List<AppInfo> = emptyList()
)
