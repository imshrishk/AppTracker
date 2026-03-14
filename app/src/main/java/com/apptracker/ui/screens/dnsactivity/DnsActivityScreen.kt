package com.apptracker.ui.screens.dnsactivity

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apptracker.data.db.entity.DnsQueryEntity
import com.apptracker.service.DnsMonitorVpnService
import com.apptracker.util.DnsResolverCatalog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DnsActivityScreen(
    onBack: () -> Unit,
    viewModel: DnsActivityViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedFocusKey by remember(state.selectedLogFilter) { mutableStateOf<String?>(null) }
    var previousFocusKey by remember { mutableStateOf<String?>(null) }
    val baseDisplayed = when (state.selectedLogFilter) {
        DnsLogFilter.ALL -> state.recentQueries
        DnsLogFilter.TRACKERS -> state.trackerQueries
        DnsLogFilter.UNATTRIBUTED -> state.unattributedQueries
        DnsLogFilter.NON_MONITORED -> state.nonMonitoredQueries
    }
    val resolverFocuses = remember(baseDisplayed) {
        baseDisplayed
            .asSequence()
            .map { it.resolverIp }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }
            .toList()
    }
    val packageFocuses = remember(baseDisplayed) {
        baseDisplayed
            .asSequence()
            .map { it.appPackageName }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }
            .toList()
    }
    val trackerDomainFocuses = remember(baseDisplayed) {
        baseDisplayed
            .asSequence()
            .filter { it.isTracker }
            .map { it.domain }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }
            .toList()
    }
    val showUnattributedFocus = state.selectedLogFilter != DnsLogFilter.UNATTRIBUTED &&
        baseDisplayed.any { it.appPackageName.isBlank() }
    val validFocusKeys = buildList {
        if (showUnattributedFocus) add("unattributed")
        addAll(resolverFocuses.map { "resolver:$it" })
        addAll(packageFocuses.map { "package:$it" })
        addAll(trackerDomainFocuses.map { "domain:$it" })
        addAll(state.topTrackerDomains.map { "domain:${it.domain}" })
    }
    LaunchedEffect(validFocusKeys, selectedFocusKey) {
        if (selectedFocusKey != null && selectedFocusKey !in validFocusKeys) {
            selectedFocusKey = null
        }
        if (previousFocusKey != null && previousFocusKey !in validFocusKeys) {
            previousFocusKey = null
        }
    }
    val canRestorePreviousFocus = previousFocusKey != null && previousFocusKey != selectedFocusKey
    val activeFocusLabel = remember(selectedFocusKey) {
        selectedFocusKey?.let { formatFocusLabel(it) }
    }
    val previousFocusLabel = remember(previousFocusKey) {
        previousFocusKey?.let { formatFocusLabel(it) }
    }

    fun setFocus(newFocusKey: String?, requiresTrackerLog: Boolean = false) {
        if (newFocusKey == selectedFocusKey) return
        if (selectedFocusKey != null) {
            previousFocusKey = selectedFocusKey
        }
        if (requiresTrackerLog && state.selectedLogFilter != DnsLogFilter.TRACKERS) {
            viewModel.setLogFilter(DnsLogFilter.TRACKERS)
        }
        selectedFocusKey = newFocusKey
    }
    val displayed = remember(baseDisplayed, selectedFocusKey) {
        when {
            selectedFocusKey == null -> baseDisplayed
            selectedFocusKey == "unattributed" -> baseDisplayed.filter { it.appPackageName.isBlank() }
            selectedFocusKey?.startsWith("resolver:") == true -> {
                val resolver = selectedFocusKey!!.removePrefix("resolver:")
                baseDisplayed.filter { it.resolverIp == resolver }
            }
            selectedFocusKey?.startsWith("package:") == true -> {
                val appPackage = selectedFocusKey!!.removePrefix("package:")
                baseDisplayed.filter { it.appPackageName == appPackage }
            }
            selectedFocusKey?.startsWith("domain:") == true -> {
                val domain = selectedFocusKey!!.removePrefix("domain:")
                baseDisplayed.filter { it.domain == domain }
            }
            else -> baseDisplayed
        }
    }

    // Launcher for the system VPN permission consent dialog
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            context.startForegroundService(
                Intent(context, DnsMonitorVpnService::class.java).apply {
                    action = DnsMonitorVpnService.ACTION_START
                }
            )
            viewModel.setMonitorRunning(true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DNS Activity") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 24-hour summary ──────────────────────────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "24-Hour Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            SummaryMetric(
                                label = "DNS Queries",
                                value = "${state.totalQueries24h}",
                                modifier = Modifier.weight(1f),
                                highlight = false
                            )
                            SummaryMetric(
                                label = "Tracker Hits",
                                value = "${state.trackerHits24h}",
                                modifier = Modifier.weight(1f),
                                highlight = state.trackerHits24h > 0
                            )
                            val pct = if (state.totalQueries24h > 0)
                                (state.trackerHits24h * 100 / state.totalQueries24h) else 0
                            SummaryMetric(
                                label = "Tracker %",
                                value = "$pct%",
                                modifier = Modifier.weight(1f),
                                highlight = pct >= 10
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            SummaryMetric(
                                label = "Resolvers",
                                value = "${state.distinctResolvers24h}",
                                modifier = Modifier.weight(1f),
                                highlight = state.distinctResolvers24h >= 4
                            )
                            SummaryMetric(
                                label = "Non-Monitored",
                                value = "${state.nonMonitoredResolverQueries24h}",
                                modifier = Modifier.weight(1f),
                                highlight = state.nonMonitoredResolverQueries24h > 0
                            )
                            SummaryMetric(
                                label = "Unattributed",
                                value = "${state.unattributedQueries24h}",
                                modifier = Modifier.weight(1f),
                                highlight = state.unattributedQueries24h > 0
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val sensitivityLabel = when (state.dnsLeakSensitivity) {
                            1 -> "Low"
                            3 -> "High"
                            else -> "Balanced"
                        }
                        val sensitivityHint = when (state.dnsLeakSensitivity) {
                            1 -> "Flags only stronger resolver-drift or leak patterns."
                            3 -> "Flags weaker resolver-drift patterns for early warning."
                            else -> "Balances false positives and missed leak signals."
                        }
                        Text(
                            text = "DNS Leak Sensitivity: $sensitivityLabel",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = sensitivityHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                val (unattributedMin, ratioMin, nonMonitoredMin, distinctResolverMin) = when (state.dnsLeakSensitivity) {
                    1 -> listOf(8, 45, 3, 5)
                    3 -> listOf(3, 20, 1, 3)
                    else -> listOf(5, 35, 1, 4)
                }
                val unattributedRatio = if (state.totalQueries24h > 0) {
                    (state.unattributedQueries24h * 100) / state.totalQueries24h
                } else {
                    0
                }
                val ratioTriggered =
                    state.unattributedQueries24h >= unattributedMin && unattributedRatio >= ratioMin
                val nonMonitoredTriggered = state.nonMonitoredResolverQueries24h >= nonMonitoredMin
                val resolverDiversityTriggered = state.distinctResolvers24h >= distinctResolverMin

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Leak Detector Checks (24h)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        DnsLeakConditionRow(
                            label = "Unattributed ratio",
                            value = "${state.unattributedQueries24h} / ${state.totalQueries24h} ($unattributedRatio%)",
                            threshold = "$unattributedMin and $ratioMin%",
                            triggered = ratioTriggered,
                            onClick = { viewModel.setLogFilter(DnsLogFilter.UNATTRIBUTED) }
                        )
                        DnsLeakConditionRow(
                            label = "Non-monitored resolvers",
                            value = "${state.nonMonitoredResolverQueries24h}",
                            threshold = "$nonMonitoredMin+",
                            triggered = nonMonitoredTriggered,
                            onClick = { viewModel.setLogFilter(DnsLogFilter.NON_MONITORED) }
                        )
                        DnsLeakConditionRow(
                            label = "Resolver diversity",
                            value = "${state.distinctResolvers24h}",
                            threshold = "$distinctResolverMin+ resolvers",
                            triggered = resolverDiversityTriggered,
                            onClick = { viewModel.setLogFilter(DnsLogFilter.ALL) }
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.unattributedQueries24h > 0)
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        else
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Potential DNS Leak Signals",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (state.unattributedQueries24h > 0)
                                "${state.unattributedQueries24h} DNS queries in the last 24 hours could not be attributed to a package. This can indicate resolver bypasses, competing VPN traffic, or OS-level DNS that AppTracker could not map cleanly."
                            else
                                "No unattributed DNS queries detected in the last 24 hours.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.unattributedQueries24h > 0)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // ── Monitor on/off card ──────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.isMonitorRunning)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Dns,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    "DNS Monitor",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (state.isMonitorRunning)
                                        "Active — logging DNS queries locally"
                                    else
                                        "Tap to start on-device DNS monitoring",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = state.isMonitorRunning,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    val vpnIntent = VpnService.prepare(context)
                                    if (vpnIntent != null) {
                                        vpnPermissionLauncher.launch(vpnIntent)
                                    } else {
                                        context.startForegroundService(
                                            Intent(context, DnsMonitorVpnService::class.java).apply {
                                                action = DnsMonitorVpnService.ACTION_START
                                            }
                                        )
                                        viewModel.setMonitorRunning(true)
                                    }
                                } else {
                                    context.startService(
                                        Intent(context, DnsMonitorVpnService::class.java).apply {
                                            action = DnsMonitorVpnService.ACTION_STOP
                                        }
                                    )
                                    viewModel.setMonitorRunning(false)
                                }
                            }
                        )
                    }
                }
            }

            // ── Top tracker domains ──────────────────────────────────────────
            if (state.topTrackerDomains.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Top Tracker Domains",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap a domain to switch to tracker log evidence for that domain.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(state.topTrackerDomains) { domainCount ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                setFocus("domain:${domainCount.domain}", requiresTrackerLog = true)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Block,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                domainCount.domain,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${domainCount.queryCount}×",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (state.unattributedQueries.isNotEmpty()) {
                item {
                    Text(
                        "Recent Unattributed Queries",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(state.unattributedQueries.take(10), key = { "unattributed-${it.id}" }) { query ->
                    DnsQueryRow(query)
                }
            }

            if (state.topResolvers24h.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Top DNS Resolvers (24h)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap a resolver to focus the log on that destination.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(state.topResolvers24h, key = { "resolver-${it.resolverIp}" }) { resolver ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                setFocus("resolver:${resolver.resolverIp}")
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                resolver.resolverIp,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${resolver.queryCount}×",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Filter row ───────────────────────────────────────────────────
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Log:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FilterChip(
                        selected = state.selectedLogFilter == DnsLogFilter.ALL,
                        onClick = { viewModel.setLogFilter(DnsLogFilter.ALL) },
                        label = { Text("All Queries") }
                    )
                    FilterChip(
                        selected = state.selectedLogFilter == DnsLogFilter.TRACKERS,
                        onClick = { viewModel.setLogFilter(DnsLogFilter.TRACKERS) },
                        label = { Text("Trackers Only") }
                    )
                    FilterChip(
                        selected = state.selectedLogFilter == DnsLogFilter.UNATTRIBUTED,
                        onClick = { viewModel.setLogFilter(DnsLogFilter.UNATTRIBUTED) },
                        label = { Text("Unattributed") }
                    )
                    FilterChip(
                        selected = state.selectedLogFilter == DnsLogFilter.NON_MONITORED,
                        onClick = { viewModel.setLogFilter(DnsLogFilter.NON_MONITORED) },
                        label = { Text("Non-Monitored") }
                    )
                }
            }

            if (validFocusKeys.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Focus:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedFocusKey == null,
                                onClick = { setFocus(null) },
                                label = { Text("Clear Focus") }
                            )
                            if (canRestorePreviousFocus) {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        val restoreKey = previousFocusKey ?: return@FilterChip
                                        val currentFocus = selectedFocusKey
                                        if (currentFocus != null && currentFocus != restoreKey) {
                                            previousFocusKey = currentFocus
                                        }
                                        if (restoreKey.startsWith("domain:")) {
                                            viewModel.setLogFilter(DnsLogFilter.TRACKERS)
                                        }
                                        selectedFocusKey = restoreKey
                                    },
                                    label = { Text("Restore Previous") }
                                )
                            }
                            if (activeFocusLabel != null) {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        clipboardManager.setText(
                                            AnnotatedString("DNS focus: $activeFocusLabel")
                                        )
                                    },
                                    label = { Text("Copy Active") }
                                )
                            }
                        }
                        if (activeFocusLabel != null) {
                            Text(
                                text = "Active focus: $activeFocusLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (canRestorePreviousFocus && previousFocusLabel != null) {
                            Text(
                                text = "Previous focus: $previousFocusLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedFocusKey == null,
                                onClick = { setFocus(null) },
                                label = { Text("Everything") }
                            )
                            if (showUnattributedFocus) {
                                FilterChip(
                                    selected = selectedFocusKey == "unattributed",
                                    onClick = { setFocus("unattributed") },
                                    label = { Text("Unattributed") }
                                )
                            }
                            resolverFocuses.forEach { resolver ->
                                FilterChip(
                                    selected = selectedFocusKey == "resolver:$resolver",
                                    onClick = { setFocus("resolver:$resolver") },
                                    label = { Text(resolver) }
                                )
                            }
                            packageFocuses.forEach { appPackage ->
                                FilterChip(
                                    selected = selectedFocusKey == "package:$appPackage",
                                    onClick = { setFocus("package:$appPackage") },
                                    label = { Text(appPackage.substringAfterLast('.')) }
                                )
                            }
                            trackerDomainFocuses.forEach { domain ->
                                FilterChip(
                                    selected = selectedFocusKey == "domain:$domain",
                                    onClick = { setFocus("domain:$domain", requiresTrackerLog = true) },
                                    label = { Text(domain) }
                                )
                            }
                        }
                        if (selectedFocusKey != null) {
                            Text(
                                text = "Showing ${displayed.size} of ${baseDisplayed.size} queries for the selected focus.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Query log ────────────────────────────────────────────────────
            if (displayed.isEmpty()) {
                item {
                    Text(
                        text = if (state.isMonitorRunning)
                            "No DNS queries captured yet. DNS activity will appear here as apps make network requests."
                        else
                            "Enable the DNS monitor above to start capturing DNS queries.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(displayed, key = { it.id }) { query ->
                    DnsQueryRow(query)
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DnsLeakConditionRow(
    label: String,
    value: String,
    threshold: String,
    triggered: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(
                text = "Value: $value • Threshold: $threshold",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (triggered) "Triggered" else "OK",
            style = MaterialTheme.typography.labelSmall,
            color = if (triggered) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DnsQueryRow(query: DnsQueryEntity) {
    val timeStr = remember(query.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(query.timestamp))
    }
    val suspiciousReasons = remember(query) {
        buildList {
            if (query.isTracker) add("Tracker")
            if (query.appPackageName.isBlank()) add("Unattributed")
            if (query.resolverIp.isNotBlank() && !DnsResolverCatalog.MONITORED_RESOLVERS.contains(query.resolverIp)) {
                add("Non-monitored resolver")
            }
        }
    }
    val attributionLabel = if (query.appPackageName.isNotBlank()) {
        query.appPackageName
    } else {
        "Unattributed"
    }
    val resolverLabel = query.resolverIp.ifBlank { "Unknown resolver" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (query.isTracker) Icons.Default.Block else Icons.Default.Check,
            contentDescription = null,
            tint = if (query.isTracker) MaterialTheme.colorScheme.error
                   else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(query.domain, style = MaterialTheme.typography.bodyMedium)
            if (suspiciousReasons.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suspiciousReasons.forEach { reason ->
                        DnsReasonChip(label = reason)
                    }
                }
            }
            Text(
                text = "Resolver: $resolverLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = attributionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (query.appPackageName.isBlank()) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (query.isTracker && query.trackerCategory.isNotEmpty()) {
                Text(
                    query.trackerCategory,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Text(
            timeStr,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
}

@Composable
private fun DnsReasonChip(label: String) {
    SuggestionChip(
        onClick = {},
        enabled = false,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
            disabledLabelColor = MaterialTheme.colorScheme.onErrorContainer
        )
    )
}

private fun formatFocusLabel(focusKey: String): String {
    return when {
        focusKey == "unattributed" -> "Unattributed queries"
        focusKey.startsWith("resolver:") -> "Resolver ${focusKey.removePrefix("resolver:")}"
        focusKey.startsWith("package:") -> "App ${focusKey.removePrefix("package:")}"
        focusKey.startsWith("domain:") -> "Tracker domain ${focusKey.removePrefix("domain:")}"
        else -> focusKey
    }
}
