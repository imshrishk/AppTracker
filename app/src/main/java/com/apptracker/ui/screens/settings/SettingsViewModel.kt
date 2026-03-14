package com.apptracker.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptracker.data.db.dao.AppOpsDao
import com.apptracker.data.db.dao.ApkSnapshotDao
import com.apptracker.data.db.dao.BatteryHistoryDao
import com.apptracker.data.db.dao.CustomRuleDao
import com.apptracker.data.db.dao.DeviceHealthSnapshotDao
import com.apptracker.data.db.dao.NetworkHistoryDao
import com.apptracker.data.db.dao.DnsQueryDao
import com.apptracker.data.db.entity.CustomRuleComparator
import com.apptracker.data.db.entity.CustomRuleEntity
import com.apptracker.data.db.entity.CustomRuleMetric
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
    private val apkSnapshotDao: ApkSnapshotDao,
    private val batteryHistoryDao: BatteryHistoryDao,
    private val customRuleDao: CustomRuleDao,
    private val deviceHealthSnapshotDao: DeviceHealthSnapshotDao,
    private val networkHistoryDao: NetworkHistoryDao,
    private val dnsQueryDao: DnsQueryDao,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observePreferences()
        observeRules()
    }

    private fun observeRules() {
        viewModelScope.launch {
            customRuleDao.observeAll().collectLatest { rules ->
                _uiState.value = _uiState.value.copy(
                    customRules = rules.map { rule ->
                        CustomRuleUi(
                            id = rule.id,
                            name = rule.name,
                            metric = metricLabel(rule.metric),
                            comparator = rule.comparator,
                            threshold = rule.threshold,
                            severity = rule.severity,
                            enabled = rule.enabled
                        )
                    }
                )
            }
        }
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
        viewModelScope.launch {
            OnboardingPreferences.highRiskThreshold(context).collectLatest { threshold ->
                _uiState.value = _uiState.value.copy(highRiskThreshold = threshold)
            }
        }
        viewModelScope.launch {
            OnboardingPreferences.backgroundHeavyHours(context).collectLatest { hours ->
                _uiState.value = _uiState.value.copy(backgroundHeavyHours = hours)
            }
        }
        viewModelScope.launch {
            OnboardingPreferences.dnsLeakSensitivity(context).collectLatest { level ->
                _uiState.value = _uiState.value.copy(dnsLeakSensitivity = level)
            }
        }
        viewModelScope.launch {
            OnboardingPreferences.searchQueryPersistence(context).collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(searchQueryPersistence = enabled)
            }
        }
    }

    fun clearHistory(daysToKeep: Int) {
        viewModelScope.launch {
            val cutoff = System.currentTimeMillis() - (daysToKeep.toLong() * 24 * 60 * 60 * 1000)
            appOpsDao.deleteOlderThan(cutoff)
            apkSnapshotDao.deleteOlderThan(cutoff)
            batteryHistoryDao.deleteOlderThan(cutoff)
            deviceHealthSnapshotDao.deleteOlderThan(cutoff)
            networkHistoryDao.deleteOlderThan(cutoff)
            dnsQueryDao.deleteOlderThan(cutoff)
            _uiState.value = _uiState.value.copy(
                lastCleanup = "Cleared data older than $daysToKeep days"
            )
        }
    }

    fun createCustomRule(name: String, metric: String, comparator: String, threshold: Float, severity: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            customRuleDao.insert(
                CustomRuleEntity(
                    name = name.trim(),
                    metric = metric,
                    comparator = comparator,
                    threshold = threshold,
                    severity = severity,
                    enabled = true
                )
            )
        }
    }

    fun setRuleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            customRuleDao.setEnabled(id, enabled)
        }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch {
            val rule = _uiState.value.customRules.firstOrNull { it.id == ruleId } ?: return@launch
            customRuleDao.delete(
                CustomRuleEntity(
                    id = rule.id,
                    name = rule.name,
                    metric = toMetricKey(rule.metric),
                    comparator = rule.comparator,
                    threshold = rule.threshold,
                    severity = rule.severity,
                    enabled = rule.enabled
                )
            )
        }
    }

    private fun metricLabel(metric: String): String = when (metric) {
        CustomRuleMetric.RISK_SCORE -> "Risk Score"
        CustomRuleMetric.DANGEROUS_PERMISSIONS -> "Dangerous Permissions"
        CustomRuleMetric.BACKGROUND_HOURS -> "Background Hours"
        CustomRuleMetric.MOBILE_MB -> "Mobile Data MB"
        else -> metric
    }

    private fun toMetricKey(label: String): String = when (label) {
        "Risk Score" -> CustomRuleMetric.RISK_SCORE
        "Dangerous Permissions" -> CustomRuleMetric.DANGEROUS_PERMISSIONS
        "Background Hours" -> CustomRuleMetric.BACKGROUND_HOURS
        "Mobile Data MB" -> CustomRuleMetric.MOBILE_MB
        else -> label
    }

    companion object {
        val metricOptions = listOf(
            CustomRuleMetric.RISK_SCORE,
            CustomRuleMetric.DANGEROUS_PERMISSIONS,
            CustomRuleMetric.BACKGROUND_HOURS,
            CustomRuleMetric.MOBILE_MB
        )
        val comparatorOptions = listOf(CustomRuleComparator.GTE, CustomRuleComparator.GT)
        val severityOptions = listOf("Info", "Warning", "Danger", "Critical")
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

    fun setHighRiskThreshold(threshold: Int) {
        viewModelScope.launch {
            OnboardingPreferences.setHighRiskThreshold(context, threshold)
        }
    }

    fun setBackgroundHeavyHours(hours: Int) {
        viewModelScope.launch {
            OnboardingPreferences.setBackgroundHeavyHours(context, hours)
        }
    }

    fun setDnsLeakSensitivity(level: Int) {
        viewModelScope.launch {
            OnboardingPreferences.setDnsLeakSensitivity(context, level)
        }
    }

    fun setSearchQueryPersistence(enabled: Boolean) {
        viewModelScope.launch {
            OnboardingPreferences.setSearchQueryPersistence(context, enabled)
            if (!enabled) {
                OnboardingPreferences.setAppListSearchQuery(context, "")
                OnboardingPreferences.setGlobalSearchQuery(context, "")
            }
        }
    }

    fun resetSearchDefaults() {
        viewModelScope.launch {
            OnboardingPreferences.setAppListSearchQuery(context, "")
            OnboardingPreferences.setGlobalSearchQuery(context, "")
            OnboardingPreferences.setSearchQueryPersistence(context, true)
        }
    }

    fun exportCsv() {
        if (_uiState.value.onDeviceOnly) return
        viewModelScope.launch {
            val apps = getInstalledAppsUseCase(includeSystem = true)
            val csv = buildString {
                appendLine(
                    "appName,packageName,category,installSource,riskScore,dangerousPermissions,grantedPermissions,totalPermissions," +
                        "dangerousPermissionNames,grantedPermissionNames,foregroundMs,backgroundMs,batteryOptimized,dozeWhitelisted," +
                        "totalNetworkBytes,wifiBytes,mobileBytes,foregroundNetworkBytes,backgroundNetworkBytes,sendReceiveRatio"
                )
                apps.forEach { app ->
                    val dangerous = app.permissions.count { it.isDangerous && it.isGranted }
                    val dangerousPermissionNames = app.permissions
                        .filter { it.isDangerous && it.isGranted }
                        .joinToString(" | ") { it.permissionName }
                    val grantedPermissionNames = app.permissions
                        .filter { it.isGranted }
                        .joinToString(" | ") { it.permissionName }
                    val fg = app.batteryUsage?.foregroundTimeMs ?: 0L
                    val bg = app.batteryUsage?.backgroundTimeMs ?: 0L
                    val batteryOptimized = app.batteryUsage?.isBatteryOptimized ?: false
                    val dozeWhitelisted = app.batteryUsage?.isDozeWhitelisted ?: false
                    val network = app.networkUsage
                    val net = network?.totalBytes ?: 0L
                    val wifi = network?.totalWifiBytes ?: 0L
                    val mobile = network?.totalMobileBytes ?: 0L
                    val foregroundNetwork = network?.foregroundBytes ?: 0L
                    val backgroundNetwork = network?.backgroundBytes ?: 0L
                    val ratio = network?.sendReceiveRatio ?: 0.0
                    appendLine(
                        "\"${escapeCsv(app.appName)}\",${app.packageName},${app.category.label},${escapeCsv(app.installSourceLabel)},${app.riskScore}," +
                            "$dangerous,${app.permissions.count { it.isGranted }},${app.permissions.size}," +
                            "\"${escapeCsv(dangerousPermissionNames)}\",\"${escapeCsv(grantedPermissionNames)}\"," +
                            "$fg,$bg,$batteryOptimized,$dozeWhitelisted,$net,$wifi,$mobile,$foregroundNetwork,$backgroundNetwork,${"%.2f".format(ratio)}"
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
                    val grantedPermissions = app.permissions.filter { it.isGranted }.map { it.permissionName }
                    val dangerousPermissions = app.permissions.filter { it.isDangerous && it.isGranted }.map { it.permissionName }
                    val fg = app.batteryUsage?.foregroundTimeMs ?: 0L
                    val bg = app.batteryUsage?.backgroundTimeMs ?: 0L
                    val network = app.networkUsage
                    val net = network?.totalBytes ?: 0L
                    append(
                        "  {\"appName\":\"${escapeJson(app.appName)}\",\"packageName\":\"${escapeJson(app.packageName)}\"," +
                            "\"category\":\"${escapeJson(app.category.label)}\",\"installSource\":\"${escapeJson(app.installSourceLabel)}\"," +
                            "\"riskScore\":${app.riskScore},\"dangerousPermissions\":$dangerous,\"grantedPermissions\":${grantedPermissions.size}," +
                            "\"totalPermissions\":${app.permissions.size},\"dangerousPermissionNames\":${toJsonArray(dangerousPermissions)}," +
                            "\"grantedPermissionNames\":${toJsonArray(grantedPermissions)},\"battery\":{\"foregroundMs\":$fg," +
                            "\"backgroundMs\":$bg,\"batteryOptimized\":${app.batteryUsage?.isBatteryOptimized ?: false}," +
                            "\"dozeWhitelisted\":${app.batteryUsage?.isDozeWhitelisted ?: false}},\"network\":{\"totalBytes\":$net," +
                            "\"wifiBytes\":${network?.totalWifiBytes ?: 0L},\"mobileBytes\":${network?.totalMobileBytes ?: 0L}," +
                            "\"foregroundBytes\":${network?.foregroundBytes ?: 0L},\"backgroundBytes\":${network?.backgroundBytes ?: 0L}," +
                            "\"sendReceiveRatio\":${"%.2f".format(network?.sendReceiveRatio ?: 0.0)}}}"
                    )
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

    private fun toJsonArray(values: List<String>): String {
        return values.joinToString(prefix = "[", postfix = "]") { "\"${escapeJson(it)}\"" }
    }
}

data class SettingsUiState(
    val refreshIntervalHours: Int = 6,
    val lastCleanup: String? = null,
    val showSystemApps: Boolean = false,
    val beginnerMode: Boolean = true,
    val onDeviceOnly: Boolean = true,
    val defaultUsageRange: UsageTimeRange = UsageTimeRange.LAST_24_HOURS,
    val highRiskThreshold: Int = 45,
    val backgroundHeavyHours: Int = 1,
    val dnsLeakSensitivity: Int = 2,
    val searchQueryPersistence: Boolean = true,
    val customRules: List<CustomRuleUi> = emptyList(),
    val exportContent: String? = null,
    val exportMimeType: String? = null,
    val exportFileName: String? = null
)

data class CustomRuleUi(
    val id: Long,
    val name: String,
    val metric: String,
    val comparator: String,
    val threshold: Float,
    val severity: String,
    val enabled: Boolean
)
