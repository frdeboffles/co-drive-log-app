package com.codrivelog.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.codrivelog.app.ui.dashboard.DashboardScreen

/** Top-level navigation destinations. */
sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Entry    : Screen("entry")
    data object Export   : Screen("export")
}

/**
 * Root navigation host wiring all top-level screens together.
 * Start destination is [Screen.Dashboard].
 */
@Composable
fun CoDriveLogNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onAddEntry = { navController.navigate(Screen.Entry.route) },
                onExport   = { navController.navigate(Screen.Export.route) },
            )
        }
        composable(Screen.Entry.route) {
            // Placeholder — will be implemented in a future iteration
            PlaceholderScreen(title = "Add Entry")
        }
        composable(Screen.Export.route) {
            // Placeholder — will be implemented in a future iteration
            PlaceholderScreen(title = "Export")
        }
    }
}
