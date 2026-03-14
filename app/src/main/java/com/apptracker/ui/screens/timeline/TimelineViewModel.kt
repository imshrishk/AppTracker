package com.apptracker.ui.screens.timeline

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptracker.data.db.dao.AppOpsDao
import com.apptracker.data.db.dao.SecurityEventDao
import com.apptracker.data.db.entity.AppOpsHistoryEntity
import com.apptracker.data.db.entity.SecurityEventEntity
import com.apptracker.data.db.entity.SecurityEventType
import com.apptracker.data.model.UsageTimeRange
import com.apptracker.util.OnboardingPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val appOpsDao: AppOpsDao,
    private val securityEventDao: SecurityEventDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    private val selectedFilter = MutableStateFlow(TimelineFilter.ALL)
    private val selectedPeriod = MutableStateFlow(UsageTimeRange.LAST_24_HOURS)
    private val customPeriodDays = MutableStateFlow<Int?>(null)

    init {
        observeDefaultRange()
        viewModelScope.launch {
            combine(selectedFilter, selectedPeriod, customPeriodDays) { filter, period, customDays ->
                Triple(filter, period, customDays)
            }.flatMapLatest { (filter, period, customDays) ->
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    error = null,
                    selectedFilter = filter,
                    selectedPeriod = period,
                    customPeriodDays = customDays
                )
                val since = computeSince(period, customDays)
                combine(
                    appOpsDao.getOpsSince(since),
                    securityEventDao.getRecentEvents(since)
                ) { ops, events ->
                    buildState(filter, period, customDays, ops, events)
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun observeDefaultRange() {
        viewModelScope.launch {
            OnboardingPreferences.defaultUsageRange(context).collectLatest { range ->
                if (customPeriodDays.value != null || selectedPeriod.value == range) return@collectLatest
                selectedPeriod.value = range
            }
        }
    }

    fun loadTimeline() {
        _uiState.value = _uiState.value.copy(isLoading = true)
    }

    fun onFilterChange(filter: TimelineFilter) {
        selectedFilter.value = filter
    }

    fun onPeriodChange(period: UsageTimeRange) {
        selectedPeriod.value = period
        customPeriodDays.value = null
        viewModelScope.launch {
            OnboardingPreferences.setDefaultUsageRange(context, period)
        }
    }

    fun onCustomPeriodDays(days: Int) {
        if (days <= 0) return
        customPeriodDays.value = days
    }

    private fun buildState(
        filter: TimelineFilter,
        period: UsageTimeRange,
        customDays: Int?,
        ops: List<AppOpsHistoryEntity>,
        events: List<SecurityEventEntity>
    ): TimelineUiState {
        val combined = buildTimelineItems(ops, events)
        val filtered = filterEntries(combined, filter)
        return TimelineUiState(
            isLoading = false,
            entries = combined,
            filteredEntries = filtered,
            selectedFilter = filter,
            selectedPeriod = period,
            customPeriodDays = customDays,
            totalEntries = filtered.size,
            uniqueApps = filtered.mapNotNull { it.packageName }.distinct().size,
            topPackages = filtered
                .map { it.packageName?.substringAfterLast('.') ?: "device" }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(3)
                .map { TimelinePackageSummary(it.key, it.value) },
            lastActivityTimestamp = filtered.maxOfOrNull { it.timestamp } ?: 0L
        )
    }

    private fun computeSince(period: UsageTimeRange, customDays: Int?): Long {
        val days = customDays ?: period.days
        return System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
    }

    private fun buildTimelineItems(
        ops: List<AppOpsHistoryEntity>,
        events: List<SecurityEventEntity>
    ): List<TimelineItemUi> {
        val opItems = ops.map { op ->
            TimelineItemUi(
                id = "op-${op.id}",
                packageName = op.packageName,
                title = op.packageName.substringAfterLast('.'),
                detail = op.opName.removePrefix("android:"),
                timestamp = maxOf(op.lastAccessTime, op.timestamp),
                badge = op.mode,
                kind = TimelineItemKind.APP_OPS
            )
        }
        val eventItems = events.map { event ->
            TimelineItemUi(
                id = "event-${event.id}",
                packageName = event.packageName.takeIf { it.isNotBlank() },
                title = event.title,
                detail = event.detail,
                timestamp = event.timestamp,
                badge = event.type.replace('_', ' '),
                kind = when (event.type) {
                    SecurityEventType.SENSITIVE_FILE_DETECTED,
                    SecurityEventType.DUPLICATE_FILES_DETECTED,
                    SecurityEventType.SECURE_DELETE,
                    SecurityEventType.FILE_ACCESS_AUDIT -> TimelineItemKind.FILES
                    SecurityEventType.DNS_LEAK -> TimelineItemKind.SECURITY
                    else -> TimelineItemKind.SECURITY
                }
            )
        }
        return (opItems + eventItems).sortedByDescending { it.timestamp }
    }

    private fun filterEntries(
        entries: List<TimelineItemUi>,
        filter: TimelineFilter
    ): List<TimelineItemUi> {
        return when (filter) {
            TimelineFilter.ALL -> entries
            TimelineFilter.LOCATION -> entries.filter {
                it.kind == TimelineItemKind.APP_OPS && it.detail.contains("LOCATION", ignoreCase = true)
            }
            TimelineFilter.CAMERA -> entries.filter {
                it.kind == TimelineItemKind.APP_OPS && it.detail.contains("CAMERA", ignoreCase = true)
            }
            TimelineFilter.MICROPHONE -> entries.filter {
                it.kind == TimelineItemKind.APP_OPS && (
                    it.detail.contains("AUDIO", ignoreCase = true) ||
                        it.detail.contains("RECORD", ignoreCase = true)
                    )
            }
            TimelineFilter.CONTACTS -> entries.filter {
                it.kind == TimelineItemKind.APP_OPS && it.detail.contains("CONTACT", ignoreCase = true)
            }
            TimelineFilter.STORAGE -> entries.filter {
                it.kind == TimelineItemKind.APP_OPS && (
                    it.detail.contains("STORAGE", ignoreCase = true) ||
                        it.detail.contains("EXTERNAL", ignoreCase = true)
                    )
            }
            TimelineFilter.SECURITY -> entries.filter { it.kind == TimelineItemKind.SECURITY }
            TimelineFilter.FILES -> entries.filter { it.kind == TimelineItemKind.FILES }
        }
    }
}

data class TimelineUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val entries: List<TimelineItemUi> = emptyList(),
    val filteredEntries: List<TimelineItemUi> = emptyList(),
    val selectedFilter: TimelineFilter = TimelineFilter.ALL,
    val selectedPeriod: UsageTimeRange = UsageTimeRange.LAST_24_HOURS,
    val customPeriodDays: Int? = null,
    val totalEntries: Int = 0,
    val uniqueApps: Int = 0,
    val topPackages: List<TimelinePackageSummary> = emptyList(),
    val lastActivityTimestamp: Long = 0L
)

data class TimelinePackageSummary(
    val packageLabel: String,
    val count: Int
)

data class TimelineItemUi(
    val id: String,
    val packageName: String?,
    val title: String,
    val detail: String,
    val timestamp: Long,
    val badge: String,
    val kind: TimelineItemKind
)

enum class TimelineItemKind {
    APP_OPS,
    SECURITY,
    FILES
}

enum class TimelineFilter(val label: String) {
    ALL("All"),
    LOCATION("Location"),
    CAMERA("Camera"),
    MICROPHONE("Microphone"),
    CONTACTS("Contacts"),
    STORAGE("Storage"),
    SECURITY("Security"),
    FILES("Files")
}
