package com.apptracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.apptracker.ui.screens.appcompare.AppCompareScreen
import com.apptracker.ui.screens.appdetail.AppDetailScreen
import com.apptracker.ui.screens.applist.AppListScreen
import com.apptracker.ui.screens.dashboard.DashboardScreen
import com.apptracker.ui.screens.settings.SettingsScreen
import com.apptracker.ui.screens.timeline.TimelineScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onAppClick = { packageName ->
                        navController.navigate(Screen.AppDetail.createRoute(packageName))
                    },
                    onViewAllClick = {
                        navController.navigate(Screen.AppList.route)
                    }
                )
            }

            composable(Screen.AppList.route) {
                AppListScreen(
                    onAppClick = { packageName ->
                        navController.navigate(Screen.AppDetail.createRoute(packageName))
                    }
                )
            }

            composable(
                route = Screen.AppDetail.route,
                arguments = listOf(
                    navArgument("packageName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString("packageName") ?: return@composable
                AppDetailScreen(
                    packageName = packageName,
                    onBack = { navController.popBackStack() },
                    onCompare = { pkgA, pkgB ->
                        navController.navigate(Screen.AppCompare.createRoute(pkgA, pkgB))
                    }
                )
            }

            composable(
                route = Screen.AppCompare.route,
                arguments = listOf(
                    navArgument("packageA") { type = NavType.StringType },
                    navArgument("packageB") { type = NavType.StringType }
                )
            ) {
                AppCompareScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Timeline.route) {
                TimelineScreen(
                    onAppClick = { packageName ->
                        navController.navigate(Screen.AppDetail.createRoute(packageName))
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}

@Composable
private fun BottomNavigationBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Hide bottom bar on detail screens
    if (currentRoute?.startsWith("app_detail") == true) return
    if (currentRoute?.startsWith("app_compare") == true) return

    NavigationBar {
        bottomNavItems.forEach { screen ->
            NavigationBarItem(
                icon = {
                    screen.icon?.let { Icon(imageVector = it, contentDescription = screen.title) }
                },
                label = { Text(screen.title) },
                selected = currentRoute == screen.route,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}
