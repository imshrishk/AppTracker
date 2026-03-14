package com.apptracker.ui.screens.networkmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptracker.data.db.dao.DnsQueryDao
import com.apptracker.data.db.dao.DomainCount
import com.apptracker.data.model.AppInfo
import com.apptracker.domain.usecase.GetInstalledAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppNetworkNode(
    val app: AppInfo,
    val topDomains: List<String>,
    val topCountries: List<CountryTraffic>,
    val attributionConfidence: AttributionConfidence,
    val attributionReason: String,
    val trackerDomainCount: Int,
    val totalDomains: Int,
    val txBytes: Long,
    val rxBytes: Long,
    val foregroundBytes: Long,
    val backgroundBytes: Long,
    val estimatedMonthlyMobileCostUsd: Double
)

data class CountryTraffic(
    val country: String,
    val queryCount: Int
)

enum class AttributionConfidence(val label: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low")
}

data class AppNetworkMapUiState(
    val isLoading: Boolean = true,
    val nodes: List<AppNetworkNode> = emptyList(),
    val topTrackerDomains: List<DomainCount> = emptyList(),
    val geoDestinations: List<CountryTraffic> = emptyList(),
    val unknownDestinationLookups: Int = 0,
    val highConfidenceCount: Int = 0,
    val mediumConfidenceCount: Int = 0,
    val lowConfidenceCount: Int = 0,
    val includeUnknownDestinations: Boolean = true,
    val showHighConfidenceOnly: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AppNetworkMapViewModel @Inject constructor(
    private val dnsQueryDao: DnsQueryDao,
    private val getInstalledApps: GetInstalledAppsUseCase
) : ViewModel() {

    private val mobileCostPerGbUsd = 0.12

    private val _uiState = MutableStateFlow(AppNetworkMapUiState())
    val uiState: StateFlow<AppNetworkMapUiState> = _uiState.asStateFlow()
    private var allNodes: List<AppNetworkNode> = emptyList()
    private var allGeoDestinations: List<CountryTraffic> = emptyList()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val topTrackerDomains = dnsQueryDao.topTrackerDomains(20)
                val apps = getInstalledApps(includeSystem = false)
                    .filter { it.networkUsage != null && (it.networkUsage!!.totalBytes) > 0 }
                    .sortedByDescending { it.networkUsage?.totalBytes ?: 0L }
                    .take(30)

                val trackerDomainSet = topTrackerDomains.map { it.domain }.toHashSet()

                val nodes = apps.map { app ->
                    val perAppDomains = dnsQueryDao.topDomainsForPackage(app.packageName, limit = 5)
                    val trackerCount = dnsQueryDao.trackerQueryCountForPackage(app.packageName)
                    val topCountries = perAppDomains
                        .groupBy { inferCountry(it.domain) }
                        .map { (country, entries) -> CountryTraffic(country, entries.sumOf { it.queryCount }) }
                        .sortedByDescending { it.queryCount }
                        .take(3)
                    val granted = app.permissions.filter { it.isGranted }.map { it.permissionName }
                    val domainHints = if (perAppDomains.isNotEmpty()) {
                        perAppDomains.map { it.domain }
                    } else {
                        buildList {
                            if (granted.any { it.contains("INTERNET", ignoreCase = true) }) {
                                add("network-enabled")
                            }
                        }
                    }
                    val confidence = when {
                        perAppDomains.size >= 3 -> AttributionConfidence.HIGH
                        perAppDomains.isNotEmpty() -> AttributionConfidence.MEDIUM
                        else -> AttributionConfidence.LOW
                    }
                    val attributionReason = when (confidence) {
                        AttributionConfidence.HIGH -> "Multiple DNS domains were attributed directly to this app UID."
                        AttributionConfidence.MEDIUM -> "Some DNS lookups were attributed; remaining data uses inferred mapping."
                        AttributionConfidence.LOW -> "No DNS attribution yet; showing inferred network profile from app signals."
                    }
                    AppNetworkNode(
                        app = app,
                        topDomains = domainHints,
                        topCountries = topCountries,
                        attributionConfidence = confidence,
                        attributionReason = attributionReason,
                        trackerDomainCount = trackerCount.takeIf { perAppDomains.isNotEmpty() } ?: trackerDomainSet.size,
                        totalDomains = domainHints.size,
                        txBytes = app.networkUsage?.totalTxBytes ?: 0L,
                        rxBytes = app.networkUsage?.totalRxBytes ?: 0L,
                        foregroundBytes = app.networkUsage?.foregroundBytes ?: 0L,
                        backgroundBytes = app.networkUsage?.backgroundBytes ?: 0L,
                        estimatedMonthlyMobileCostUsd = estimateMonthlyMobileCost(app.networkUsage?.totalMobileBytes ?: 0L)
                    )
                }

                allNodes = nodes
                val highCount = nodes.count { it.attributionConfidence == AttributionConfidence.HIGH }
                val mediumCount = nodes.count { it.attributionConfidence == AttributionConfidence.MEDIUM }
                val lowCount = nodes.count { it.attributionConfidence == AttributionConfidence.LOW }
                val visibleNodes = if (_uiState.value.showHighConfidenceOnly) {
                    nodes
                        .filter { it.attributionConfidence == AttributionConfidence.HIGH }
                        .sortedByDescending { it.app.riskScore }
                } else {
                    nodes
                }

                val geoDestinations = nodes
                    .flatMap { node -> node.topCountries }
                    .groupBy { it.country }
                    .map { (country, entries) -> CountryTraffic(country, entries.sumOf { it.queryCount }) }
                    .sortedByDescending { it.queryCount }
                    .take(8)
                allGeoDestinations = geoDestinations
                val unknownDestinationLookups = geoDestinations
                    .filter { isUnknownCountry(it.country) }
                    .sumOf { it.queryCount }
                val visibleGeoDestinations = applyGeoFilters(
                    geoDestinations,
                    includeUnknown = _uiState.value.includeUnknownDestinations
                )

                _uiState.value = AppNetworkMapUiState(
                    isLoading = false,
                    nodes = visibleNodes,
                    topTrackerDomains = topTrackerDomains,
                    geoDestinations = visibleGeoDestinations,
                    unknownDestinationLookups = unknownDestinationLookups,
                    highConfidenceCount = highCount,
                    mediumConfidenceCount = mediumCount,
                    lowConfidenceCount = lowCount,
                    includeUnknownDestinations = _uiState.value.includeUnknownDestinations,
                    showHighConfidenceOnly = _uiState.value.showHighConfidenceOnly
                )
            } catch (e: Exception) {
                _uiState.value = AppNetworkMapUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to build network map"
                )
            }
        }
    }

    fun setShowHighConfidenceOnly(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            showHighConfidenceOnly = enabled,
            nodes = if (enabled) {
                allNodes
                    .filter { it.attributionConfidence == AttributionConfidence.HIGH }
                    .sortedByDescending { it.app.riskScore }
            } else {
                allNodes
            }
        )
    }

    fun setIncludeUnknownDestinations(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            includeUnknownDestinations = enabled,
            geoDestinations = applyGeoFilters(allGeoDestinations, enabled)
        )
    }

    private fun estimateMonthlyMobileCost(mobileBytes: Long): Double {
        val gb = mobileBytes.toDouble() / 1_073_741_824.0
        return gb * mobileCostPerGbUsd
    }

    private fun inferCountry(domain: String): String {
        val normalized = domain.lowercase()
        val hostedCountryHints = mapOf(
            "amazonaws.com" to "United States",
            "googleapis.com" to "United States",
            "gstatic.com" to "United States",
            "cloudfront.net" to "United States",
            "azureedge.net" to "United States",
            "doubleclick.net" to "United States",
            "baidu.com" to "China",
            "yandex.ru" to "Russia"
        )
        hostedCountryHints.entries.firstOrNull { normalized.contains(it.key) }?.let { return it.value }

        return when {
            normalized.endsWith(".in") -> "India"
            normalized.endsWith(".uk") || normalized.endsWith(".co.uk") -> "United Kingdom"
            normalized.endsWith(".de") -> "Germany"
            normalized.endsWith(".fr") -> "France"
            normalized.endsWith(".nl") -> "Netherlands"
            normalized.endsWith(".jp") -> "Japan"
            normalized.endsWith(".sg") -> "Singapore"
            normalized.endsWith(".au") -> "Australia"
            normalized.endsWith(".br") -> "Brazil"
            normalized.endsWith(".ca") -> "Canada"
            normalized.endsWith(".ru") -> "Russia"
            normalized.endsWith(".cn") -> "China"
            normalized.endsWith(".io") || normalized.endsWith(".ai") || normalized.endsWith(".com") -> "Global (CDN/Unknown)"
            else -> "Unknown"
        }
    }

    private fun applyGeoFilters(
        destinations: List<CountryTraffic>,
        includeUnknown: Boolean
    ): List<CountryTraffic> {
        if (includeUnknown) return destinations
        return destinations.filterNot { isUnknownCountry(it.country) }
    }

    private fun isUnknownCountry(country: String): Boolean {
        val normalized = country.lowercase()
        return normalized.contains("unknown") || normalized.contains("global")
    }
}
