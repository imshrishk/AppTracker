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
import com.apptracker.ui.screens.filemanager.FileManagerScreen
import com.apptracker.ui.screens.remediation.RemediationScreen
import com.apptracker.ui.screens.settings.SettingsScreen
import com.apptracker.ui.screens.timeline.TimelineScreen
import com.apptracker.ui.screens.dnsactivity.DnsActivityScreen
import com.apptracker.ui.screens.globalsearch.GlobalSearchScreen
import com.apptracker.ui.screens.networkmap.AppNetworkMapScreen

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
                    },
                    onOpenRemediation = {
                        navController.navigate(Screen.Remediation.route)
                        },
                        onOpenDnsActivity = {
                            navController.navigate(Screen.DnsActivity.route)
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

            composable(Screen.FileManager.route) {
                FileManagerScreen()
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

            composable(Screen.Remediation.route) {
                RemediationScreen(
                    onBack = { navController.popBackStack() },
                    onOpenApp = { packageName ->
                        navController.navigate(Screen.AppDetail.createRoute(packageName))
                    }
                )
            }

                composable(Screen.DnsActivity.route) {
                    DnsActivityScreen(onBack = { navController.popBackStack() })
                }

            composable(Screen.GlobalSearch.route) {
                GlobalSearchScreen(
                    onAppClick = { pkg -> navController.navigate(Screen.AppDetail.createRoute(pkg)) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.NetworkMap.route) {
                AppNetworkMapScreen(
                    onBack = { navController.popBackStack() },
                    onAppClick = { pkg -> navController.navigate(Screen.AppDetail.createRoute(pkg)) }
                )
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
    if (currentRoute?.startsWith("remediation") == true) return
    if (currentRoute?.startsWith("dns_activity") == true) return
    if (currentRoute?.startsWith("global_search") == true) return
    if (currentRoute?.startsWith("network_map") == true) return

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
