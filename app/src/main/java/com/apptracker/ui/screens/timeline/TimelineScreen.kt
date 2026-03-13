package com.apptracker.ui.screens.timeline

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apptracker.data.db.entity.AppOpsHistoryEntity
import com.apptracker.data.model.UsageTimeRange
import com.apptracker.ui.theme.CategoryColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onAppClick: (String) -> Unit,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showCustomPeriodDialog by remember { mutableStateOf(false) }
    var customPeriodText by remember { mutableStateOf(state.customPeriodDays?.toString() ?: "14") }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Activity Timeline") })

        // Filter chips
        LazyRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(TimelineFilter.entries.toList()) { filter ->
                FilterChip(
                    selected = state.selectedFilter == filter,
                    onClick = { viewModel.onFilterChange(filter) },
                    label = { Text(filter.label) },
                    leadingIcon = {
                        Icon(
                            imageVector = filterIcon(filter),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }

        LazyRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(UsageTimeRange.entries.toList()) { period ->
                FilterChip(
                    selected = state.customPeriodDays == null && state.selectedPeriod == period,
                    onClick = { viewModel.onPeriodChange(period) },
                    label = { Text(period.label) }
                )
            }
            item {
                FilterChip(
                    selected = state.customPeriodDays != null,
                    onClick = { showCustomPeriodDialog = true },
                    label = { Text(state.customPeriodDays?.let { "Custom ${it}d" } ?: "Custom") }
                )
            }
        }

        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.filteredEntries.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No activity recorded yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Activity will appear here as apps are monitored",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.filteredEntries) { entry ->
                        TimelineEntry(
                            entry = entry,
                            onClick = { onAppClick(entry.packageName) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (showCustomPeriodDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCustomPeriodDialog = false },
            title = { Text("Custom Timeline Period") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Show timeline entries from the last N days (1-365).")
                    OutlinedTextField(
                        value = customPeriodText,
                        onValueChange = { customPeriodText = it.filter { ch -> ch.isDigit() }.take(3) },
                        singleLine = true,
                        label = { Text("Days") }
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val days = customPeriodText.toIntOrNull()
                    if (days != null && days in 1..365) {
                        viewModel.onCustomPeriodDays(days)
                        showCustomPeriodDialog = false
                    }
                }) { Text("Apply") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showCustomPeriodDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TimelineEntry(
    entry: AppOpsHistoryEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline dot and line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(getOpColor(entry.opName))
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Card(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = entry.packageName.substringAfterLast("."),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = entry.mode,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (entry.mode == "ALLOWED")
                            com.apptracker.ui.theme.Granted
                        else com.apptracker.ui.theme.Denied,
                        modifier = Modifier
                            .background(
                                (if (entry.mode == "ALLOWED")
                                    com.apptracker.ui.theme.Granted
                                else com.apptracker.ui.theme.Denied).copy(alpha = 0.1f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = entry.opName.removePrefix("android:"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (entry.lastAccessTime > 0) {
                    Text(
                        text = formatTimestamp(entry.lastAccessTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

private fun filterIcon(filter: TimelineFilter): ImageVector = when (filter) {
    TimelineFilter.ALL -> Icons.Default.MoreHoriz
    TimelineFilter.LOCATION -> Icons.Default.LocationOn
    TimelineFilter.CAMERA -> Icons.Default.Camera
    TimelineFilter.MICROPHONE -> Icons.Default.Mic
    TimelineFilter.CONTACTS -> Icons.Default.Contacts
    TimelineFilter.STORAGE -> Icons.Default.FolderOpen
}

private fun getOpColor(opName: String) = when {
    opName.contains("LOCATION", ignoreCase = true) -> CategoryColors[0]
    opName.contains("CAMERA", ignoreCase = true) -> CategoryColors[1]
    opName.contains("AUDIO", ignoreCase = true) ||
            opName.contains("RECORD", ignoreCase = true) -> CategoryColors[2]
    opName.contains("CONTACT", ignoreCase = true) -> CategoryColors[3]
    opName.contains("STORAGE", ignoreCase = true) -> CategoryColors[4]
    else -> CategoryColors[8]
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
