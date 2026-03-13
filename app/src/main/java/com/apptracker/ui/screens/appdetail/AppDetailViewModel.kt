package com.apptracker.ui.screens.appdetail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptracker.data.db.dao.AppOpsDao
import com.apptracker.data.db.entity.AppOpsHistoryEntity
import com.apptracker.data.db.dao.WatchedAppDao
import com.apptracker.data.db.entity.WatchedAppEntity
import com.apptracker.data.model.AppInfo
import com.apptracker.data.model.UsageTimeRange
import com.apptracker.data.repository.BatteryRepository
import com.apptracker.data.repository.NetworkRepository
import com.apptracker.domain.model.RiskScore
import com.apptracker.domain.usecase.CalculateRiskScoreUseCase
import com.apptracker.domain.usecase.GenerateReportUseCase
import com.apptracker.domain.usecase.GetAppDetailUseCase
import com.apptracker.domain.usecase.GetInstalledAppsUseCase
import com.apptracker.util.OnboardingPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAppDetail: GetAppDetailUseCase,
    private val calculateRiskScore: CalculateRiskScoreUseCase,
    private val generateReport: GenerateReportUseCase,
    private val watchedAppDao: WatchedAppDao,
    private val batteryRepository: BatteryRepository,
    private val networkRepository: NetworkRepository,
    private val appOpsDao: AppOpsDao,
    private val getInstalledApps: GetInstalledAppsUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val packageName: String = savedStateHandle.get<String>("packageName") ?: ""

    private val _uiState = MutableStateFlow(AppDetailUiState())
    val uiState: StateFlow<AppDetailUiState> = _uiState.asStateFlow()

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps: StateFlow<List<AppInfo>> = _allApps.asStateFlow()

    val isWatched: StateFlow<Boolean> = watchedAppDao.isWatched(packageName)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        observePreferences()
        loadAppDetail()
        loadAllApps()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            OnboardingPreferences.beginnerMode(context).collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(isBeginnerMode = enabled)
            }
        }
        viewModelScope.launch {
            OnboardingPreferences.onDeviceOnly(context).collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(onDeviceOnly = enabled)
            }
        }
        viewModelScope.launch {
            OnboardingPreferences.defaultUsageRange(context).collectLatest { range ->
                val previous = _uiState.value.selectedRange
                _uiState.value = _uiState.value.copy(selectedRange = range)
                if (previous != range) {
                    loadAppDetail(range)
                }
            }
        }
    }

    fun loadAppDetail(range: UsageTimeRange = _uiState.value.selectedRange) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val customStartTime = _uiState.value.customRangeDays?.let { days ->
                    System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
                }
                val app = getAppDetail(
                    packageName = packageName,
                    usageTimeRange = range,
                    customStartTimeMillis = customStartTime
                )
                if (app != null) {
                    val riskScore = calculateRiskScore(app)
                    val since = customStartTime ?: range.startTimeMillis()
                    val batteryHistoryDeferred = async { batteryRepository.getBatteryHistory(packageName).first() }
                    val networkHistoryDeferred = async { networkRepository.getNetworkHistory(packageName).first() }
                    val auditEntriesDeferred = async { appOpsDao.getOpsForPackage(packageName).first() }
                    val batteryHistory = batteryHistoryDeferred.await()
                    val networkHistory = networkHistoryDeferred.await()
                    val auditEntries = auditEntriesDeferred.await()
                    _uiState.value = AppDetailUiState(
                        isLoading = false,
                        app = app,
                        riskScore = riskScore,
                        batteryTrend = batteryHistory
                            .filter { it.timestamp >= since }
                            .take(7)
                            .reversed()
                            .map {
                                val value = ((it.foregroundTimeMs + it.backgroundTimeMs) / 60_000f)
                                formatDay(it.timestamp) to value
                            },
                        networkTrend = networkHistory
                            .filter { it.timestamp >= since }
                            .take(7)
                            .reversed()
                            .map {
                                val value = ((it.wifiRxBytes + it.wifiTxBytes + it.mobileRxBytes + it.mobileTxBytes) / 1_048_576f)
                                formatDay(it.timestamp) to value
                            },
                        permissionAuditEntries = auditEntries
                            .filter { it.lastAccessTime >= since || it.timestamp >= since }
                            .take(25),
                        selectedRange = range,
                        customRangeDays = _uiState.value.customRangeDays,
                        isBeginnerMode = _uiState.value.isBeginnerMode,
                        onDeviceOnly = _uiState.value.onDeviceOnly
                    )
                } else {
                    _uiState.value = AppDetailUiState(
                        isLoading = false,
                        error = "App not found",
                        selectedRange = range,
                        customRangeDays = _uiState.value.customRangeDays,
                        isBeginnerMode = _uiState.value.isBeginnerMode,
                        onDeviceOnly = _uiState.value.onDeviceOnly
                    )
                }
            } catch (e: Exception) {
                _uiState.value = AppDetailUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load app details",
                    selectedRange = range,
                    customRangeDays = _uiState.value.customRangeDays,
                    isBeginnerMode = _uiState.value.isBeginnerMode,
                    onDeviceOnly = _uiState.value.onDeviceOnly
                )
            }
        }
    }

    private fun loadAllApps() {
        viewModelScope.launch {
            try {
                _allApps.value = getInstalledApps(includeSystem = false)
            } catch (_: Exception) { }
        }
    }

    fun toggleWatch() {
        viewModelScope.launch {
            val app = _uiState.value.app ?: return@launch
            if (isWatched.value) {
                watchedAppDao.unwatch(packageName)
            } else {
                watchedAppDao.watch(
                    WatchedAppEntity(
                        packageName = packageName,
                        appName = app.appName
                    )
                )
            }
        }
    }

    fun getReport(): String? {
        if (_uiState.value.onDeviceOnly) return null
        val app = _uiState.value.app ?: return null
        return generateReport(app, _uiState.value.riskScore)
    }

    fun onRangeSelected(range: UsageTimeRange) {
        if (range == _uiState.value.selectedRange) return
        _uiState.value = _uiState.value.copy(selectedRange = range, customRangeDays = null)
        viewModelScope.launch {
            OnboardingPreferences.setDefaultUsageRange(context, range)
        }
        loadAppDetail(range)
    }

    fun onCustomRangeDaysSelected(days: Int) {
        if (days <= 0) return
        _uiState.value = _uiState.value.copy(customRangeDays = days)
        loadAppDetail(_uiState.value.selectedRange)
    }

    private fun formatDay(timestamp: Long): String {
        return SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestamp))
    }
}

data class AppDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val app: AppInfo? = null,
    val riskScore: RiskScore? = null,
    val batteryTrend: List<Pair<String, Float>> = emptyList(),
    val networkTrend: List<Pair<String, Float>> = emptyList(),
    val permissionAuditEntries: List<AppOpsHistoryEntity> = emptyList(),
    val selectedRange: UsageTimeRange = UsageTimeRange.LAST_24_HOURS,
    val customRangeDays: Int? = null,
    val isBeginnerMode: Boolean = true,
    val onDeviceOnly: Boolean = true,
    val selectedTab: DetailTab = DetailTab.PERMISSIONS
)

enum class DetailTab(val label: String) {
    PERMISSIONS("Permissions"),
    APP_OPS("Activity Log"),
    BATTERY("Battery"),
    NETWORK("Network"),
    RISK("Risk Analysis"),
    INFO("App Info")
}
