package com.apptracker.ui.screens.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.apptracker.util.BaselineSummary
import com.apptracker.util.OnboardingPreferences
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val actionLabel: String? = null
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Default.Search,
        title = "Welcome to AppTracker",
        description = "AppTracker helps you understand what your installed apps are really doing — " +
                "monitoring permissions, battery use, network activity, and privacy risks in one place."
    ),
    OnboardingPage(
        icon = Icons.Default.Lock,
        title = "Grant Usage Access",
        description = "To analyze battery usage and app activity, AppTracker needs Usage Access permission. " +
                "Tap the button below to open Settings and enable it for AppTracker.",
        actionLabel = "Open Usage Access Settings"
    ),
    OnboardingPage(
        icon = Icons.Default.Analytics,
        title = "Your Privacy Dashboard",
        description = "Get a risk score for every app, see which permissions are granted, " +
                "watch suspicious apps, and compare apps side-by-side. " +
                "All analysis stays on your device and no cloud upload is used.",
        actionLabel = "Get Started"
    ),
    OnboardingPage(
        icon = Icons.Default.Security,
        title = "Your Risk Snapshot",
        description = "Here is a quick overview of AppTracker's findings so far.",
        actionLabel = "Go to Dashboard"
    )
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val baselineSummary by OnboardingPreferences.baselineSummary(context).collectAsState(initial = null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { pageIndex ->
            val page = pages[pageIndex]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (pageIndex == pages.lastIndex) {
                    RiskSnapshotPage(
                        baselineSummary = baselineSummary,
                        actionLabel = page.actionLabel ?: "Go to Dashboard",
                        onFinished = {
                            scope.launch {
                                OnboardingPreferences.markOnboardingDone(context)
                                onFinished()
                            }
                        }
                    )
                } else {
                    Icon(
                        imageVector = page.icon,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = page.description,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(32.dp))

                    if (page.actionLabel != null) {
                        if (pageIndex == pages.size - 2) {
                            Button(
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(pageIndex + 1) }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(page.actionLabel)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(page.actionLabel)
                            }
                        }
                    }
                }
            }
        }

        // Page indicator dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            repeat(pages.size) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }

        // Navigation row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = pagerState.currentPage > 0,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }
                ) { Text("Back") }
            }

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(visible = pagerState.currentPage < pages.lastIndex) {
                Button(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                ) { Text("Next") }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RiskSnapshotPage(
    baselineSummary: BaselineSummary?,
    actionLabel: String,
    onFinished: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Your Risk Snapshot",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))

        if (baselineSummary != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SnapshotRow("Apps scanned", "${baselineSummary.appCount}")
                    SnapshotRow("Device health score", "${baselineSummary.healthScore} / 100")
                    SnapshotRow("High-risk apps", "${baselineSummary.highRiskCount}")
                    SnapshotRow("Dangerous permissions", "${baselineSummary.dangerousPermissionCount}")
                }
            }
        } else {
            Text(
                text = "Scan results will appear here after AppTracker completes its first background scan.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onFinished,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(actionLabel)
        }
    }
}

@Composable
private fun SnapshotRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
