package com.apptracker.ui.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apptracker.data.model.AppInfo
import com.apptracker.data.model.UsageTimeRange
import com.apptracker.ui.components.AppIcon
import com.apptracker.ui.components.LineChart
import com.apptracker.ui.components.RadarChart
import com.apptracker.ui.components.RiskScoreIndicator
import com.apptracker.ui.components.StatCard
import com.apptracker.ui.components.UsageBarChart
import com.apptracker.ui.components.riskColor
import com.apptracker.ui.theme.StarAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAppClick: (String) -> Unit,
    onViewAllClick: () -> Unit,
    onOpenRemediation: () -> Unit,
    onOpenDnsActivity: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val watchedEntities by viewModel.watchedApps.collectAsState()
    val watchedApps = remember(watchedEntities, state.allApps) {
        val packageMap = state.allApps.associateBy { it.packageName }
        watchedEntities.mapNotNull { packageMap[it.packageName] }
    }
    var isRefreshing by remember { mutableStateOf(false) }
    var showCustomRangeDialog by remember { mutableStateOf(false) }
    var customRangeText by remember { mutableStateOf(state.customRangeDays?.toString() ?: "14") }
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) isRefreshing = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    "AppTracker",
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                IconButton(onClick = { viewModel.loadData() }) {
                    Icon(Icons.Default.Refresh, "Refresh")
                }
            }
        )

        when {
            state.isLoading && !isRefreshing -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error ?: "Unknown error",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.loadData() }) {
                            Text("Retry")
                        }
                    }
                }
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { isRefreshing = true; viewModel.loadData() },
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
            Text(
                text = "Insights Period",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UsageTimeRange.entries.forEach { range ->
                    FilterChip(
                        selected = state.selectedRange == range,
                        onClick = { viewModel.onRangeSelected(range) },
                        label = { Text(range.label) }
                    )
                }
                FilterChip(
                    selected = state.customRangeDays != null,
                    onClick = { showCustomRangeDialog = true },
                    label = { Text(state.customRangeDays?.let { "Custom ${it}d" } ?: "Custom") }
                )
            }

            if (state.isBeginnerMode) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Text(
                        text = "Beginner Tip: Higher risk score means an app may need extra review. " +
                                "Open any app to see clear explanations and recommendations.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Analysis Thresholds",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "High-risk cutoff: ${state.highRiskThreshold}+  •  Heavy background: ${state.backgroundHeavyHours}h+",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Adjust these in Settings → Privacy & Experience",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Threat Feed",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${state.appsUpdated7d} updated • ${state.permissionDeltaEvents7d} deltas • ${state.nightActivityEvents7d} night anomalies • ${state.sensitiveAppOpsEvents7d} sensitive events • ${state.autoRevokeEvents7d} auto-revokes • ${state.darkPatternEvents7d} re-requests",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.watchlistChangeEvents7d > 0 || state.permissionSpikeEvents7d > 0 ||
                        state.burstNetworkEvents7d > 0 || state.appInstallEvents7d > 0 ||
                        state.heuristicSecurityEvents7d > 0 || state.dnsLeakEvents7d > 0) {
                        Text(
                            text = buildString {
                                if (state.watchlistChangeEvents7d > 0) append("${state.watchlistChangeEvents7d} watchlist • ")
                                if (state.permissionSpikeEvents7d > 0) append("${state.permissionSpikeEvents7d} spikes • ")
                                if (state.burstNetworkEvents7d > 0) append("${state.burstNetworkEvents7d} burst-net • ")
                                if (state.heuristicSecurityEvents7d > 0) append("${state.heuristicSecurityEvents7d} heuristic flags • ")
                                if (state.dnsLeakEvents7d > 0) append("${state.dnsLeakEvents7d} DNS leak signals • ")
                                if (state.appInstallEvents7d > 0) append("${state.appInstallEvents7d} installs")
                                trimEnd(' ', '•')
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                            if (state.dnsTrackerHits7d > 0) {
                                Text(
                                    text = "${state.dnsTrackerHits7d} DNS tracker hits",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenRemediation),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Guided Remediation",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Step-by-step fixes for your highest-risk apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onOpenRemediation) { Text("Open") }
                }
            }

                // DNS Activity shortcut
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenDnsActivity),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "DNS Activity",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (state.dnsTrackerHits7d > 0)
                                    "${state.dnsTrackerHits7d} tracker hits in the last 7 days"
                                else
                                    "Monitor DNS queries for tracker domains locally",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.dnsTrackerHits7d > 0)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onOpenDnsActivity) { Text("View") }
                    }
                }

            // Overview stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Total Apps",
                    value = "${state.totalApps}",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Risk Score",
                    value = "${state.averageRiskScore}",
                    subtitle = "avg",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Dangerous",
                    value = "${state.totalDangerousPermissions}",
                    subtitle = "permissions",
                    modifier = Modifier.weight(1f)
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Device Health Score",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = healthLabel(state.deviceHealthScore),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${state.deviceHealthScore}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = riskColor(100 - state.deviceHealthScore),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (state.riskRadarValues.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Risk Surface",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "A quick view of your current permission, behavior, network, battery, and data-collection exposure.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        RadarChart(values = state.riskRadarValues, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            if (state.healthTrendSixMonths.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Health Trend (6 Months)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        LineChart(points = state.healthTrendSixMonths, maxPoints = 6)
                        Text(
                            text = "Benchmark: ${state.healthBenchmarkLabel} • Recent avg ${state.recentAverageHealthScore}/100",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.baselineSummary != null || state.healthChecklist.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.24f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Baseline & Checklist",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (state.checklistTotalCount > 0) {
                            Text(
                                text = "${state.checklistConfiguredCount} of ${state.checklistTotalCount} best practices configured",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        state.baselineSummary?.let { baseline ->
                            Text(
                                text = buildString {
                                    append("Baseline captured with ${baseline.appCount} apps")
                                    append(" • Health ${baseline.healthScore}/100")
                                    state.healthDeltaVsBaseline?.let { delta ->
                                        append(" • ")
                                        append(if (delta >= 0) "+$delta vs baseline" else "$delta vs baseline")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        state.healthChecklist.forEach { item ->
                            Text(
                                text = (if (item.configured) "✅ " else "⚠️ ") + item.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            state.peerBenchmark?.let { benchmark ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.24f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Peer Benchmark",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Closest local archetype: ${benchmark.archetypeName} • ${benchmark.archetypeHealthScore}/100",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = benchmark.comparisonLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = benchmark.archetypeDescription,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        benchmark.matchedSignals.forEach { signal ->
                            Text(
                                text = "• $signal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (state.healthImpactEntries.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Health Score Breakdown",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Top apps dragging device health right now:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        state.healthImpactEntries.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAppClick(entry.app.packageName) },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = entry.app.appName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                                Text(
                                    text = "-${entry.impactPoints} pts",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            if (state.highBackgroundApps.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Background Activity Alerts",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${state.highBackgroundApps.size} apps exceeded ${state.backgroundHeavyHours}h background usage in this period.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Watched apps section
            if (watchedApps.isNotEmpty()) {
                SectionHeader(
                    title = "Watched Apps (${watchedApps.size})",
                    titleIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = StarAmber) },
                    onViewAll = onViewAllClick
                )
                watchedApps.take(5).forEach { app ->
                    AppSummaryRow(app = app, onClick = { onAppClick(app.packageName) })
                }
            }

            // High risk apps section
            if (state.highRiskApps.isNotEmpty()) {
                SectionHeader(
                    title = "High Risk Apps (${state.highRiskApps.size}) · ${state.highRiskThreshold}+",
                    onViewAll = onViewAllClick
                )
                state.highRiskApps.take(3).forEach { app ->
                    AppSummaryRow(app = app, onClick = { onAppClick(app.packageName) })
                }
            }

            if (state.highBackgroundApps.isNotEmpty()) {
                SectionHeader(title = "High Background Usage", onViewAll = onViewAllClick)
                state.highBackgroundApps.forEach { app ->
                    AppSummaryRow(app = app, onClick = { onAppClick(app.packageName) })
                }
            }

            // Top battery consumers
            if (state.topBatteryApps.isNotEmpty()) {
                SectionHeader(title = "Top Battery Consumers")
                UsageBarChart(
                    items = state.topBatteryApps.map { app ->
                        val totalMs = (app.batteryUsage?.foregroundTimeMs ?: 0) +
                                (app.batteryUsage?.backgroundTimeMs ?: 0)
                        app.appName to (totalMs / 60_000f) // minutes
                    }
                )
                Text(
                    text = "Usage in minutes (${state.customRangeDays?.let { "${it}d" } ?: state.selectedRange.label})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Top network consumers
            if (state.topNetworkApps.isNotEmpty()) {
                SectionHeader(title = "Top Data Users")
                UsageBarChart(
                    items = state.topNetworkApps.map { app ->
                        app.appName to (app.networkUsage?.totalBytes?.toFloat() ?: 0f)
                    }
                )
                Text(
                    text = "Data usage in bytes (${state.customRangeDays?.let { "${it}d" } ?: state.selectedRange.label})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Recently updated
            if (state.recentlyUpdatedApps.isNotEmpty()) {
                SectionHeader(
                    title = "Recently Updated",
                    onViewAll = onViewAllClick
                )
                state.recentlyUpdatedApps.forEach { app ->
                    AppSummaryRow(app = app, onClick = { onAppClick(app.packageName) })
                }
            }

            if (state.newlyInstalledApps.isNotEmpty()) {
                SectionHeader(title = "Newly Installed (7d)", onViewAll = onViewAllClick)
                state.newlyInstalledApps.forEach { app ->
                    AppSummaryRow(app = app, onClick = { onAppClick(app.packageName) })
                }
            }

            if (state.sensitivePermissionApps.isNotEmpty()) {
                SectionHeader(title = "Camera / Mic / Location Apps", onViewAll = onViewAllClick)
                state.sensitivePermissionApps.forEach { app ->
                    AppSummaryRow(app = app, onClick = { onAppClick(app.packageName) })
                }
            }

            if (state.sideloadedApps.isNotEmpty()) {
                SectionHeader(title = "Sideloaded / Unknown Source Apps", onViewAll = onViewAllClick)
                state.sideloadedApps.forEach { app ->
                    AppSummaryRow(app = app, onClick = { onAppClick(app.packageName) })
                }
            }

            if (state.dormantApps.isNotEmpty()) {
                SectionHeader(title = "Dormant Apps (60+ Days Unused)", onViewAll = onViewAllClick)
                state.dormantApps.forEach { app ->
                    AppSummaryRow(app = app, onClick = { onAppClick(app.packageName) })
                }
                if (state.dormantEventCount7d > 0) {
                    Text(
                        text = "${state.dormantEventCount7d} dormant-app alerts this week — review and uninstall unused apps.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            if (state.topDataHoarders.isNotEmpty()) {
                SectionHeader(title = "Top Data Collectors", onViewAll = onViewAllClick)
                state.topDataHoarders.forEach { app ->
                    val hoardScore = com.apptracker.util.PrivacyScoreUtils.dataHoardingScore(app.permissions)
                    Column {
                        AppSummaryRow(app = app, onClick = { onAppClick(app.packageName) })
                        Text(
                            text = "Data score: $hoardScore/100 \u2022 ${com.apptracker.util.PrivacyScoreUtils.dataHoardingLabel(hoardScore)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = com.apptracker.ui.components.riskColor(hoardScore),
                            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                        )
                    }
                }
            }

                    Spacer(modifier = Modifier.height(16.dp))
                    } // end content Column
                } // end PullToRefreshBox
            } // end else ->
        } // end when
    } // end outer Column

    if (showCustomRangeDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCustomRangeDialog = false },
            title = { Text("Custom Insights Period") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter number of days (1-365) for dashboard battery/network insights.")
                    OutlinedTextField(
                        value = customRangeText,
                        onValueChange = { customRangeText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Days") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val days = customRangeText.toIntOrNull()
                    if (days != null && days in 1..365) {
                        viewModel.onCustomRangeDaysSelected(days)
                        showCustomRangeDialog = false
                    }
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomRangeDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    titleIcon: (@Composable () -> Unit)? = null,
    onViewAll: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (titleIcon != null) titleIcon()
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (onViewAll != null) {
            TextButton(onClick = onViewAll) {
                Text("View All")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.height(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AppSummaryRow(
    app: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIcon(icon = app.icon, contentDescription = app.appName, size = 40.dp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "${app.permissions.count { it.isDangerous && it.isGranted }} dangerous permissions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RiskScoreIndicator(score = app.riskScore)
        }
    }
}

private fun healthLabel(score: Int): String = when {
    score >= 85 -> "Excellent (A)"
    score >= 70 -> "Good (B)"
    score >= 55 -> "Moderate (C)"
    score >= 40 -> "Needs Attention (D)"
    else -> "Critical (F)"
}
