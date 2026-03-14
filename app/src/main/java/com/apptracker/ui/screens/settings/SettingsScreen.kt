package com.apptracker.ui.screens.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apptracker.data.model.UsageTimeRange
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showCleanupDialog by remember { mutableStateOf(false) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var ruleName by remember { mutableStateOf("") }
    var ruleMetric by remember { mutableStateOf(SettingsViewModel.metricOptions.first()) }
    var ruleComparator by remember { mutableStateOf(SettingsViewModel.comparatorOptions.first()) }
    var ruleThreshold by remember { mutableStateOf("70") }
    var ruleSeverity by remember { mutableStateOf(SettingsViewModel.severityOptions[2]) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.exportContent, state.exportMimeType, state.exportFileName) {
        val content = state.exportContent
        val mimeType = state.exportMimeType
        if (!content.isNullOrBlank() && !mimeType.isNullOrBlank()) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_TEXT, content)
                putExtra(Intent.EXTRA_SUBJECT, state.exportFileName ?: "apptracker-export")
            }
            context.startActivity(Intent.createChooser(sendIntent, "Export AppTracker data"))
            viewModel.clearPendingExport()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
    ) {

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Required Permissions Section
            SectionTitle("Required Permissions")
            Text(
                text = "AppTracker needs special permissions to inspect other apps. " +
                        "Tap each item below to grant access.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SettingsItem(
                icon = Icons.Default.DataUsage,
                title = "Usage Access",
                subtitle = "Required for battery stats and app usage data",
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            )

            SettingsItem(
                icon = Icons.Default.BatteryStd,
                title = "Battery Optimization",
                subtitle = "Allow AppTracker to run in background for monitoring",
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    )
                }
            )

            SettingsItem(
                icon = Icons.Default.Security,
                title = "App Permissions",
                subtitle = "View and manage AppTracker's own permissions",
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )

            SettingsItem(
                icon = Icons.Default.Info,
                title = "Notification Permissions",
                subtitle = "Allow alerts, scan summaries, and local monitoring notifications",
                onClick = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                    }
                    context.startActivity(intent)
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Data Management
            SectionTitle("Data Management")

            SettingsItem(
                icon = Icons.Default.CleaningServices,
                title = "Clear History",
                subtitle = state.lastCleanup ?: "Remove old monitoring data",
                onClick = { showCleanupDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle("Privacy & Experience")

            SettingToggleItem(
                icon = Icons.Default.Lock,
                title = "On-device only mode",
                subtitle = "Keep all analysis local and disable sharing",
                checked = state.onDeviceOnly,
                onCheckedChange = { viewModel.setOnDeviceOnly(it) }
            )

            SettingToggleItem(
                icon = Icons.Default.Info,
                title = "Beginner mode",
                subtitle = "Show plain-language explanations and simpler views",
                checked = state.beginnerMode,
                onCheckedChange = { viewModel.setBeginnerMode(it) }
            )

            SettingToggleItem(
                icon = Icons.Default.Search,
                title = "Remember search filters",
                subtitle = "Persist App List and Global Search queries across restarts",
                checked = state.searchQueryPersistence,
                onCheckedChange = {
                    viewModel.setSearchQueryPersistence(it)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (it) {
                                "Search memory enabled"
                            } else {
                                "Search memory disabled and saved filters cleared"
                            }
                        )
                    }
                }
            )

            SettingsItem(
                icon = Icons.Default.Search,
                title = "Reset search defaults",
                subtitle = "Clear saved App List/Global Search filters and re-enable memory",
                onClick = {
                    viewModel.resetSearchDefaults()
                    scope.launch { snackbarHostState.showSnackbar("Search defaults reset") }
                }
            )

            Text(
                text = "Default Insights Period",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UsageTimeRange.entries.forEach { range ->
                    FilterChip(
                        selected = state.defaultUsageRange == range,
                        onClick = { viewModel.setDefaultUsageRange(range) },
                        label = { Text(range.label) }
                    )
                }
            }

            Text(
                text = "High Risk Threshold",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(40, 45, 50, 60).forEach { threshold ->
                    FilterChip(
                        selected = state.highRiskThreshold == threshold,
                        onClick = { viewModel.setHighRiskThreshold(threshold) },
                        label = { Text("$threshold+") }
                    )
                }
            }

            Text(
                text = "Heavy Background Threshold",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1, 2, 3, 4).forEach { hours ->
                    FilterChip(
                        selected = state.backgroundHeavyHours == hours,
                        onClick = { viewModel.setBackgroundHeavyHours(hours) },
                        label = { Text("$hours h") }
                    )
                }
            }

            Text(
                text = "DNS Leak Sensitivity",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    1 to "Low",
                    2 to "Balanced",
                    3 to "High"
                ).forEach { (level, label) ->
                    FilterChip(
                        selected = state.dnsLeakSensitivity == level,
                        onClick = { viewModel.setDnsLeakSensitivity(level) },
                        label = { Text(label) }
                    )
                }
            }
            Text(
                text = "Controls how aggressively resolver drift and unattributed DNS are flagged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle("Custom Rules")

            SettingsItem(
                icon = Icons.AutoMirrored.Filled.Rule,
                title = "Add Rule",
                subtitle = "Create local alert logic for risk, permissions, usage, or mobile data",
                onClick = { showAddRuleDialog = true }
            )

            if (state.customRules.isEmpty()) {
                Text(
                    text = "No custom rules yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.customRules.forEach { rule ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rule.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${rule.metric} ${rule.comparator} ${rule.threshold} • ${rule.severity}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { viewModel.setRuleEnabled(rule.id, it) }
                                )
                            }
                            TextButton(onClick = { viewModel.deleteRule(rule.id) }) {
                                Text("Delete Rule")
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle("Export")

            SettingsItem(
                icon = Icons.Default.Info,
                title = "Export CSV",
                subtitle = if (state.onDeviceOnly) {
                    "Disabled in On-device only mode"
                } else {
                    "Export app security summary as CSV"
                },
                enabled = !state.onDeviceOnly,
                onClick = { viewModel.exportCsv() }
            )

            SettingsItem(
                icon = Icons.Default.Info,
                title = "Export JSON",
                subtitle = if (state.onDeviceOnly) {
                    "Disabled in On-device only mode"
                } else {
                    "Export app security summary as JSON"
                },
                enabled = !state.onDeviceOnly,
                onClick = { viewModel.exportJson() }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // About
            SectionTitle("About")
            SettingsItem(
                icon = Icons.Default.Info,
                title = "AppTracker v1.1.0",
                subtitle = "Deep permission inspection & battery analysis tool",
                onClick = { }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "How Deep Can This Go?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "On a non-rooted device, AppTracker accesses ~80% of available " +
                                "system data: all declared permissions, runtime permission usage " +
                                "via App Ops, battery usage breakdown, and per-app network " +
                                "statistics. The remaining 20% (kernel-level wakelocks, " +
                                "sub-component power draw) requires root access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    } // end Scaffold content

    if (showCleanupDialog) {
        AlertDialog(
            onDismissRequest = { showCleanupDialog = false },
            title = { Text("Clear History") },
            text = { Text("Choose how much history to keep:") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory(7)
                    showCleanupDialog = false
                    scope.launch { snackbarHostState.showSnackbar("History cleared — keeping last 7 days") }
                }) { Text("Keep 7 days") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.clearHistory(30)
                    showCleanupDialog = false
                    scope.launch { snackbarHostState.showSnackbar("History cleared — keeping last 30 days") }
                }) { Text("Keep 30 days") }
            }
        )
    }

    if (showAddRuleDialog) {
        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = { Text("Add Custom Rule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ruleName,
                        onValueChange = { ruleName = it },
                        label = { Text("Rule Name") },
                        singleLine = true
                    )

                    Text("Metric", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsViewModel.metricOptions.forEach { metric ->
                            FilterChip(
                                selected = ruleMetric == metric,
                                onClick = { ruleMetric = metric },
                                label = { Text(metricLabel(metric)) }
                            )
                        }
                    }

                    Text("Comparator", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsViewModel.comparatorOptions.forEach { comparator ->
                            FilterChip(
                                selected = ruleComparator == comparator,
                                onClick = { ruleComparator = comparator },
                                label = { Text(comparator) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = ruleThreshold,
                        onValueChange = { ruleThreshold = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) },
                        label = { Text("Threshold") },
                        singleLine = true
                    )

                    Text("Severity", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsViewModel.severityOptions.forEach { severity ->
                            FilterChip(
                                selected = ruleSeverity == severity,
                                onClick = { ruleSeverity = severity },
                                label = { Text(severity) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val threshold = ruleThreshold.toFloatOrNull()
                    if (!ruleName.isBlank() && threshold != null) {
                        viewModel.createCustomRule(
                            name = ruleName,
                            metric = ruleMetric,
                            comparator = ruleComparator,
                            threshold = threshold,
                            severity = ruleSeverity
                        )
                        ruleName = ""
                        ruleMetric = SettingsViewModel.metricOptions.first()
                        ruleComparator = SettingsViewModel.comparatorOptions.first()
                        ruleThreshold = "70"
                        ruleSeverity = SettingsViewModel.severityOptions[2]
                        showAddRuleDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun metricLabel(metric: String): String = when (metric) {
    "risk_score" -> "Risk Score"
    "dangerous_permissions" -> "Dangerous Permissions"
    "background_hours" -> "Background Hours"
    "mobile_mb" -> "Mobile Data MB"
    else -> metric
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
