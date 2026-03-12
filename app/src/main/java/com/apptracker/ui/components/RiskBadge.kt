package com.apptracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.apptracker.domain.model.RiskLevel
import com.apptracker.ui.theme.RiskCritical
import com.apptracker.ui.theme.RiskHigh
import com.apptracker.ui.theme.RiskLow
import com.apptracker.ui.theme.RiskMedium

@Composable
fun RiskBadge(
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier,
    showScore: Boolean = false,
    score: Int = 0
) {
    val backgroundColor = when (riskLevel) {
        RiskLevel.LOW -> RiskLow
        RiskLevel.MEDIUM -> RiskMedium
        RiskLevel.HIGH -> RiskHigh
        RiskLevel.CRITICAL -> RiskCritical
    }

    val text = if (showScore) "${riskLevel.label} ($score)" else riskLevel.label

    Box(
        modifier = modifier
            .background(
                color = backgroundColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = backgroundColor
        )
    }
}

@Composable
fun RiskScoreIndicator(
    score: Int,
    modifier: Modifier = Modifier
) {
    val color = when {
        score >= 70 -> RiskCritical
        score >= 45 -> RiskHigh
        score >= 20 -> RiskMedium
        else -> RiskLow
    }

    Box(
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "$score",
            style = MaterialTheme.typography.titleMedium,
            color = color
        )
    }
}

fun riskLevelFromScore(score: Int): RiskLevel = when {
    score >= 70 -> RiskLevel.CRITICAL
    score >= 45 -> RiskLevel.HIGH
    score >= 20 -> RiskLevel.MEDIUM
    else -> RiskLevel.LOW
}

fun riskColor(score: Int): Color = when {
    score >= 70 -> RiskCritical
    score >= 45 -> RiskHigh
    score >= 20 -> RiskMedium
    else -> RiskLow
}
