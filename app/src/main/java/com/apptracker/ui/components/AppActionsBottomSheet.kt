package com.apptracker.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apptracker.data.model.AppInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionsBottomSheet(
    app: AppInfo,
    isWatched: Boolean,
    onDismiss: () -> Unit,
    onViewDetails: () -> Unit,
    onToggleWatch: () -> Unit,
    onShareReport: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(icon = app.icon, contentDescription = app.appName, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Risk Score: ${app.riskScore}",
                        style = MaterialTheme.typography.bodySmall,
                        color = riskColor(app.riskScore)
                    )
                }
            }

            HorizontalDivider()

            ActionItem(
                icon = Icons.Default.Info,
                label = "View Details",
                onClick = { onDismiss(); onViewDetails() }
            )

            ActionItem(
                icon = if (isWatched) Icons.Default.Star else Icons.Default.StarBorder,
                label = if (isWatched) "Remove from Watchlist" else "Add to Watchlist",
                onClick = { onToggleWatch(); onDismiss() }
            )

            ActionItem(
                icon = Icons.Default.Share,
                label = "Share Report",
                onClick = { onDismiss(); onShareReport() }
            )

            ActionItem(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                label = "Open App Settings",
                onClick = {
                    onDismiss()
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${app.packageName}")
                        }
                    )
                }
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
