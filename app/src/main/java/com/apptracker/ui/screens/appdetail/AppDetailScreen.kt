package com.apptracker.ui.screens.appdetail

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apptracker.data.model.AppInfo
import com.apptracker.data.model.AppOpsEntry
import com.apptracker.data.model.AppOpsMode
import com.apptracker.data.db.entity.AppOpsHistoryEntity
import com.apptracker.data.db.entity.TrustLabel
import com.apptracker.data.model.BatteryUsageInfo
import com.apptracker.data.model.NetworkUsageInfo
import com.apptracker.data.model.PermissionDetail
import com.apptracker.data.model.ProtectionLevel
import com.apptracker.data.model.UsageTimeRange
import com.apptracker.domain.model.RiskFlag
import com.apptracker.domain.model.RiskScore
import com.apptracker.domain.model.RiskSeverity
import com.apptracker.ui.components.AppIcon
import com.apptracker.ui.components.PermissionCard
import com.apptracker.ui.components.RadarChart
import com.apptracker.ui.components.RiskBadge
import com.apptracker.ui.components.StatCard
import com.apptracker.ui.components.UsageBarChart
import com.apptracker.ui.components.riskColor
import com.apptracker.ui.components.riskLevelFromScore
import com.apptracker.ui.theme.Denied
import com.apptracker.ui.theme.Granted
import com.apptracker.ui.theme.RiskHigh
import com.apptracker.ui.theme.RiskLow
import com.apptracker.ui.theme.RiskMedium
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    packageName: String,
    onBack: () -> Unit,
    onCompare: ((String, String) -> Unit)? = null,
    viewModel: AppDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isWatched by viewModel.isWatched.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = DetailTab.entries.toList()
    val context = LocalContext.current
    var showCompareDialog by remember { mutableStateOf(false) }
    var compareSearchQuery by remember { mutableStateOf("") }
    var showCustomRangeDialog by remember { mutableStateOf(false) }
    var customRangeText by remember { mutableStateOf(state.customRangeDays?.toString() ?: "14") }
    val compareApps by viewModel.allApps.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = state.app?.appName ?: "Loading...",
                    maxLines = 1
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                IconButton(
                    onClick = { viewModel.toggleWatch() },
                    enabled = state.app != null
                ) {
                    Icon(
                        imageVector = if (isWatched) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (isWatched) "Unwatch" else "Watch",
                        tint = if (isWatched) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onCompare != null) {
                    IconButton(
                        onClick = { showCompareDialog = true },
                        enabled = state.app != null
                    ) {
                        Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = "Compare")
                    }
                }
                IconButton(
                    onClick = {
                        val report = viewModel.getReport()
                        if (report != null) {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, report)
                                putExtra(Intent.EXTRA_SUBJECT, "AppTracker Report: ${state.app?.appName}")
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Report"))
                        }
                    },
                    enabled = state.app != null && !state.onDeviceOnly
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share Report")
                }
                IconButton(
                    onClick = {
                        val permissionIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            Intent("android.intent.action.MANAGE_APP_PERMISSIONS").apply {
                                putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                            }
                        } else {
                            null
                        }

                        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                        }

                        runCatching {
                            if (permissionIntent != null) {
                                context.startActivity(permissionIntent)
                            } else {
                                context.startActivity(fallbackIntent)
                            }
                        }.onFailure {
                            context.startActivity(fallbackIntent)
                        }
                    }
                ) {
                    Icon(Icons.Default.Security, contentDescription = "Permission Audit")
                }
            }
        )

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        if (state.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
            }
            return
        }

        val app = state.app ?: return

        // Compare dialog
        if (showCompareDialog && onCompare != null) {
            val filtered = remember(compareSearchQuery, compareApps) {
                compareApps.filter { it.packageName != packageName }
                    .filter { compareSearchQuery.isBlank() || it.appName.contains(compareSearchQuery, ignoreCase = true) }
                    .sortedBy { it.appName }
            }
            AlertDialog(
                onDismissRequest = { showCompareDialog = false; compareSearchQuery = "" },
                title = { Text("Compare with...") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = compareSearchQuery,
                            onValueChange = { compareSearchQuery = it },
                            placeholder = { Text("Search apps") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.height(300.dp)
                        ) {
                            items(filtered) { other ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showCompareDialog = false
                                            compareSearchQuery = ""
                                            onCompare(packageName, other.packageName)
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    AppIcon(icon = other.icon, contentDescription = other.appName, size = 36.dp)
                                    Column {
                                        Text(other.appName, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "Risk: ${other.riskScore}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = riskColor(other.riskScore)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showCompareDialog = false; compareSearchQuery = "" }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // App header
        AppHeaderSection(app, state.riskScore)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "App Trust Label",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TrustLabel.TRUSTED, TrustLabel.SUSPICIOUS, TrustLabel.UNKNOWN).forEach { label ->
                        FilterChip(
                            selected = state.trustLabel == label,
                            onClick = { viewModel.setTrustLabel(label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        if (state.onDeviceOnly) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                )
            ) {
                Text(
                    text = "Privacy Mode is ON. All analysis stays on your device, and sharing is disabled.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(tab.label) },
                    icon = {
                        Icon(
                            imageVector = tabIcon(tab),
                            contentDescription = tab.label
                        )
                    }
                )
            }
        }

        if (tabs[selectedTabIndex] == DetailTab.BATTERY || tabs[selectedTabIndex] == DetailTab.NETWORK) {
            UsageRangeSelector(
                selectedRange = state.selectedRange,
                customRangeDays = state.customRangeDays,
                onRangeSelected = viewModel::onRangeSelected,
                onCustomRangeClicked = { showCustomRangeDialog = true }
            )
        }

        // Tab content
        when (tabs[selectedTabIndex]) {
            DetailTab.PERMISSIONS -> PermissionsTab(app.permissions, state.permissionAuditEntries)
            DetailTab.APP_OPS -> AppOpsTab(app.appOpsEntries)
            DetailTab.BATTERY -> BatteryTab(app.batteryUsage, state.selectedRange, state.isBeginnerMode, state.batteryTrend)
            DetailTab.NETWORK -> NetworkTab(app.networkUsage, state.selectedRange, state.isBeginnerMode, state.networkTrend)
            DetailTab.RISK -> RiskTab(
                riskScore = state.riskScore,
                isBeginnerMode = state.isBeginnerMode,
                permissionCreepIndex = state.permissionCreepIndex,
                dataHoardingScore = state.dataHoardingScore,
                perAppRadarValues = state.perAppRadarValues,
                threatSimulationItems = state.threatSimulationItems,
                safeAlternatives = state.safeAlternatives
            )
            DetailTab.INFO -> AppInfoTab(
                app = app,
                sharedUidPeers = state.sharedUidPeers,
                sdkFingerprints = state.sdkFingerprints,
                certificatePinningInspection = state.certificatePinningInspection
            )
            DetailTab.INSPECTOR -> InspectorTab(
                packageName = packageName,
                exportedComponents = state.exportedComponents,
                signerInfos = state.signerInfos,
                apkDiffSummary = state.apkDiffSummary,
                permissionDiffSummary = state.permissionDiffSummary,
                permissionDiffTimeline = state.permissionDiffTimeline,
                dataFlowSummary = state.dataFlowSummary
            )
            DetailTab.LOGCAT -> LogcatTab(state.logcatLines)
        }
    }

    if (showCustomRangeDialog) {
        AlertDialog(
            onDismissRequest = { showCustomRangeDialog = false },
            title = { Text("Custom Time Period") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter number of days (1-365) to analyze battery and network data.")
                    OutlinedTextField(
                        value = customRangeText,
                        onValueChange = { customRangeText = it.filter { ch -> ch.isDigit() }.take(3) },
                        singleLine = true,
                        label = { Text("Days") }
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val days = customRangeText.toIntOrNull()
                    if (days != null && days in 1..365) {
                        viewModel.onCustomRangeDaysSelected(days)
                        showCustomRangeDialog = false
                    }
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showCustomRangeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun UsageRangeSelector(
    selectedRange: UsageTimeRange,
    customRangeDays: Int?,
    onRangeSelected: (UsageTimeRange) -> Unit,
    onCustomRangeClicked: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(UsageTimeRange.entries) { range ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                label = { Text(range.label) }
            )
        }
        item {
            FilterChip(
                selected = customRangeDays != null,
                onClick = onCustomRangeClicked,
                label = { Text(customRangeDays?.let { "Custom ${it}d" } ?: "Custom") }
            )
        }
    }
}

@Composable
private fun AppHeaderSection(app: AppInfo, riskScore: RiskScore?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(icon = app.icon, contentDescription = app.appName, size = 64.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.titleLarge)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "v${app.versionName ?: "?"} | Target SDK ${app.targetSdkVersion}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = app.category.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            riskScore?.let {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${it.overallScore}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = riskColor(it.overallScore),
                        fontWeight = FontWeight.Bold
                    )
                    RiskBadge(riskLevel = it.riskLevel)
                }
            }
        }
    }
}

// ============ PERMISSIONS TAB ============

@Composable
private fun PermissionsTab(
    permissions: List<PermissionDetail>,
    permissionAuditEntries: List<AppOpsHistoryEntity>
) {
    val dangerous = permissions.filter { it.isDangerous }
    val normal = permissions.filter { it.protectionLevel == ProtectionLevel.NORMAL }
    val signature = permissions.filter {
        it.protectionLevel == ProtectionLevel.SIGNATURE ||
                it.protectionLevel == ProtectionLevel.SIGNATURE_OR_SYSTEM
    }
    val other = permissions.filter {
        it.protectionLevel == ProtectionLevel.UNKNOWN || it.protectionLevel == ProtectionLevel.INTERNAL
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Summary
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = "Total",
                    value = "${permissions.size}",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Dangerous",
                    value = "${dangerous.size}",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Granted",
                    value = "${permissions.count { it.isGranted }}",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (dangerous.isNotEmpty()) {
            item {
                SectionLabel("Dangerous Permissions (${dangerous.size})")
            }
            items(dangerous) { perm ->
                PermissionCard(permission = perm, showDetails = true)
            }
        }

        if (normal.isNotEmpty()) {
            item {
                SectionLabel("Normal Permissions (${normal.size})")
            }
            items(normal) { perm ->
                PermissionCard(permission = perm, showDetails = true)
            }
        }

        if (signature.isNotEmpty()) {
            item {
                SectionLabel("Signature/System Permissions (${signature.size})")
            }
            items(signature) { perm ->
                PermissionCard(permission = perm, showDetails = true)
            }
        }

        if (other.isNotEmpty()) {
            item {
                SectionLabel("Other Permissions (${other.size})")
            }
            items(other) { perm ->
                PermissionCard(permission = perm, showDetails = true)
            }
        }

        if (permissionAuditEntries.isNotEmpty()) {
            item { SectionLabel("Permission Usage Audit (${permissionAuditEntries.size})") }
            items(permissionAuditEntries.take(12)) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = entry.opName.removePrefix("android:"),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Last: ${formatTimestamp(entry.lastAccessTime.coerceAtLeast(entry.timestamp))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Accesses: ${entry.accessCount} • Rejects: ${entry.rejectCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

// ============ APP OPS TAB ============

@Composable
private fun AppOpsTab(ops: List<AppOpsEntry>) {
    if (ops.isEmpty()) {
        EmptyTabContent("No App Ops data available", "Requires Usage Access permission")
        return
    }

    val clipboardOps = ops.filter { it.opName.contains("CLIPBOARD", ignoreCase = true) }
    val allowed = ops.filter { it.mode == AppOpsMode.ALLOWED || it.mode == AppOpsMode.FOREGROUND }
    val denied = ops.filter { it.mode == AppOpsMode.IGNORED || it.mode == AppOpsMode.ERRORED }
    val defaultOps = ops.filter { it.mode == AppOpsMode.DEFAULT || it.mode == AppOpsMode.UNKNOWN }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(title = "Total Ops", value = "${ops.size}", modifier = Modifier.weight(1f))
                StatCard(title = "Allowed", value = "${allowed.size}", modifier = Modifier.weight(1f))
                StatCard(title = "Denied", value = "${denied.size}", modifier = Modifier.weight(1f))
            }
        }

        if (clipboardOps.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Text(
                        text = "Clipboard access has been observed for this app. Review recent copied text exposure and confirm the app genuinely needs clipboard reads.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        if (allowed.isNotEmpty()) {
            item { SectionLabel("Allowed Operations (${allowed.size})") }
            items(allowed) { op -> AppOpsRow(op) }
        }

        if (denied.isNotEmpty()) {
            item { SectionLabel("Denied Operations (${denied.size})") }
            items(denied) { op -> AppOpsRow(op) }
        }

        if (defaultOps.isNotEmpty()) {
            item { SectionLabel("Default/System (${defaultOps.size})") }
            items(defaultOps) { op -> AppOpsRow(op) }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun AppOpsRow(op: AppOpsEntry) {
    val modeColor = when (op.mode) {
        AppOpsMode.ALLOWED, AppOpsMode.FOREGROUND -> Granted
        AppOpsMode.IGNORED, AppOpsMode.ERRORED -> Denied
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = op.opName.removePrefix("android:"),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Category: ${op.category.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (op.lastAccessTime > 0) {
                    Text(
                        text = "Last: ${formatTimestamp(op.lastAccessTime)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = op.mode.name,
                style = MaterialTheme.typography.labelMedium,
                color = modeColor,
                modifier = Modifier
                    .background(modeColor.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

// ============ BATTERY TAB ============

@Composable
private fun BatteryTab(
    battery: BatteryUsageInfo?,
    range: UsageTimeRange,
    isBeginnerMode: Boolean,
    trendData: List<Pair<String, Float>>
) {
    if (battery == null) {
        EmptyTabContent("No battery data available", "Grant Usage Access in Settings")
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isBeginnerMode) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Text(
                        text = "This section shows how long this app stayed active over ${range.label}. " +
                                "Higher background time can impact battery life.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = "Foreground",
                    value = battery.totalForegroundTimeFormatted,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Background",
                    value = battery.totalBackgroundTimeFormatted,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            SectionLabel("Battery Optimization")
            DetailRow("Battery Optimized", if (battery.isBatteryOptimized) "Yes" else "No")
            DetailRow("Doze Whitelisted", if (battery.isDozeWhitelisted) "Yes" else "No")
            DetailRow("Background Restricted", if (battery.isBackgroundRestricted) "Yes" else "No")
        }

        item {
            SectionLabel("Usage Breakdown")
            DetailRow("Foreground Service", BatteryUsageInfo.formatDuration(battery.foregroundServiceTimeMs))
            DetailRow("Visible Time", BatteryUsageInfo.formatDuration(battery.visibleTimeMs))
            DetailRow("Last Used", formatTimestamp(battery.lastTimeUsed))
        }

        if (trendData.isNotEmpty()) {
            item {
                SectionLabel("Usage Trend")
                UsageBarChart(items = trendData, maxItems = 7)
                Text(
                    text = "Minutes over ${range.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (battery.wakelockTimeMs > 0 || battery.alarmWakeups > 0) {
            item {
                SectionLabel("Wake Activity")
                DetailRow("Wakelock Time", BatteryUsageInfo.formatDuration(battery.wakelockTimeMs))
                DetailRow("Alarm Wakeups", "${battery.alarmWakeups}")
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

// ============ NETWORK TAB ============

@Composable
private fun NetworkTab(
    network: NetworkUsageInfo?,
    range: UsageTimeRange,
    isBeginnerMode: Boolean,
    trendData: List<Pair<String, Float>>
) {
    if (network == null) {
        EmptyTabContent("No network data available", "May require phone state permission")
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isBeginnerMode) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Text(
                        text = "This section shows how much data this app used over ${range.label}. " +
                                "Background data can continue even when you are not using the app.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = "Total",
                    value = network.formattedTotal,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "WiFi",
                    value = network.formattedWifi,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Mobile",
                    value = network.formattedMobile,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            SectionLabel("WiFi Usage")
            DetailRow("Downloaded", NetworkUsageInfo.formatBytes(network.wifiRxBytes))
            DetailRow("Uploaded", NetworkUsageInfo.formatBytes(network.wifiTxBytes))
            if (!isBeginnerMode) {
                DetailRow("Packets Rx", "${network.wifiRxPackets}")
                DetailRow("Packets Tx", "${network.wifiTxPackets}")
            }
        }

        item {
            SectionLabel("Mobile Data Usage")
            DetailRow("Downloaded", NetworkUsageInfo.formatBytes(network.mobileRxBytes))
            DetailRow("Uploaded", NetworkUsageInfo.formatBytes(network.mobileTxBytes))
            if (!isBeginnerMode) {
                DetailRow("Packets Rx", "${network.mobileRxPackets}")
                DetailRow("Packets Tx", "${network.mobileTxPackets}")
            }
        }

        item {
            SectionLabel("Usage Pattern")
            DetailRow("Foreground", NetworkUsageInfo.formatBytes(network.foregroundBytes))
            DetailRow("Background", NetworkUsageInfo.formatBytes(network.backgroundBytes))
            DetailRow(
                "Send/Receive Ratio",
                "%.2f".format(network.sendReceiveRatio)
            )
            if (network.sendReceiveRatio > 2.0) {
                WarningBanner("App sends more data than it receives — potential anomaly")
            }
        }

        item {
            val total = network.totalBytes.coerceAtLeast(1L)
            val bgPercent = ((network.backgroundBytes.toDouble() / total) * 100).toInt().coerceIn(0, 100)
            val fgPercent = 100 - bgPercent
            val monthlyMobileEstimateBytes = when (range.days) {
                30 -> network.totalMobileBytes
                else -> (network.totalMobileBytes.toDouble() / range.days * 30.0).toLong()
            }
            val assumedPricePerGb = 0.50 // local heuristic default
            val estimatedMonthlyCost = (monthlyMobileEstimateBytes / 1_073_741_824.0) * assumedPricePerGb

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Foreground vs Background Split", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(
                            title = "Foreground",
                            value = "$fgPercent%",
                            subtitle = NetworkUsageInfo.formatBytes(network.foregroundBytes),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "Background",
                            value = "$bgPercent%",
                            subtitle = NetworkUsageInfo.formatBytes(network.backgroundBytes),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (bgPercent >= 60) {
                        WarningBanner("Most data is used in background — review background data permissions.")
                    }
                    DetailRow(
                        "Estimated Monthly Mobile Cost",
                        "$${"%.2f".format(estimatedMonthlyCost)} (at $$${"%.2f".format(assumedPricePerGb)}/GB)"
                    )
                    Text(
                        text = "Local estimate from mobile usage in selected period.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (trendData.isNotEmpty()) {
            item {
                SectionLabel("Data Trend")
                UsageBarChart(items = trendData, maxItems = 7)
                Text(
                    text = "MB over ${range.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

// ============ RISK TAB ============

@Composable
private fun RiskTab(
    riskScore: RiskScore?,
    isBeginnerMode: Boolean,
    permissionCreepIndex: Int,
    dataHoardingScore: Int = 0,
    perAppRadarValues: List<Pair<String, Float>> = emptyList(),
    threatSimulationItems: List<String> = emptyList(),
    safeAlternatives: List<String> = emptyList()
) {
    if (riskScore == null) {
        EmptyTabContent("Risk analysis unavailable")
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Why this score?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isBeginnerMode) {
                            "AppTracker combines permissions, behavior, network activity, and battery impact. " +
                                    "Higher scores indicate higher potential privacy/security risk."
                        } else {
                            "Overall score is composed from Permission, Behavior, Network, and Battery sub-scores. " +
                                    "Flags below explain which signals increased risk."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = plainEnglishRiskSummary(riskScore),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            RadarChart(
                values = if (perAppRadarValues.isNotEmpty()) perAppRadarValues else listOf(
                    "Permission" to riskScore.permissionScore.toFloat(),
                    "Behavior" to riskScore.behaviorScore.toFloat(),
                    "Network" to riskScore.networkScore.toFloat(),
                    "Battery" to riskScore.batteryScore.toFloat(),
                    "Hoarding" to dataHoardingScore.toFloat()
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            // Score breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = "Overall",
                    value = "${riskScore.overallScore}",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Permission",
                    value = "${riskScore.permissionScore}",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Behavior",
                    value = "${riskScore.behaviorScore}",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = "Network",
                    value = "${riskScore.networkScore}",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Battery",
                    value = "${riskScore.batteryScore}",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Permission Creep Index", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "$permissionCreepIndex dangerous permission additions across update history on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            val hoardColor = riskColor(dataHoardingScore)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Data Hoarding Score", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "$dataHoardingScore / 100",
                            style = MaterialTheme.typography.labelLarge,
                            color = hoardColor
                        )
                    }
                    Text(
                        text = com.apptracker.util.PrivacyScoreUtils.dataHoardingLabel(dataHoardingScore),
                        style = MaterialTheme.typography.bodySmall,
                        color = hoardColor
                    )
                    Text(
                        text = "Measures the combination of sensitive permissions held — higher means more data access potential.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!isBeginnerMode) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Score components", style = MaterialTheme.typography.titleSmall)
                        DetailRow("Permissions", "Sensitive permissions and grants")
                        DetailRow("Behavior", "Suspicious patterns and app-ops activity")
                        DetailRow("Network", "Background transfer and send/receive anomalies")
                        DetailRow("Battery", "High background usage and wake activity")
                    }
                }
            }
        }

        if (threatSimulationItems.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Threat Simulation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "If this app turned malicious, it could:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        threatSimulationItems.forEach { item ->
                            Text("• $item", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (safeAlternatives.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Safe Alternatives", style = MaterialTheme.typography.titleSmall)
                        safeAlternatives.forEach { alt ->
                            Text(
                                text = "• Consider: $alt",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (riskScore.flags.isNotEmpty()) {
            item { SectionLabel("Risk Flags (${riskScore.flags.size})") }
            items(riskScore.flags) { flag ->
                RiskFlagCard(flag)
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = RiskLow.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Shield, "Safe", tint = RiskLow)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "No risk flags detected. This app appears safe.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun RiskFlagCard(flag: RiskFlag) {
    val color = when (flag.severity) {
        RiskSeverity.CRITICAL -> com.apptracker.ui.theme.RiskCritical
        RiskSeverity.DANGER -> RiskHigh
        RiskSeverity.WARNING -> RiskMedium
        RiskSeverity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = flag.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = color
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = flag.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun plainEnglishRiskSummary(riskScore: RiskScore): String {
    val highFlags = riskScore.flags.take(3).map { it.title.lowercase() }
    val flagSummary = if (highFlags.isNotEmpty()) {
        highFlags.joinToString(", ")
    } else {
        "no major risk flags"
    }

    return when {
        riskScore.overallScore >= 75 ->
            "High risk: this app shows $flagSummary and should be reviewed now."
        riskScore.overallScore >= 50 ->
            "Moderate risk: this app has $flagSummary; review permissions and background activity."
        else ->
            "Lower risk right now: $flagSummary, but keep monitoring after app updates."
    }
}

// ============ APP INFO TAB ============

@Composable
private fun InspectorTab(
    packageName: String,
    exportedComponents: List<AppComponentInspection>,
    signerInfos: List<ApkSignerInspection>,
    apkDiffSummary: ApkDiffSummary?,
    permissionDiffSummary: PermissionDiffSummary?,
    permissionDiffTimeline: List<PermissionDiffTimelineEntry>,
    dataFlowSummary: List<String>
) {
    val context = LocalContext.current
    var showOpenOnly by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (dataFlowSummary.isNotEmpty()) {
            item {
                SectionLabel("Data Flow Diagram (Text)")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        dataFlowSummary.forEach { line ->
                            Text(text = line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (apkDiffSummary != null) {
            item {
                SectionLabel("APK Diff on Update")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DetailRow("Previous Version Code", "${apkDiffSummary.previousVersionCode}")
                        DetailRow("Previous Snapshot", formatTimestamp(apkDiffSummary.previousCapturedAt))
                        DetailRow("Previous APK Size", com.apptracker.data.model.NetworkUsageInfo.formatBytes(apkDiffSummary.previousSizeBytes))
                        DetailRow("Current APK Size", com.apptracker.data.model.NetworkUsageInfo.formatBytes(apkDiffSummary.currentSizeBytes))
                        DetailRow(
                            "Size Delta",
                            (if (apkDiffSummary.sizeDeltaBytes >= 0) "+" else "") +
                                com.apptracker.data.model.NetworkUsageInfo.formatBytes(kotlin.math.abs(apkDiffSummary.sizeDeltaBytes))
                        )
                        DetailRow("Signer Changed", if (apkDiffSummary.signatureChanged) "Yes" else "No")
                    }
                }
            }
        }

        if (permissionDiffSummary != null &&
            (permissionDiffSummary.addedPermissions.isNotEmpty() || permissionDiffSummary.removedPermissions.isNotEmpty())
        ) {
            item {
                SectionLabel("Permission Diff on Update")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DetailRow("Compared To Version", "${permissionDiffSummary.previousVersionCode}")
                        DetailRow("Previous Snapshot", formatTimestamp(permissionDiffSummary.previousCapturedAt))
                        if (permissionDiffSummary.addedPermissions.isNotEmpty()) {
                            Text(
                                text = "Added Dangerous Permissions",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            permissionDiffSummary.addedPermissions.take(8).forEach {
                                Text("+ $it", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (permissionDiffSummary.removedPermissions.isNotEmpty()) {
                            Text(
                                text = "Removed Dangerous Permissions",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            permissionDiffSummary.removedPermissions.take(8).forEach {
                                Text("- $it", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        if (permissionDiffTimeline.isNotEmpty()) {
            item {
                SectionLabel("Permission Change Timeline")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        permissionDiffTimeline.forEach { entry ->
                            Text(
                                text = "v${entry.fromVersionCode} → v${entry.toVersionCode} • ${formatTimestamp(entry.capturedAt)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "+${entry.addedCount} added • -${entry.removedCount} removed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (entry.addedPermissions.isNotEmpty()) {
                                Text(
                                    text = "Added: ${entry.addedPermissions.take(3).joinToString()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (entry.removedPermissions.isNotEmpty()) {
                                Text(
                                    text = "Removed: ${entry.removedPermissions.take(3).joinToString()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionLabel("Signature Viewer")
            if (signerInfos.isEmpty()) {
                Text(
                    text = "No signer metadata available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(signerInfos) { signer ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DetailRow("Chain Position", "${signer.chainPosition} / ${signer.chainSize}")
                    DetailRow("SHA-256", signer.sha256)
                    DetailRow("Subject", signer.subject)
                    DetailRow("Issuer", signer.issuer)
                    DetailRow("Signature Algorithm", signer.signatureAlgorithm)
                    DetailRow("Public Key", "${signer.publicKeyAlgorithm} (${signer.publicKeyBits} bits)")
                    DetailRow("Key Strength", signer.keyStrength)
                    DetailRow("Valid From", signer.validFrom)
                    DetailRow("Valid To", signer.validTo)
                    DetailRow("Expiry Risk", signer.expiryRisk)
                    DetailRow("Days Until Expiry", "${signer.daysUntilExpiry}")
                }
            }
        }

        item {
            SectionLabel("Intent Inspector")
            Text(
                text = "Exported components can be reached by other apps or the system. Review exposed entry points and permissions guarding them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (exportedComponents.isEmpty()) {
            item {
                Text(
                    text = "No exported activities, services, receivers, or providers were detected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            fun exposureWeight(component: AppComponentInspection): Int {
                val base = when (component.kind) {
                    "Provider" -> 14
                    "Service" -> 12
                    "Receiver" -> 10
                    "Activity" -> 8
                    else -> 6
                }
                return if (component.permission.isNullOrBlank()) base else (base / 2).coerceAtLeast(1)
            }

            val openComponents = exportedComponents.filter { it.permission.isNullOrBlank() }
            val guardedComponents = exportedComponents.filter { !it.permission.isNullOrBlank() }
            val sortedComponents = exportedComponents.sortedByDescending { exposureWeight(it) }
            val visibleComponents = if (showOpenOnly) {
                sortedComponents.filter { it.permission.isNullOrBlank() }
            } else {
                sortedComponents
            }
            val topActions = openComponents
                .sortedByDescending { exposureWeight(it) + if (it.enabled) 2 else 0 }
                .take(3)
                .map { component ->
                    val action = when (component.kind) {
                        "Provider" -> "Restrict or remove exported provider access"
                        "Service" -> "Disable unnecessary exported background service"
                        "Receiver" -> "Harden receiver with explicit permission guard"
                        "Activity" -> "Limit exported activity entry points"
                        else -> "Review exported component exposure"
                    }
                    "${component.kind}: $action (${component.name.substringAfterLast('.')})"
                }
            val weightedRiskScore = (
                openComponents.count { it.kind == "Activity" } * 8 +
                    openComponents.count { it.kind == "Service" } * 12 +
                    openComponents.count { it.kind == "Receiver" } * 10 +
                    openComponents.count { it.kind == "Provider" } * 14 +
                    guardedComponents.count { it.kind == "Provider" } * 4 +
                    guardedComponents.count { it.kind == "Service" } * 3
                ).coerceIn(0, 100)

            val severityLabel = when {
                weightedRiskScore >= 70 -> "High"
                weightedRiskScore >= 40 -> "Medium"
                else -> "Low"
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "Exported",
                        value = "${exportedComponents.size}",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Guarded",
                        value = "${exportedComponents.count { !it.permission.isNullOrBlank() }}",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Open",
                        value = "${exportedComponents.count { it.permission.isNullOrBlank() }}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Exposure Severity: $severityLabel ($weightedRiskScore/100)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Prioritize reviewing ${openComponents.size} open exported components first, then validate permission guards on ${guardedComponents.size} guarded components.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (topActions.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Top 3 Actions Now",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            topActions.forEach { action ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "• $action",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    androidx.compose.material3.TextButton(
                                        onClick = {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:$packageName")
                                            }
                                            context.startActivity(intent)
                                        }
                                    ) {
                                        Text("Open")
                                    }
                                    androidx.compose.material3.TextButton(
                                        onClick = {
                                            val permissionIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                Intent("android.intent.action.MANAGE_APP_PERMISSIONS").apply {
                                                    putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                                                }
                                            } else {
                                                null
                                            }
                                            val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:$packageName")
                                            }
                                            runCatching {
                                                if (permissionIntent != null) {
                                                    context.startActivity(permissionIntent)
                                                } else {
                                                    context.startActivity(fallbackIntent)
                                                }
                                            }.onFailure {
                                                context.startActivity(fallbackIntent)
                                            }
                                        }
                                    ) {
                                        Text("Permissions")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = showOpenOnly,
                        onClick = { showOpenOnly = !showOpenOnly },
                        label = { Text("Open only") }
                    )
                    Text(
                        text = if (showOpenOnly) "Showing highest-risk open components" else "Showing all components (ranked by risk)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(visibleComponents) { component ->
                val componentRisk = (exposureWeight(component) * 7 + if (component.enabled) 5 else 0).coerceIn(0, 100)
                val exposureLabel = if (component.permission.isNullOrBlank()) "Open" else "Guarded"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                component.name,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$componentRisk/100",
                                style = MaterialTheme.typography.labelMedium,
                                color = riskColor(componentRisk),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "$exposureLabel exposure",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (exposureLabel == "Open") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        DetailRow("Type", component.kind)
                        DetailRow("Enabled", if (component.enabled) "Yes" else "No")
                        DetailRow("Permission Guard", component.permission ?: "None")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun LogcatTab(logLines: List<String>) {
    if (logLines.isEmpty()) {
        EmptyTabContent("No logcat entries", "Logs may be unavailable on this Android version/profile")
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(logLines) { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun AppInfoTab(
    app: AppInfo,
    sharedUidPeers: List<AppInfo>,
    sdkFingerprints: List<String>,
    certificatePinningInspection: com.apptracker.util.CertificatePinningInspection?
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SectionLabel("General")
            DetailRow("Package Name", app.packageName)
            DetailRow("Version", app.versionName ?: "N/A")
            DetailRow("Version Code", "${app.versionCode}")
            DetailRow("Target SDK", "${app.targetSdkVersion}")
            DetailRow("Min SDK", "${app.minSdkVersion}")
            DetailRow("Install Source", app.installSourceLabel)
            DetailRow("Sideloaded", if (app.isSideloaded) "Yes" else "No")
            DetailRow("Linux UID", if (app.linuxUid >= 0) "${app.linuxUid}" else "Unknown")
            DetailRow("Shared UID Peers", "${sharedUidPeers.size}")
            DetailRow("System App", if (app.isSystemApp) "Yes" else "No")
            DetailRow("Enabled", if (app.isEnabled) "Yes" else "No")
        }

        if (sharedUidPeers.isNotEmpty()) {
            item {
                SectionLabel("Shared UID Group")
                Text(
                    text = "Apps sharing this UID may exchange data more directly within the same Linux sandbox.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(sharedUidPeers) { peer ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(peer.appName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            peer.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            SectionLabel("SDK Fingerprint (Heuristic)")
            if (sdkFingerprints.isEmpty()) {
                Text(
                    text = "No known analytics/ads SDK markers detected in quick local scan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sdkFingerprints.forEach { marker ->
                        Text("• $marker", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            SectionLabel("Certificate Pinning (Quick APK Scan)")
            val inspection = certificatePinningInspection
            if (inspection == null) {
                Text(
                    text = "Certificate pinning scan not available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (inspection.status) {
                            com.apptracker.util.CertificatePinningStatus.LIKELY_PRESENT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            com.apptracker.util.CertificatePinningStatus.CONFIG_PRESENT -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                            com.apptracker.util.CertificatePinningStatus.NO_EVIDENCE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                            com.apptracker.util.CertificatePinningStatus.UNAVAILABLE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = inspection.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (inspection.evidence.isNotEmpty()) {
                            inspection.evidence.forEach { evidence ->
                                Text("• $evidence", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionLabel("Dates")
            DetailRow("Installed", formatTimestamp(app.installTime))
            DetailRow("Last Updated", formatTimestamp(app.lastUpdateTime))
        }

        item {
            SectionLabel("Storage")
            DetailRow("APK Location", app.sourceDir ?: "N/A")
            DetailRow("Data Directory", app.dataDir ?: "N/A")
            app.storageUsage?.let { storage ->
                DetailRow("App Size", storage.formattedAppSize)
                DetailRow("Data Size", storage.formattedDataSize)
                DetailRow("Cache Size", storage.formattedCacheSize)
                DetailRow("Total Size", storage.formattedTotalSize)
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

// ============ COMMON COMPONENTS ============

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun WarningBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = RiskMedium.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, null, tint = RiskMedium)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = RiskMedium
            )
        }
    }
}

@Composable
private fun EmptyTabContent(title: String, subtitle: String? = null) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun tabIcon(tab: DetailTab): ImageVector = when (tab) {
    DetailTab.PERMISSIONS -> Icons.Default.Security
    DetailTab.APP_OPS -> Icons.Default.Timeline
    DetailTab.BATTERY -> Icons.Default.BatteryAlert
    DetailTab.NETWORK -> Icons.Default.NetworkCheck
    DetailTab.RISK -> Icons.Default.Shield
    DetailTab.INFO -> Icons.Default.Info
    DetailTab.INSPECTOR -> Icons.Default.Search
    DetailTab.LOGCAT -> Icons.Default.Timeline
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0) return "N/A"
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
