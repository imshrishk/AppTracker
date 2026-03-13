package com.apptracker.ui.screens.applist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptracker.data.db.dao.WatchedAppDao
import com.apptracker.data.db.entity.WatchedAppEntity
import com.apptracker.data.model.AppInfo
import com.apptracker.data.model.AppCategory
import com.apptracker.domain.usecase.GetInstalledAppsUseCase
import com.apptracker.util.OnboardingPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppListViewModel @Inject constructor(
    private val getInstalledApps: GetInstalledAppsUseCase,
    private val watchedAppDao: WatchedAppDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    init {
        observePreferences()
        loadApps()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            OnboardingPreferences.beginnerMode(context).collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(isBeginnerMode = enabled)
            }
        }
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val apps = getInstalledApps(
                    includeSystem = _uiState.value.showSystemApps
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    allApps = apps,
                    filteredApps = applyFilters(apps, _uiState.value)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        _uiState.value = _uiState.value.copy(
            filteredApps = applyFilters(_uiState.value.allApps, _uiState.value)
        )
    }

    fun onSortChange(sort: SortOption) {
        _uiState.value = _uiState.value.copy(sortOption = sort)
        _uiState.value = _uiState.value.copy(
            filteredApps = applyFilters(_uiState.value.allApps, _uiState.value)
        )
    }

    fun onFilterChange(filter: FilterOption) {
        _uiState.value = _uiState.value.copy(filterOption = filter)
        _uiState.value = _uiState.value.copy(
            filteredApps = applyFilters(_uiState.value.allApps, _uiState.value)
        )
    }

    fun onCategoryChange(category: AppCategory?) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        _uiState.value = _uiState.value.copy(
            filteredApps = applyFilters(_uiState.value.allApps, _uiState.value)
        )
    }

    fun toggleSystemApps() {
        _uiState.value = _uiState.value.copy(
            showSystemApps = !_uiState.value.showSystemApps
        )
        loadApps()
    }

    fun watchApp(app: AppInfo) {
        viewModelScope.launch {
            watchedAppDao.watch(
                WatchedAppEntity(packageName = app.packageName, appName = app.appName)
            )
        }
    }

    fun unwatchApp(packageName: String) {
        viewModelScope.launch { watchedAppDao.unwatch(packageName) }
    }

    fun isWatched(packageName: String) =
        watchedAppDao.isWatched(packageName)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private fun applyFilters(apps: List<AppInfo>, state: AppListUiState): List<AppInfo> {
        var result = apps

        // Search
        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.lowercase()
            result = result.filter {
                it.appName.lowercase().contains(query) ||
                        it.packageName.lowercase().contains(query)
            }
        }

        // Filter
        result = when (state.filterOption) {
            FilterOption.ALL -> result
            FilterOption.HIGH_RISK -> result.filter { it.riskScore >= 45 }
            FilterOption.HAS_DANGEROUS -> result.filter { app ->
                app.permissions.any { it.isDangerous && it.isGranted }
            }
            FilterOption.USES_LOCATION -> result.filter { app ->
                app.permissions.any {
                    it.isGranted && it.permissionName.contains("LOCATION", ignoreCase = true)
                }
            }
            FilterOption.USES_CAMERA -> result.filter { app ->
                app.permissions.any {
                    it.isGranted && it.permissionName.contains("CAMERA", ignoreCase = true)
                }
            }
            FilterOption.USES_MICROPHONE -> result.filter { app ->
                app.permissions.any {
                    it.isGranted && it.permissionName.contains("RECORD_AUDIO", ignoreCase = true)
                }
            }
        }

        if (state.selectedCategory != null) {
            result = result.filter { it.category == state.selectedCategory }
        }

        // Sort
        result = when (state.sortOption) {
            SortOption.NAME_ASC -> result.sortedBy { it.appName.lowercase() }
            SortOption.NAME_DESC -> result.sortedByDescending { it.appName.lowercase() }
            SortOption.RISK_HIGH -> result.sortedByDescending { it.riskScore }
            SortOption.RISK_LOW -> result.sortedBy { it.riskScore }
            SortOption.PERMISSIONS_COUNT -> result.sortedByDescending {
                it.permissions.count { p -> p.isDangerous && p.isGranted }
            }
            SortOption.RECENTLY_UPDATED -> result.sortedByDescending { it.lastUpdateTime }
            SortOption.RECENTLY_INSTALLED -> result.sortedByDescending { it.installTime }
        }

        return result
    }
}

data class AppListUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val allApps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.RISK_HIGH,
    val filterOption: FilterOption = FilterOption.ALL,
    val selectedCategory: AppCategory? = null,
    val showSystemApps: Boolean = false,
    val isBeginnerMode: Boolean = true
)

enum class SortOption(val label: String) {
    NAME_ASC("Name A-Z"),
    NAME_DESC("Name Z-A"),
    RISK_HIGH("Highest Risk"),
    RISK_LOW("Lowest Risk"),
    PERMISSIONS_COUNT("Most Permissions"),
    RECENTLY_UPDATED("Recently Updated"),
    RECENTLY_INSTALLED("Recently Installed")
}

enum class FilterOption(val label: String) {
    ALL("All Apps"),
    HIGH_RISK("High Risk Only"),
    HAS_DANGEROUS("Has Dangerous Permissions"),
    USES_LOCATION("Uses Location"),
    USES_CAMERA("Uses Camera"),
    USES_MICROPHONE("Uses Microphone")
}
