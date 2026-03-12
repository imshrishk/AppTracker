package com.apptracker.ui.screens.appcompare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apptracker.data.model.AppInfo
import com.apptracker.ui.components.AppIcon
import com.apptracker.ui.components.riskColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCompareScreen(
    onBack: () -> Unit,
    viewModel: AppCompareViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Compare Apps") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
        )

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        if (state.error != null || state.appA == null || state.appB == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    state.error ?: "Apps not found",
                    color = MaterialTheme.colorScheme.error
                )
            }
            return
        }

        val appA = state.appA!!
        val appB = state.appB!!

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { AppHeaderRow(appA, appB) }
            item { HorizontalDivider() }
            item {
                CompareSection(title = "Risk & Security") {
                    CompareRow(
                        label = "Risk Score",
                        valueA = "${appA.riskScore}",
                        valueB = "${appB.riskScore}",
                        colorA = riskColor(appA.riskScore),
                        colorB = riskColor(appB.riskScore),
                        highlightWinner = true,
                        winnerIsLower = true
                    )
                    CompareRow(
                        label = "Dangerous Permissions",
                        valueA = "${appA.permissions.count { it.isDangerous && it.isGranted }}",
                        valueB = "${appB.permissions.count { it.isDangerous && it.isGranted }}",
                        highlightWinner = true,
                        winnerIsLower = true
                    )
                    CompareRow(
                        label = "Total Permissions",
                        valueA = "${appA.permissions.size}",
                        valueB = "${appB.permissions.size}",
                        highlightWinner = true,
                        winnerIsLower = true
                    )
                }
            }
            item {
                CompareSection(title = "Battery Usage") {
                    val aTotal = ((appA.batteryUsage?.foregroundTimeMs ?: 0) +
                            (appA.batteryUsage?.backgroundTimeMs ?: 0)) / 60_000
                    val bTotal = ((appB.batteryUsage?.foregroundTimeMs ?: 0) +
                            (appB.batteryUsage?.backgroundTimeMs ?: 0)) / 60_000
                    CompareRow(
                        label = "Active Time",
                        valueA = if (aTotal > 0) "${aTotal}m" else "—",
                        valueB = if (bTotal > 0) "${bTotal}m" else "—",
                        highlightWinner = true,
                        winnerIsLower = true
                    )
                    val aWakeups = appA.batteryUsage?.alarmWakeups ?: 0
                    val bWakeups = appB.batteryUsage?.alarmWakeups ?: 0
                    CompareRow(
                        label = "Alarm Wakeups",
                        valueA = if (appA.batteryUsage != null) "$aWakeups" else "—",
                        valueB = if (appB.batteryUsage != null) "$bWakeups" else "—",
                        highlightWinner = true,
                        winnerIsLower = true
                    )
                }
            }
            item {
                CompareSection(title = "Network Usage") {
                    val aTx = appA.networkUsage?.totalTxBytes ?: 0L
                    val aRx = appA.networkUsage?.totalRxBytes ?: 0L
                    val bTx = appB.networkUsage?.totalTxBytes ?: 0L
                    val bRx = appB.networkUsage?.totalRxBytes ?: 0L
                    CompareRow(
                        label = "Data Sent",
                        valueA = if (appA.networkUsage != null) formatBytes(aTx) else "—",
                        valueB = if (appB.networkUsage != null) formatBytes(bTx) else "—",
                        highlightWinner = true,
                        winnerIsLower = true
                    )
                    CompareRow(
                        label = "Data Received",
                        valueA = if (appA.networkUsage != null) formatBytes(aRx) else "—",
                        valueB = if (appB.networkUsage != null) formatBytes(bRx) else "—",
                        highlightWinner = true,
                        winnerIsLower = true
                    )
                }
            }
            item {
                CompareSection(title = "App Info") {
                    CompareRow(
                        label = "Version",
                        valueA = appA.versionName ?: "—",
                        valueB = appB.versionName ?: "—"
                    )
                    CompareRow(
                        label = "Target SDK",
                        valueA = "${appA.targetSdkVersion}",
                        valueB = "${appB.targetSdkVersion}",
                        highlightWinner = true,
                        winnerIsLower = false
                    )
                    CompareRow(
                        label = "System App",
                        valueA = if (appA.isSystemApp) "Yes" else "No",
                        valueB = if (appB.isSystemApp) "Yes" else "No"
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AppHeaderRow(appA: AppInfo, appB: AppInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            AppIcon(icon = appA.icon, contentDescription = appA.appName, size = 56.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = appA.appName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = appA.versionName ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "VS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            AppIcon(icon = appB.icon, contentDescription = appB.appName, size = 56.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = appB.appName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = appB.versionName ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompareSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun CompareRow(
    label: String,
    valueA: String,
    valueB: String,
    colorA: Color = MaterialTheme.colorScheme.onSurface,
    colorB: Color = MaterialTheme.colorScheme.onSurface,
    highlightWinner: Boolean = false,
    winnerIsLower: Boolean = true
) {
    val aNum = valueA.filter { it.isDigit() || it == '.' }.toDoubleOrNull()
    val bNum = valueB.filter { it.isDigit() || it == '.' }.toDoubleOrNull()

    val aWins = highlightWinner && aNum != null && bNum != null &&
            if (winnerIsLower) aNum < bNum else aNum > bNum
    val bWins = highlightWinner && aNum != null && bNum != null &&
            if (winnerIsLower) bNum < aNum else bNum > aNum

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = valueA,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (aWins) FontWeight.Bold else FontWeight.Normal,
            color = if (aNum != null && bNum != null) colorA
                    else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.4f),
            textAlign = TextAlign.Center,
            fontStyle = FontStyle.Italic
        )
        Text(
            text = valueB,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bWins) FontWeight.Bold else FontWeight.Normal,
            color = if (aNum != null && bNum != null) colorB
                    else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}
