package com.apptracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null
) {
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    data object AppList : Screen("app_list", "Apps", Icons.Default.Apps)
    data object FileManager : Screen("file_manager", "Files", Icons.Default.Folder)
    data object AppDetail : Screen("app_detail/{packageName}", "App Details") {
        fun createRoute(packageName: String) = "app_detail/$packageName"
    }
    data object Timeline : Screen("timeline", "Timeline", Icons.Default.Timeline)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    data object Remediation : Screen("remediation", "Remediation")
    data object AppCompare : Screen("app_compare/{packageA}/{packageB}", "Compare Apps") {
        fun createRoute(packageA: String, packageB: String) = "app_compare/$packageA/$packageB"
    }
        data object DnsActivity : Screen("dns_activity", "DNS Activity")
    data object GlobalSearch : Screen("global_search", "Search")
    data object NetworkMap : Screen("network_map", "Network Map")
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.AppList,
    Screen.FileManager,
    Screen.Timeline,
    Screen.Settings
)
