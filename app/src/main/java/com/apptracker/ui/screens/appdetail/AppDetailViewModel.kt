package com.apptracker.ui.screens.appdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptracker.data.db.dao.WatchedAppDao
import com.apptracker.data.db.entity.WatchedAppEntity
import com.apptracker.data.model.AppInfo
import com.apptracker.domain.model.RiskScore
import com.apptracker.domain.usecase.CalculateRiskScoreUseCase
import com.apptracker.domain.usecase.GenerateReportUseCase
import com.apptracker.domain.usecase.GetAppDetailUseCase
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
class AppDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAppDetail: GetAppDetailUseCase,
    private val calculateRiskScore: CalculateRiskScoreUseCase,
    private val generateReport: GenerateReportUseCase,
    private val watchedAppDao: WatchedAppDao,
    private val getInstalledApps: GetInstalledAppsUseCase
) : ViewModel() {

    private val packageName: String = savedStateHandle.get<String>("packageName") ?: ""

    private val _uiState = MutableStateFlow(AppDetailUiState())
    val uiState: StateFlow<AppDetailUiState> = _uiState.asStateFlow()

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps: StateFlow<List<AppInfo>> = _allApps.asStateFlow()

    val isWatched: StateFlow<Boolean> = watchedAppDao.isWatched(packageName)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        loadAppDetail()
        loadAllApps()
    }

    fun loadAppDetail() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val app = getAppDetail(packageName)
                if (app != null) {
                    val riskScore = calculateRiskScore(app)
                    _uiState.value = AppDetailUiState(
                        isLoading = false,
                        app = app,
                        riskScore = riskScore
                    )
                } else {
                    _uiState.value = AppDetailUiState(
                        isLoading = false,
                        error = "App not found"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = AppDetailUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load app details"
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
        val app = _uiState.value.app ?: return null
        return generateReport(app, _uiState.value.riskScore)
    }
}

data class AppDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val app: AppInfo? = null,
    val riskScore: RiskScore? = null,
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
