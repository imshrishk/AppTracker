package com.apptracker.ui.screens.settings

import android.content.Intent
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
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
