package com.apptracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.apptracker.util.OnboardingPreferences

class AppTrackerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val score = OnboardingPreferences.getLastHealthScore(context).coerceIn(0, 100)
        provideContent {
            GlanceTheme {
                WidgetContent(score = score)
            }
        }
    }
}

@Composable
private fun WidgetContent(score: Int) {
    val (grade, bgColor) = when {
        score >= 90 -> "A" to Color(0xFF1B5E20)
        score >= 75 -> "B" to Color(0xFF2E7D32)
        score >= 60 -> "C" to Color(0xFFF57F17)
        score >= 40 -> "D" to Color(0xFFE65100)
        else        -> "F" to Color(0xFFB71C1C)
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = grade,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            )
            Text(
                text = "Health: $score/100",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            )
            Text(
                text = "AppTracker",
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

class AppTrackerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AppTrackerWidget()
}
