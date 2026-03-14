package com.apptracker.ui.screens.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptracker.data.db.dao.DeviceHealthSnapshotDao
import com.apptracker.data.db.dao.DnsQueryDao
import com.apptracker.data.db.dao.SecurityEventDao
import com.apptracker.data.db.dao.WatchedAppDao
import com.apptracker.data.db.entity.DeviceHealthSnapshotEntity
import com.apptracker.data.db.entity.SecurityEventType
import com.apptracker.data.model.AppInfo
import com.apptracker.data.model.UsageTimeRange
import com.apptracker.domain.usecase.GetInstalledAppsUseCase
import com.apptracker.util.BaselineSummary
import com.apptracker.util.OnboardingPreferences
import com.apptracker.util.PrivacyScoreUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

data class HealthImpactEntry(
    val app: AppInfo,
    val impactPoints: Int
)

data class PeerBenchmarkUi(
    val archetypeName: String,
    val archetypeDescription: String,
    val archetypeHealthScore: Int,
    val comparisonLabel: String,
    val matchedSignals: List<String>
)

private data class BenchmarkProfile(
    val archetypeName: String,
    val archetypeDescription: String,
    val healthScore: Int,
    val averageRiskScore: Int,
    val dangerousPermissionsPerApp: Float,
    val highBackgroundRatio: Float,
    val sideloadedCount: Int,
    val dnsTrackerHitsWeekly: Int
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getInstalledApps: GetInstalledAppsUseCase,
    private val watchedAppDao: WatchedAppDao,
    private val securityEventDao: SecurityEventDao,
    private val dnsQueryDao: DnsQueryDao,
    private val deviceHealthSnapshotDao: DeviceHealthSnapshotDao,
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

        viewModelScope.launch {
            OnboardingPreferences.highRiskThreshold(context).collectLatest { threshold ->
                val previous = _uiState.value.highRiskThreshold
                _uiState.value = _uiState.value.copy(highRiskThreshold = threshold)
                if (previous != threshold) {
                    loadData(_uiState.value.selectedRange)
                }
            }
        }

        viewModelScope.launch {
            OnboardingPreferences.backgroundHeavyHours(context).collectLatest { hours ->
                val previous = _uiState.value.backgroundHeavyHours
                _uiState.value = _uiState.value.copy(backgroundHeavyHours = hours)
                if (previous != hours) {
                    loadData(_uiState.value.selectedRange)
                }
            }
        }
    }

    fun loadData(range: UsageTimeRange = _uiState.value.selectedRange) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val now = System.currentTimeMillis()
                val customStartTime = _uiState.value.customRangeDays?.let { days ->
                    now - days * 24L * 60L * 60L * 1000L
                }
                val apps = getInstalledApps(
                    includeSystem = false,
                    usageTimeRange = range,
                    customStartTimeMillis = customStartTime
                )

                val totalApps = apps.size
                val highRiskApps = apps.filter { it.riskScore >= _uiState.value.highRiskThreshold }
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
                } else {
                    0
                }

                val highBackgroundThresholdMs = _uiState.value.backgroundHeavyHours * 60L * 60L * 1000L
                val highBackgroundApps = apps.filter {
                    (it.batteryUsage?.backgroundTimeMs ?: 0L) > highBackgroundThresholdMs
                }

                val sevenDaysAgo = now - 7L * 24L * 60L * 60L * 1000L
                val newlyInstalledApps = apps
                    .filter { it.installTime >= sevenDaysAgo }
                    .sortedByDescending { it.installTime }
                    .take(5)

                val permissionDeltaEvents = securityEventDao.countByTypeSince(SecurityEventType.PERMISSION_DELTA, sevenDaysAgo)
                val nightEvents = securityEventDao.countByTypeSince(SecurityEventType.NIGHT_ACTIVITY, sevenDaysAgo)
                val sensitiveOpsEvents = securityEventDao.countByTypeSince(SecurityEventType.SENSITIVE_APPOPS, sevenDaysAgo)
                val watchlistChangeEvents = securityEventDao.countByTypeSince(SecurityEventType.WATCHLIST_CHANGE, sevenDaysAgo)
                val permissionSpikeEvents = securityEventDao.countByTypeSince(SecurityEventType.PERMISSION_SPIKE, sevenDaysAgo)
                val burstNetworkEvents = securityEventDao.countByTypeSince(SecurityEventType.BURST_NETWORK, sevenDaysAgo)
                val heuristicSecurityEvents = listOf(
                    SecurityEventType.FAKE_GPS,
                    SecurityEventType.ACCESSIBILITY_WATCHDOG,
                    SecurityEventType.SCREEN_RECORDING,
                    SecurityEventType.KEYLOGGER_RISK,
                    SecurityEventType.APP_IMPERSONATION,
                    SecurityEventType.CROSS_APP_COLLUSION,
                    SecurityEventType.HIDDEN_PROCESS,
                    SecurityEventType.CERTIFICATE_PINNING,
                    SecurityEventType.DNS_LEAK
                ).sumOf { type -> securityEventDao.countByTypeSince(type, sevenDaysAgo) }
                val appInstallEvents = securityEventDao.countByTypeSince(SecurityEventType.APP_INSTALL, sevenDaysAgo)
                val dormantEvents = securityEventDao.countByTypeSince(SecurityEventType.DORMANT_APP, sevenDaysAgo)
                val autoRevokeEvents = securityEventDao.countByTypeSince(SecurityEventType.AUTO_REVOKE, sevenDaysAgo)
                val darkPatternEvents = securityEventDao.countByTypeSince(SecurityEventType.DARK_PATTERN, sevenDaysAgo)
                val dnsLeakEvents = securityEventDao.countByTypeSince(SecurityEventType.DNS_LEAK, sevenDaysAgo)
                val dnsTrackerHits = dnsQueryDao.countTrackerHitsSince(sevenDaysAgo)

                val dormantApps = apps.filter { app ->
                    !app.isSystemApp &&
                        app.installTime < now - 60L * 24L * 60L * 60L * 1000L &&
                        app.batteryUsage == null &&
                        app.permissions.any { it.isDangerous && it.isGranted }
                }.sortedByDescending { app ->
                    app.permissions.count { it.isDangerous && it.isGranted }
                }.take(5)

                val topDataHoarders = apps
                    .filter { !it.isSystemApp }
                    .map { app -> app to PrivacyScoreUtils.dataHoardingScore(app.permissions) }
                    .filter { (_, score) -> score >= 40 }
                    .sortedByDescending { (_, score) -> score }
                    .take(5)
                    .map { (app, _) -> app }

                val sideloadedApps = apps
                    .filter { it.isSideloaded }
                    .sortedByDescending { it.lastUpdateTime }
                    .take(5)

                val sensitivePermissionApps = apps
                    .filter { app ->
                        app.permissions.any {
                            it.isGranted && (
                                it.permissionName.contains("CAMERA", ignoreCase = true) ||
                                    it.permissionName.contains("RECORD_AUDIO", ignoreCase = true) ||
                                    it.permissionName.contains("LOCATION", ignoreCase = true)
                                )
                        }
                    }
                    .sortedByDescending { app ->
                        app.permissions.count {
                            it.isGranted && (
                                it.permissionName.contains("CAMERA", ignoreCase = true) ||
                                    it.permissionName.contains("RECORD_AUDIO", ignoreCase = true) ||
                                    it.permissionName.contains("LOCATION", ignoreCase = true)
                                )
                        }
                    }
                    .take(5)

                val healthScore = calculateDeviceHealthScore(
                    appCount = apps.size,
                    averageRiskScore = avgRiskScore,
                    highRiskCount = highRiskApps.size,
                    dangerousPermissionCount = dangerousPermissionCount,
                    highBackgroundAppCount = highBackgroundApps.size
                )

                val healthImpactEntries = apps
                    .map { app ->
                        val dangerousGranted = app.permissions.count { it.isDangerous && it.isGranted }
                        val backgroundHeavy = (app.batteryUsage?.backgroundTimeMs ?: 0L) > highBackgroundThresholdMs
                        val impact =
                            (app.riskScore * 0.6f).toInt() +
                                (dangerousGranted * 2) +
                                if (backgroundHeavy) 8 else 0
                        HealthImpactEntry(app = app, impactPoints = impact)
                    }
                    .sortedByDescending { it.impactPoints }
                    .take(5)

                val recentHealthSnapshots = deviceHealthSnapshotDao.getRecent(8)
                val sixMonthsAgo = now - 180L * 24L * 60L * 60L * 1000L
                val sixMonthSnapshots = deviceHealthSnapshotDao.getSnapshotsSince(sixMonthsAgo)
                val baselineSummary = OnboardingPreferences.baselineSummary(context).first()
                val healthTrend = buildHealthTrend(recentHealthSnapshots, healthScore)
                val healthTrendSixMonths = buildSixMonthHealthTrend(sixMonthSnapshots, healthScore, now)
                val recentAverage = if (recentHealthSnapshots.isNotEmpty()) {
                    recentHealthSnapshots.map { it.healthScore }.average().roundToInt()
                } else {
                    healthScore
                }
                val healthBenchmarkLabel = when {
                    healthScore >= recentAverage + 5 -> "Better than recent baseline"
                    healthScore <= recentAverage - 5 -> "Below recent baseline"
                    else -> "Near recent baseline"
                }
                val healthDeltaVsBaseline = baselineSummary?.let { healthScore - it.healthScore }
                val riskRadarValues = buildRiskRadarValues(
                    apps = apps,
                    dangerousPermissionCount = dangerousPermissionCount,
                    highBackgroundApps = highBackgroundApps,
                    topDataHoarders = topDataHoarders
                )
                val healthChecklist = buildChecklist(
                    highRiskApps = highRiskApps,
                    highBackgroundApps = highBackgroundApps,
                    sideloadedApps = sideloadedApps,
                    dnsTrackerHits = dnsTrackerHits,
                    topDataHoarders = topDataHoarders,
                    darkPatternEvents = darkPatternEvents,
                    permissionSpikeEvents = permissionSpikeEvents,
                    baselineCaptured = baselineSummary != null
                )
                val peerBenchmark = buildPeerBenchmark(
                    healthScore = healthScore,
                    averageRiskScore = avgRiskScore,
                    dangerousPermissionCount = dangerousPermissionCount,
                    appCount = totalApps,
                    highBackgroundAppCount = highBackgroundApps.size,
                    sideloadedCount = sideloadedApps.size,
                    dnsTrackerHits = dnsTrackerHits
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
                    highRiskThreshold = _uiState.value.highRiskThreshold,
                    backgroundHeavyHours = _uiState.value.backgroundHeavyHours,
                    highBackgroundApps = highBackgroundApps.sortedByDescending {
                        it.batteryUsage?.backgroundTimeMs ?: 0L
                    }.take(5),
                    newlyInstalledApps = newlyInstalledApps,
                    sensitivePermissionApps = sensitivePermissionApps,
                    appsUpdated7d = apps.count { it.lastUpdateTime >= sevenDaysAgo },
                    permissionDeltaEvents7d = permissionDeltaEvents,
                    nightActivityEvents7d = nightEvents,
                    sensitiveAppOpsEvents7d = sensitiveOpsEvents,
                    autoRevokeEvents7d = autoRevokeEvents,
                    darkPatternEvents7d = darkPatternEvents,
                    watchlistChangeEvents7d = watchlistChangeEvents,
                    permissionSpikeEvents7d = permissionSpikeEvents,
                    burstNetworkEvents7d = burstNetworkEvents,
                    heuristicSecurityEvents7d = heuristicSecurityEvents,
                    appInstallEvents7d = appInstallEvents,
                    dormantEventCount7d = dormantEvents,
                    dnsLeakEvents7d = dnsLeakEvents,
                    sideloadedApps = sideloadedApps,
                    dormantApps = dormantApps,
                    topDataHoarders = topDataHoarders,
                    healthImpactEntries = healthImpactEntries,
                    recentlyUpdatedApps = apps.sortedByDescending { it.lastUpdateTime }.take(5),
                    dnsTrackerHits7d = dnsTrackerHits,
                    healthTrend = healthTrend,
                    healthTrendSixMonths = healthTrendSixMonths,
                    baselineSummary = baselineSummary,
                    healthBenchmarkLabel = healthBenchmarkLabel,
                    recentAverageHealthScore = recentAverage,
                    healthDeltaVsBaseline = healthDeltaVsBaseline,
                    riskRadarValues = riskRadarValues,
                    peerBenchmark = peerBenchmark,
                    checklistConfiguredCount = healthChecklist.count { it.configured },
                    checklistTotalCount = healthChecklist.size,
                    healthChecklist = healthChecklist
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load data"
                )
            }
        }
    }

    private fun buildHealthTrend(
        snapshots: List<DeviceHealthSnapshotEntity>,
        currentHealthScore: Int
    ): List<Pair<String, Float>> {
        val formatter = SimpleDateFormat("MM/dd", Locale.getDefault())
        if (snapshots.isEmpty()) {
            return listOf("Now" to currentHealthScore.toFloat())
        }
        return snapshots
            .asReversed()
            .takeLast(7)
            .map { snapshot ->
                formatter.format(Date(snapshot.timestamp)) to snapshot.healthScore.toFloat()
            }
    }

    private fun buildSixMonthHealthTrend(
        snapshots: List<DeviceHealthSnapshotEntity>,
        currentHealthScore: Int,
        now: Long
    ): List<Pair<String, Float>> {
        val formatter = SimpleDateFormat("MMM", Locale.getDefault())
        if (snapshots.isEmpty()) {
            return listOf(formatter.format(Date(now)) to currentHealthScore.toFloat())
        }

        val latestPerMonth = snapshots
            .groupBy { formatter.format(Date(it.timestamp)) }
            .mapValues { (_, entries) -> entries.maxByOrNull { it.timestamp }!! }

        val ordered = latestPerMonth
            .values
            .sortedBy { it.timestamp }
            .takeLast(6)
            .map { entry -> formatter.format(Date(entry.timestamp)) to entry.healthScore.toFloat() }

        val currentMonthLabel = formatter.format(Date(now))
        return if (ordered.lastOrNull()?.first == currentMonthLabel) {
            ordered
        } else {
            (ordered + (currentMonthLabel to currentHealthScore.toFloat())).takeLast(6)
        }
    }

    private fun buildRiskRadarValues(
        apps: List<AppInfo>,
        dangerousPermissionCount: Int,
        highBackgroundApps: List<AppInfo>,
        topDataHoarders: List<AppInfo>
    ): List<Pair<String, Float>> {
        if (apps.isEmpty()) {
            return listOf(
                "Permission" to 0f,
                "Behavior" to 0f,
                "Network" to 0f,
                "Battery" to 0f,
                "Data" to 0f
            )
        }
        val permissionExposure = (dangerousPermissionCount * 100f / (apps.size * 3).coerceAtLeast(1)).coerceIn(0f, 100f)
        val behaviorExposure = (apps.count { it.riskScore >= 60 } * 100f / apps.size).coerceIn(0f, 100f)
        val networkExposure = (apps.count { (it.networkUsage?.backgroundBytes ?: 0L) > 5L * 1024 * 1024 } * 100f / apps.size).coerceIn(0f, 100f)
        val batteryExposure = (highBackgroundApps.size * 100f / apps.size).coerceIn(0f, 100f)
        val dataExposure = if (topDataHoarders.isEmpty()) 0f else {
            topDataHoarders
                .map { PrivacyScoreUtils.dataHoardingScore(it.permissions) }
                .average()
                .toFloat()
                .coerceIn(0f, 100f)
        }
        return listOf(
            "Permission" to permissionExposure,
            "Behavior" to behaviorExposure,
            "Network" to networkExposure,
            "Battery" to batteryExposure,
            "Data" to dataExposure
        )
    }

    private fun buildChecklist(
        highRiskApps: List<AppInfo>,
        highBackgroundApps: List<AppInfo>,
        sideloadedApps: List<AppInfo>,
        dnsTrackerHits: Int,
        topDataHoarders: List<AppInfo>,
        darkPatternEvents: Int,
        permissionSpikeEvents: Int,
        baselineCaptured: Boolean
    ): List<ChecklistItem> {
        return listOf(
            ChecklistItem(
                label = if (highRiskApps.isEmpty()) "No high-risk apps above threshold" else "Review ${highRiskApps.size} high-risk apps",
                configured = highRiskApps.isEmpty()
            ),
            ChecklistItem(
                label = if (highBackgroundApps.isEmpty()) "No heavy background-usage apps" else "Restrict background activity for ${highBackgroundApps.size} apps",
                configured = highBackgroundApps.isEmpty()
            ),
            ChecklistItem(
                label = if (sideloadedApps.isEmpty()) "No sideloaded / unknown-source apps detected" else "Audit ${sideloadedApps.size} sideloaded apps",
                configured = sideloadedApps.isEmpty()
            ),
            ChecklistItem(
                label = if (dnsTrackerHits == 0) "No DNS tracker hits in the last 7 days" else "Inspect $dnsTrackerHits DNS tracker hits",
                configured = dnsTrackerHits == 0
            ),
            ChecklistItem(
                label = if (topDataHoarders.isEmpty()) "No high data-hoarding apps detected" else "Re-check top data-collector apps",
                configured = topDataHoarders.isEmpty()
            ),
            ChecklistItem(
                label = if (baselineCaptured) "Baseline captured for trend benchmarking" else "Capture initial baseline snapshot",
                configured = baselineCaptured
            ),
            ChecklistItem(
                label = if (darkPatternEvents == 0) "No dark-pattern permission re-requests this week" else "Investigate $darkPatternEvents dark-pattern re-request events",
                configured = darkPatternEvents == 0
            ),
            ChecklistItem(
                label = if (permissionSpikeEvents == 0) "No permission grant spikes this week" else "Review $permissionSpikeEvents permission spike events",
                configured = permissionSpikeEvents == 0
            )
        )
    }

    private fun buildPeerBenchmark(
        healthScore: Int,
        averageRiskScore: Int,
        dangerousPermissionCount: Int,
        appCount: Int,
        highBackgroundAppCount: Int,
        sideloadedCount: Int,
        dnsTrackerHits: Int
    ): PeerBenchmarkUi {
        val dangerousPermissionsPerApp = if (appCount > 0) {
            dangerousPermissionCount.toFloat() / appCount
        } else {
            0f
        }
        val highBackgroundRatio = if (appCount > 0) {
            highBackgroundAppCount.toFloat() / appCount
        } else {
            0f
        }

        val profiles = listOf(
            BenchmarkProfile(
                archetypeName = "Low-risk device",
                archetypeDescription = "Mostly updated apps, low tracker activity, and few dangerous permission grants.",
                healthScore = 88,
                averageRiskScore = 28,
                dangerousPermissionsPerApp = 0.8f,
                highBackgroundRatio = 0.08f,
                sideloadedCount = 0,
                dnsTrackerHitsWeekly = 2
            ),
            BenchmarkProfile(
                archetypeName = "Medium-risk device",
                archetypeDescription = "Mixed app hygiene with some tracker exposure, moderate dangerous permissions, and a few noisy apps.",
                healthScore = 66,
                averageRiskScore = 48,
                dangerousPermissionsPerApp = 1.8f,
                highBackgroundRatio = 0.2f,
                sideloadedCount = 1,
                dnsTrackerHitsWeekly = 10
            ),
            BenchmarkProfile(
                archetypeName = "High-risk device",
                archetypeDescription = "Several sideloaded or stale apps, heavy tracker traffic, and elevated permission or background-activity exposure.",
                healthScore = 39,
                averageRiskScore = 74,
                dangerousPermissionsPerApp = 3.1f,
                highBackgroundRatio = 0.35f,
                sideloadedCount = 3,
                dnsTrackerHitsWeekly = 24
            )
        )

        val matchedProfile = profiles.minByOrNull { profile ->
            distanceToProfile(
                averageRiskScore = averageRiskScore,
                dangerousPermissionsPerApp = dangerousPermissionsPerApp,
                highBackgroundRatio = highBackgroundRatio,
                sideloadedCount = sideloadedCount,
                dnsTrackerHits = dnsTrackerHits,
                profile = profile
            )
        } ?: profiles[1]

        val scoreDelta = healthScore - matchedProfile.healthScore
        val comparisonLabel = when {
            scoreDelta >= 12 -> "$scoreDelta points safer than the typical ${matchedProfile.archetypeName.lowercase()}."
            scoreDelta >= 4 -> "$scoreDelta points better than the typical ${matchedProfile.archetypeName.lowercase()}."
            scoreDelta <= -12 -> "${kotlin.math.abs(scoreDelta)} points riskier than the typical ${matchedProfile.archetypeName.lowercase()}."
            scoreDelta <= -4 -> "${kotlin.math.abs(scoreDelta)} points below the typical ${matchedProfile.archetypeName.lowercase()}."
            else -> "Very close to the typical ${matchedProfile.archetypeName.lowercase()}."
        }

        val matchedSignals = buildList {
            add("Avg risk $averageRiskScore vs archetype ${matchedProfile.averageRiskScore}")
            add(
                "Dangerous permissions/app ${"%.1f".format(dangerousPermissionsPerApp)} vs ${"%.1f".format(matchedProfile.dangerousPermissionsPerApp)}"
            )
            add("DNS tracker hits $dnsTrackerHits vs ${matchedProfile.dnsTrackerHitsWeekly}")
            if (sideloadedCount > 0 || matchedProfile.sideloadedCount > 0) {
                add("Sideloaded apps $sideloadedCount vs ${matchedProfile.sideloadedCount}")
            }
        }.take(3)

        return PeerBenchmarkUi(
            archetypeName = matchedProfile.archetypeName,
            archetypeDescription = matchedProfile.archetypeDescription,
            archetypeHealthScore = matchedProfile.healthScore,
            comparisonLabel = comparisonLabel,
            matchedSignals = matchedSignals
        )
    }

    private fun distanceToProfile(
        averageRiskScore: Int,
        dangerousPermissionsPerApp: Float,
        highBackgroundRatio: Float,
        sideloadedCount: Int,
        dnsTrackerHits: Int,
        profile: BenchmarkProfile
    ): Float {
        val riskDistance = kotlin.math.abs(averageRiskScore - profile.averageRiskScore) * 1.2f
        val permissionDistance = kotlin.math.abs(dangerousPermissionsPerApp - profile.dangerousPermissionsPerApp) * 14f
        val backgroundDistance = kotlin.math.abs(highBackgroundRatio - profile.highBackgroundRatio) * 80f
        val sideloadDistance = kotlin.math.abs(sideloadedCount - profile.sideloadedCount) * 6f
        val dnsDistance = kotlin.math.abs(dnsTrackerHits - profile.dnsTrackerHitsWeekly) * 0.8f
        return riskDistance + permissionDistance + backgroundDistance + sideloadDistance + dnsDistance
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
    val highRiskThreshold: Int = 45,
    val backgroundHeavyHours: Int = 1,
    val highBackgroundApps: List<AppInfo> = emptyList(),
    val newlyInstalledApps: List<AppInfo> = emptyList(),
    val sensitivePermissionApps: List<AppInfo> = emptyList(),
    val appsUpdated7d: Int = 0,
    val permissionDeltaEvents7d: Int = 0,
    val nightActivityEvents7d: Int = 0,
    val sensitiveAppOpsEvents7d: Int = 0,
    val autoRevokeEvents7d: Int = 0,
    val darkPatternEvents7d: Int = 0,
    val watchlistChangeEvents7d: Int = 0,
    val permissionSpikeEvents7d: Int = 0,
    val burstNetworkEvents7d: Int = 0,
    val heuristicSecurityEvents7d: Int = 0,
    val appInstallEvents7d: Int = 0,
    val dormantEventCount7d: Int = 0,
    val dnsLeakEvents7d: Int = 0,
    val sideloadedApps: List<AppInfo> = emptyList(),
    val dormantApps: List<AppInfo> = emptyList(),
    val topDataHoarders: List<AppInfo> = emptyList(),
    val healthImpactEntries: List<HealthImpactEntry> = emptyList(),
    val recentlyUpdatedApps: List<AppInfo> = emptyList(),
    val dnsTrackerHits7d: Int = 0,
    val healthTrend: List<Pair<String, Float>> = emptyList(),
    val healthTrendSixMonths: List<Pair<String, Float>> = emptyList(),
    val baselineSummary: BaselineSummary? = null,
    val healthBenchmarkLabel: String = "Near recent baseline",
    val recentAverageHealthScore: Int = 100,
    val healthDeltaVsBaseline: Int? = null,
    val riskRadarValues: List<Pair<String, Float>> = emptyList(),
    val peerBenchmark: PeerBenchmarkUi? = null,
    val checklistConfiguredCount: Int = 0,
    val checklistTotalCount: Int = 0,
    val healthChecklist: List<ChecklistItem> = emptyList()
)

data class ChecklistItem(
    val label: String,
    val configured: Boolean
)
