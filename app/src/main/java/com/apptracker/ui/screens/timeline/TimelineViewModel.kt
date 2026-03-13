package com.apptracker.ui.screens.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptracker.data.db.dao.AppOpsDao
import com.apptracker.data.db.entity.AppOpsHistoryEntity
import com.apptracker.data.model.UsageTimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val appOpsDao: AppOpsDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    init {
        loadTimeline()
    }

    fun loadTimeline() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val since = computeSince(
                    _uiState.value.selectedPeriod,
                    _uiState.value.customPeriodDays
                )
                appOpsDao.getOpsSince(since).collectLatest { ops ->
                    _uiState.value = TimelineUiState(
                        isLoading = false,
                        entries = ops,
                        filteredEntries = filterEntries(ops, _uiState.value.selectedFilter),
                        selectedFilter = _uiState.value.selectedFilter,
                        selectedPeriod = _uiState.value.selectedPeriod,
                        customPeriodDays = _uiState.value.customPeriodDays
                    )
                }
            } catch (e: Exception) {
                _uiState.value = TimelineUiState(
                    isLoading = false,
                    error = e.message,
                    selectedFilter = _uiState.value.selectedFilter,
                    selectedPeriod = _uiState.value.selectedPeriod,
                    customPeriodDays = _uiState.value.customPeriodDays
                )
            }
        }
    }

    fun onFilterChange(filter: TimelineFilter) {
        _uiState.value = _uiState.value.copy(
            selectedFilter = filter,
            filteredEntries = filterEntries(_uiState.value.entries, filter)
        )
    }

    fun onPeriodChange(period: UsageTimeRange) {
        _uiState.value = _uiState.value.copy(
            selectedPeriod = period,
            customPeriodDays = null
        )
        loadTimeline()
    }

    fun onCustomPeriodDays(days: Int) {
        if (days <= 0) return
        _uiState.value = _uiState.value.copy(customPeriodDays = days)
        loadTimeline()
    }

    private fun computeSince(period: UsageTimeRange, customDays: Int?): Long {
        val days = customDays ?: period.days
        return System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
    }

    private fun filterEntries(
        entries: List<AppOpsHistoryEntity>,
        filter: TimelineFilter
    ): List<AppOpsHistoryEntity> {
        return when (filter) {
            TimelineFilter.ALL -> entries
            TimelineFilter.LOCATION -> entries.filter {
                it.opName.contains("LOCATION", ignoreCase = true)
            }
            TimelineFilter.CAMERA -> entries.filter {
                it.opName.contains("CAMERA", ignoreCase = true)
            }
            TimelineFilter.MICROPHONE -> entries.filter {
                it.opName.contains("AUDIO", ignoreCase = true) ||
                        it.opName.contains("RECORD", ignoreCase = true)
            }
            TimelineFilter.CONTACTS -> entries.filter {
                it.opName.contains("CONTACT", ignoreCase = true)
            }
            TimelineFilter.STORAGE -> entries.filter {
                it.opName.contains("STORAGE", ignoreCase = true) ||
                        it.opName.contains("EXTERNAL", ignoreCase = true)
            }
        }
    }
}

data class TimelineUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val entries: List<AppOpsHistoryEntity> = emptyList(),
    val filteredEntries: List<AppOpsHistoryEntity> = emptyList(),
    val selectedFilter: TimelineFilter = TimelineFilter.ALL,
    val selectedPeriod: UsageTimeRange = UsageTimeRange.LAST_24_HOURS,
    val customPeriodDays: Int? = null
)

enum class TimelineFilter(val label: String) {
    ALL("All"),
    LOCATION("Location"),
    CAMERA("Camera"),
    MICROPHONE("Microphone"),
    CONTACTS("Contacts"),
    STORAGE("Storage")
}
