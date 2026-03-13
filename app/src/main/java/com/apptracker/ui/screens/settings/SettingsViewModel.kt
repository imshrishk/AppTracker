package com.apptracker.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptracker.data.db.dao.AppOpsDao
import com.apptracker.data.db.dao.BatteryHistoryDao
import com.apptracker.data.db.dao.NetworkHistoryDao
import com.apptracker.data.model.UsageTimeRange
import com.apptracker.domain.usecase.GetInstalledAppsUseCase
import com.apptracker.util.OnboardingPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appOpsDao: AppOpsDao,
    private val batteryHistoryDao: BatteryHistoryDao,
    private val networkHistoryDao: NetworkHistoryDao,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observePreferences()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            OnboardingPreferences.beginnerMode(context).collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(beginnerMode = enabled)
            }
        }
        viewModelScope.launch {
            OnboardingPreferences.onDeviceOnly(context).collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(onDeviceOnly = enabled)
            }
        }
        viewModelScope.launch {
            OnboardingPreferences.defaultUsageRange(context).collectLatest { range ->
                _uiState.value = _uiState.value.copy(defaultUsageRange = range)
            }
        }
    }

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

    fun setBeginnerMode(enabled: Boolean) {
        viewModelScope.launch {
            OnboardingPreferences.setBeginnerMode(context, enabled)
        }
    }

    fun setOnDeviceOnly(enabled: Boolean) {
        viewModelScope.launch {
            OnboardingPreferences.setOnDeviceOnly(context, enabled)
        }
    }

    fun setDefaultUsageRange(range: UsageTimeRange) {
        viewModelScope.launch {
            OnboardingPreferences.setDefaultUsageRange(context, range)
        }
    }

    fun exportCsv() {
        if (_uiState.value.onDeviceOnly) return
        viewModelScope.launch {
            val apps = getInstalledAppsUseCase(includeSystem = true)
            val csv = buildString {
                appendLine("appName,packageName,category,riskScore,dangerousPermissions,totalPermissions,foregroundMs,backgroundMs,totalNetworkBytes")
                apps.forEach { app ->
                    val dangerous = app.permissions.count { it.isDangerous && it.isGranted }
                    val fg = app.batteryUsage?.foregroundTimeMs ?: 0L
                    val bg = app.batteryUsage?.backgroundTimeMs ?: 0L
                    val net = app.networkUsage?.totalBytes ?: 0L
                    appendLine(
                        "\"${escapeCsv(app.appName)}\",${app.packageName},${app.category.label},${app.riskScore},$dangerous,${app.permissions.size},$fg,$bg,$net"
                    )
                }
            }
            _uiState.value = _uiState.value.copy(
                exportContent = csv,
                exportMimeType = "text/csv",
                exportFileName = "apptracker-export.csv"
            )
        }
    }

    fun exportJson() {
        if (_uiState.value.onDeviceOnly) return
        viewModelScope.launch {
            val apps = getInstalledAppsUseCase(includeSystem = true)
            val json = buildString {
                appendLine("[")
                apps.forEachIndexed { index, app ->
                    val dangerous = app.permissions.count { it.isDangerous && it.isGranted }
                    val fg = app.batteryUsage?.foregroundTimeMs ?: 0L
                    val bg = app.batteryUsage?.backgroundTimeMs ?: 0L
                    val net = app.networkUsage?.totalBytes ?: 0L
                    append("  {\"appName\":\"${escapeJson(app.appName)}\",\"packageName\":\"${escapeJson(app.packageName)}\",\"category\":\"${escapeJson(app.category.label)}\",\"riskScore\":${app.riskScore},\"dangerousPermissions\":$dangerous,\"totalPermissions\":${app.permissions.size},\"foregroundMs\":$fg,\"backgroundMs\":$bg,\"totalNetworkBytes\":$net}")
                    if (index < apps.lastIndex) append(',')
                    appendLine()
                }
                appendLine("]")
            }
            _uiState.value = _uiState.value.copy(
                exportContent = json,
                exportMimeType = "application/json",
                exportFileName = "apptracker-export.json"
            )
        }
    }

    fun clearPendingExport() {
        _uiState.value = _uiState.value.copy(
            exportContent = null,
            exportMimeType = null,
            exportFileName = null
        )
    }

    private fun escapeCsv(value: String): String = value.replace("\"", "\"\"")

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}

data class SettingsUiState(
    val refreshIntervalHours: Int = 6,
    val lastCleanup: String? = null,
    val showSystemApps: Boolean = false,
    val beginnerMode: Boolean = true,
    val onDeviceOnly: Boolean = true,
    val defaultUsageRange: UsageTimeRange = UsageTimeRange.LAST_24_HOURS,
    val exportContent: String? = null,
    val exportMimeType: String? = null,
    val exportFileName: String? = null
)
