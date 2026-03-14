package com.apptracker.ui.screens.remediation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptracker.data.model.AppCategory
import com.apptracker.data.model.AppInfo
import com.apptracker.domain.usecase.GetInstalledAppsUseCase
import com.apptracker.util.OnboardingPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RemediationPlanUi(
    val reasons: List<String>,
    val actions: List<String>,
    val safeAlternative: String?
)

@HiltViewModel
class RemediationViewModel @Inject constructor(
    private val getInstalledApps: GetInstalledAppsUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemediationUiState())
    val uiState: StateFlow<RemediationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            OnboardingPreferences.highRiskThreshold(context).collectLatest { threshold ->
                _uiState.value = _uiState.value.copy(highRiskThreshold = threshold)
                load()
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val apps = getInstalledApps(includeSystem = false)
                val risky = apps
                    .filter { it.riskScore >= _uiState.value.highRiskThreshold }
                    .sortedByDescending { it.riskScore }
                    .take(8)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    riskyApps = risky
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load remediation apps"
                )
            }
        }
    }

    fun recommendations(app: AppInfo): List<String> {
        val recs = mutableListOf<String>()
        val dangerous = app.permissions.filter { it.isDangerous && it.isGranted }
        if (dangerous.isNotEmpty()) recs += "Review and revoke unused dangerous permissions"
        if ((app.batteryUsage?.backgroundTimeMs ?: 0L) > 60L * 60L * 1000L) {
            recs += "Restrict background battery usage"
        }
        if ((app.networkUsage?.backgroundBytes ?: 0L) > 10L * 1024 * 1024) {
            recs += "Review background data access"
        }
        safeAlternativeFor(app)?.let { alt ->
            recs += "Safer alternative to evaluate: $alt"
        }
        if (recs.isEmpty()) recs += "Open app settings and review permissions"
        return recs.take(3)
    }

    fun remediationPlan(app: AppInfo): RemediationPlanUi {
        val reasons = mutableListOf<String>()
        val dangerous = app.permissions.filter { it.isDangerous && it.isGranted }
        if (dangerous.isNotEmpty()) {
            reasons += "${dangerous.size} dangerous permission${if (dangerous.size == 1) "" else "s"} granted"
        }
        val backgroundHours = ((app.batteryUsage?.backgroundTimeMs ?: 0L) / (60L * 60L * 1000L)).toInt()
        if (backgroundHours >= 1) {
            reasons += "$backgroundHours h background activity recorded"
        }
        val backgroundMb = ((app.networkUsage?.backgroundBytes ?: 0L) / (1024L * 1024L)).toInt()
        if (backgroundMb >= 10) {
            reasons += "$backgroundMb MB background data used"
        }
        if (app.isSideloaded) {
            reasons += "Installed from outside a known app store"
        }
        if (reasons.isEmpty()) {
            reasons += "Elevated overall risk score based on combined signals"
        }

        return RemediationPlanUi(
            reasons = reasons.take(3),
            actions = recommendations(app),
            safeAlternative = safeAlternativeFor(app)
        )
    }

    private fun safeAlternativeFor(app: AppInfo): String? {
        if (app.riskScore < _uiState.value.highRiskThreshold) return null
        return when (app.category) {
            AppCategory.COMMUNICATION -> "Signal"
            AppCategory.BROWSER -> "Firefox or Brave"
            AppCategory.SOCIAL -> "Mastodon / privacy-first clients"
            AppCategory.MEDIA -> "VLC"
            AppCategory.TOOLS -> "Simple Mobile Tools alternatives"
            else -> null
        }
    }
}

data class RemediationUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val highRiskThreshold: Int = 45,
    val riskyApps: List<AppInfo> = emptyList()
)
