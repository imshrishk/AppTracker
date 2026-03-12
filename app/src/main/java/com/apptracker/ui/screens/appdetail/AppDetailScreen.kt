package com.apptracker.ui.screens.appdetail

import android.content.Intent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
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
import com.apptracker.data.model.BatteryUsageInfo
import com.apptracker.data.model.NetworkUsageInfo
import com.apptracker.data.model.PermissionDetail
import com.apptracker.data.model.ProtectionLevel
import com.apptracker.domain.model.RiskFlag
import com.apptracker.domain.model.RiskScore
import com.apptracker.domain.model.RiskSeverity
import com.apptracker.ui.components.AppIcon
import com.apptracker.ui.components.PermissionCard
import com.apptracker.ui.components.RiskBadge
import com.apptracker.ui.components.StatCard
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
                    enabled = state.app != null
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share Report")
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

        // Tab content
        when (tabs[selectedTabIndex]) {
            DetailTab.PERMISSIONS -> PermissionsTab(app.permissions)
            DetailTab.APP_OPS -> AppOpsTab(app.appOpsEntries)
            DetailTab.BATTERY -> BatteryTab(app.batteryUsage)
            DetailTab.NETWORK -> NetworkTab(app.networkUsage)
            DetailTab.RISK -> RiskTab(state.riskScore)
            DetailTab.INFO -> AppInfoTab(app)
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
private fun PermissionsTab(permissions: List<PermissionDetail>) {
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
private fun BatteryTab(battery: BatteryUsageInfo?) {
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
private fun NetworkTab(network: NetworkUsageInfo?) {
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
            DetailRow("Packets Rx", "${network.wifiRxPackets}")
            DetailRow("Packets Tx", "${network.wifiTxPackets}")
        }

        item {
            SectionLabel("Mobile Data Usage")
            DetailRow("Downloaded", NetworkUsageInfo.formatBytes(network.mobileRxBytes))
            DetailRow("Uploaded", NetworkUsageInfo.formatBytes(network.mobileTxBytes))
            DetailRow("Packets Rx", "${network.mobileRxPackets}")
            DetailRow("Packets Tx", "${network.mobileTxPackets}")
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

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

// ============ RISK TAB ============

@Composable
private fun RiskTab(riskScore: RiskScore?) {
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

// ============ APP INFO TAB ============

@Composable
private fun AppInfoTab(app: AppInfo) {
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
            DetailRow("System App", if (app.isSystemApp) "Yes" else "No")
            DetailRow("Enabled", if (app.isEnabled) "Yes" else "No")
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
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0) return "N/A"
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
