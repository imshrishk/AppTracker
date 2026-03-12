package com.apptracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apptracker.data.model.PermissionDetail
import com.apptracker.data.model.ProtectionLevel
import com.apptracker.data.util.PermissionDescriptions
import com.apptracker.ui.theme.Denied
import com.apptracker.ui.theme.Granted
import com.apptracker.ui.theme.RiskHigh
import com.apptracker.ui.theme.RiskMedium

@Composable
fun PermissionCard(
    permission: PermissionDetail,
    modifier: Modifier = Modifier,
    showDetails: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val permInfo = remember(permission.permissionName) {
        PermissionDescriptions.getInfo(permission.permissionName)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = permInfo?.title
                            ?: permission.label
                            ?: permission.permissionName.substringAfterLast("."),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = permission.permissionName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProtectionLevelBadge(permission.protectionLevel)
                    StatusIcon(permission.isGranted)
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(2.dp))

                    // Rich description from our utility
                    if (permInfo != null) {
                        Text(
                            text = permInfo.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Privacy Concern: ${permInfo.concern}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                permInfo.concern.startsWith("CRITICAL") -> RiskHigh
                                permInfo.concern.startsWith("HIGH") -> RiskMedium
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    } else if (permission.description != null) {
                        Text(
                            text = permission.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (permission.group != null) {
                        Text(
                            text = "Group: ${permission.group.substringAfterLast(".")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "Status: ${if (permission.isGranted) "GRANTED" else "DENIED"}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (permission.isGranted) Granted else Denied
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtectionLevelBadge(level: ProtectionLevel) {
    val (text, color) = when (level) {
        ProtectionLevel.DANGEROUS -> "Dangerous" to RiskHigh
        ProtectionLevel.SIGNATURE -> "Signature" to RiskMedium
        ProtectionLevel.NORMAL -> "Normal" to Granted
        ProtectionLevel.SIGNATURE_OR_SYSTEM -> "System" to RiskMedium
        ProtectionLevel.INTERNAL -> "Internal" to MaterialTheme.colorScheme.onSurfaceVariant
        ProtectionLevel.UNKNOWN -> "Unknown" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun StatusIcon(isGranted: Boolean) {
    Icon(
        imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
        contentDescription = if (isGranted) "Granted" else "Denied",
        tint = if (isGranted) Granted else Denied
    )
}
