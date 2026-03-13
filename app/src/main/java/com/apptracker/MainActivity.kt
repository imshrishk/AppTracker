package com.apptracker

import android.app.AppOpsManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.apptracker.ui.navigation.AppNavigation
import com.apptracker.ui.screens.onboarding.OnboardingScreen
import com.apptracker.ui.theme.AppTrackerTheme
import com.apptracker.util.OnboardingPreferences
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var hasUsageAccess by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkUsageAccess()

        setContent {
            AppTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val onboardingDone by OnboardingPreferences.isOnboardingDone(this@MainActivity)
                        .collectAsState(initial = null)

                    when {
                        onboardingDone == null -> {
                            // Still loading preference — show nothing (prevents flash)
                        }
                        onboardingDone == false -> {
                            OnboardingScreen(
                                onFinished = { /* state update triggers recompose */ }
                            )
                        }
                        hasUsageAccess -> {
                            AppNavigation()
                        }
                        else -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Usage Access Required",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "AppTracker needs Usage Access permission to inspect " +
                                            "battery usage, app activity, and network statistics " +
                                            "of installed apps.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(onClick = {
                                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                }) {
                                    Text("Grant Usage Access")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkUsageAccess()
    }

    private fun checkUsageAccess() {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        hasUsageAccess = mode == AppOpsManager.MODE_ALLOWED
    }
}
