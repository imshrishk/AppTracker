package com.apptracker.ui.screens.dnsactivity

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptracker.data.db.dao.DnsQueryDao
import com.apptracker.data.db.dao.DomainCount
import com.apptracker.data.db.dao.ResolverCount
import com.apptracker.data.db.entity.DnsQueryEntity
import com.apptracker.util.DnsResolverCatalog
import com.apptracker.util.OnboardingPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DnsLogFilter {
    ALL,
    TRACKERS,
    UNATTRIBUTED,
    NON_MONITORED
}

data class DnsActivityUiState(
    val recentQueries: List<DnsQueryEntity> = emptyList(),
    val trackerQueries: List<DnsQueryEntity> = emptyList(),
    val unattributedQueries: List<DnsQueryEntity> = emptyList(),
    val nonMonitoredQueries: List<DnsQueryEntity> = emptyList(),
    val topTrackerDomains: List<DomainCount> = emptyList(),
    val topResolvers24h: List<ResolverCount> = emptyList(),
    val totalQueries24h: Int = 0,
    val trackerHits24h: Int = 0,
    val unattributedQueries24h: Int = 0,
    val distinctResolvers24h: Int = 0,
    val nonMonitoredResolverQueries24h: Int = 0,
    val dnsLeakSensitivity: Int = 2,
    val selectedLogFilter: DnsLogFilter = DnsLogFilter.ALL,
    val isMonitorRunning: Boolean = false
)

@HiltViewModel
class DnsActivityViewModel @Inject constructor(
    private val dnsQueryDao: DnsQueryDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DnsActivityUiState())
    val uiState: StateFlow<DnsActivityUiState> = _uiState.asStateFlow()

    init {
        observeQueries()
        observePreferences()
        loadStats()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            OnboardingPreferences.dnsLeakSensitivity(context).collectLatest { level ->
                _uiState.value = _uiState.value.copy(dnsLeakSensitivity = level)
            }
        }
    }

    private fun observeQueries() {
        viewModelScope.launch {
            dnsQueryDao.getRecentQueries(300).collectLatest { queries ->
                val nonMonitored = queries.filter {
                    it.resolverIp.isNotBlank() && !DnsResolverCatalog.MONITORED_RESOLVERS.contains(it.resolverIp)
                }
                _uiState.value = _uiState.value.copy(
                    recentQueries = queries,
                    nonMonitoredQueries = nonMonitored
                )
            }
        }
        viewModelScope.launch {
            dnsQueryDao.getTrackerQueries(200).collectLatest { queries ->
                _uiState.value = _uiState.value.copy(trackerQueries = queries)
            }
        }
        viewModelScope.launch {
            dnsQueryDao.getRecentUnattributedQueries(100).collectLatest { queries ->
                _uiState.value = _uiState.value.copy(unattributedQueries = queries)
            }
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            val since = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            val total       = dnsQueryDao.countTotalQueriesSince(since)
            val trackerHits = dnsQueryDao.countTrackerHitsSince(since)
            val unattributed = dnsQueryDao.countUnattributedSince(since)
            val distinctResolvers = dnsQueryDao.countDistinctResolversSince(since)
            val nonMonitoredResolverQueries = dnsQueryDao.countNonMonitoredResolverQueriesSince(
                since,
                DnsResolverCatalog.MONITORED_RESOLVERS
            )
            val topDomains  = dnsQueryDao.topTrackerDomains(10)
            val topResolvers = dnsQueryDao.topResolversSince(since, 5)
            _uiState.value = _uiState.value.copy(
                totalQueries24h   = total,
                trackerHits24h    = trackerHits,
                unattributedQueries24h = unattributed,
                distinctResolvers24h = distinctResolvers,
                nonMonitoredResolverQueries24h = nonMonitoredResolverQueries,
                topTrackerDomains = topDomains,
                topResolvers24h = topResolvers
            )
        }
    }

    fun setLogFilter(filter: DnsLogFilter) {
        _uiState.value = _uiState.value.copy(selectedLogFilter = filter)
    }

    fun setMonitorRunning(running: Boolean) {
        _uiState.value = _uiState.value.copy(isMonitorRunning = running)
    }
}
