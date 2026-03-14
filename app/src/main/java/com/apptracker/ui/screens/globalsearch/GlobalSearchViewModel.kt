package com.apptracker.ui.screens.globalsearch

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptracker.data.db.dao.AppTrustLabelDao
import com.apptracker.data.model.AppInfo
import com.apptracker.domain.usecase.CalculateRiskScoreUseCase
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

data class GlobalSearchUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val results: List<AppInfo> = emptyList(),
    val rememberSearchFilters: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val getInstalledApps: GetInstalledAppsUseCase,
    private val calculateRiskScore: CalculateRiskScoreUseCase,
    private val appTrustLabelDao: AppTrustLabelDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlobalSearchUiState())
    val uiState: StateFlow<GlobalSearchUiState> = _uiState.asStateFlow()

    private var allApps: List<AppInfo> = emptyList()
    private var trustLabelsByPackage: Map<String, String> = emptyMap()
    private var persistSearchQueries: Boolean = true

    init {
        observeSearchPersistence()
        observeSearchQuery()
        observeTrustLabels()
        load()
    }

    private fun observeSearchPersistence() {
        viewModelScope.launch {
            OnboardingPreferences.searchQueryPersistence(context).collectLatest { enabled ->
                persistSearchQueries = enabled
                _uiState.value = _uiState.value.copy(rememberSearchFilters = enabled)
                if (!enabled && _uiState.value.query.isNotBlank()) {
                    _uiState.value = _uiState.value.copy(query = "")
                    _uiState.value = _uiState.value.copy(results = filterApps(""))
                }
            }
        }
    }

    private fun observeSearchQuery() {
        viewModelScope.launch {
            OnboardingPreferences.globalSearchQuery(context).collectLatest { query ->
                if (!persistSearchQueries) return@collectLatest
                if (_uiState.value.query == query) return@collectLatest
                _uiState.value = _uiState.value.copy(query = query)
                _uiState.value = _uiState.value.copy(results = filterApps(query))
            }
        }
    }

    private fun observeTrustLabels() {
        viewModelScope.launch {
            appTrustLabelDao.getAll().collectLatest { labels ->
                trustLabelsByPackage = labels.associate { it.packageName to it.label }
                _uiState.value = _uiState.value.copy(results = filterApps(_uiState.value.query))
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                allApps = getInstalledApps(includeSystem = true)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    results = filterApps(_uiState.value.query)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Search unavailable")
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        _uiState.value = _uiState.value.copy(results = filterApps(query))
        if (persistSearchQueries) {
            viewModelScope.launch {
                OnboardingPreferences.setGlobalSearchQuery(context, query)
            }
        }
    }

    fun setSearchFilterMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            OnboardingPreferences.setSearchQueryPersistence(context, enabled)
        }
    }

    private fun filterApps(rawQuery: String): List<AppInfo> {
        val normalized = rawQuery.trim()
        if (normalized.isBlank()) return allApps

        val tokens = normalized
            .lowercase()
            .split(Regex("[\\s,]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return allApps.filter { app ->
            val trustLabel = trustLabelsByPackage[app.packageName].orEmpty()
            val riskFlags = calculateRiskScore(app).flags
            tokens.all { token ->
                matchesToken(app, token, trustLabel, riskFlags)
            }
        }
    }

    private fun matchesToken(
        app: AppInfo,
        token: String,
        trustLabel: String,
        riskFlags: List<com.apptracker.domain.model.RiskFlag>
    ): Boolean {
        return app.appName.lowercase().contains(token) ||
            app.packageName.lowercase().contains(token) ||
            app.permissions.any { permission ->
                permission.permissionName.lowercase().contains(token) ||
                    (permission.group ?: "").lowercase().contains(token)
            } ||
            app.category.label.lowercase().contains(token) ||
            app.installSourceLabel.lowercase().contains(token) ||
            trustLabel.lowercase().contains(token) ||
            riskFlags.any { flag ->
                flag.title.lowercase().contains(token) || flag.description.lowercase().contains(token)
            } ||
            (token.contains("sideload") && app.isSideloaded) ||
            ((token == "high" || token == "high risk") && app.riskScore >= 70) ||
            (token == "critical" && app.riskScore >= 85) ||
            (token == "medium" && app.riskScore in 45..69) ||
            ((token == "low" || token == "low risk") && app.riskScore < 45) ||
            (token.contains("camera") && app.permissions.any { it.permissionName.contains("CAMERA", ignoreCase = true) }) ||
            (token.contains("microphone") && app.permissions.any { it.permissionName.contains("RECORD_AUDIO", ignoreCase = true) }) ||
            (token.contains("location") && app.permissions.any { it.permissionName.contains("LOCATION", ignoreCase = true) })
    }
}
