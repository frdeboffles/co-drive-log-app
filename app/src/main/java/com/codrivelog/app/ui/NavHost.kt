package com.codrivelog.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.codrivelog.app.onboarding.OnboardingScreen
import com.codrivelog.app.ui.active.ActiveDriveScreen
import com.codrivelog.app.ui.dashboard.DashboardScreen
import com.codrivelog.app.ui.entry.ManualEntryScreen
import com.codrivelog.app.ui.export.ExportScreen
import com.codrivelog.app.ui.history.DriveHistoryScreen
import com.codrivelog.app.ui.supervisor.SupervisorManagementScreen

/** Top-level navigation destinations. */
sealed class Screen(val route: String) {
    data object Onboarding  : Screen("onboarding")
    data object Dashboard   : Screen("dashboard")
    data object ActiveDrive : Screen("active_drive")
    data object Entry       : Screen("entry")
    data object History     : Screen("history")
    data object Supervisors : Screen("supervisors")
    data object Export      : Screen("export")
}

/**
 * Root navigation host wiring all top-level screens together.
 *
 * If [showOnboarding] is `true` the graph starts at [Screen.Onboarding];
 * otherwise it starts at [Screen.Dashboard].  The onboarding screen pops
 * itself and navigates to Dashboard once complete.
 *
 * Export callbacks are provided by [MainActivity] so that file-saving and
 * intent-launching happen in an Activity context, keeping this composable
 * fully testable without a real Android runtime.
 *
 * @param showOnboarding `true` on first launch (DataStore flag not yet set).
 * @param onExportPdf    Invoked when the user confirms PDF export.
 * @param onExportCsv    Invoked when the user confirms CSV export.
 */
@Composable
fun CoDriveLogNavHost(
    showOnboarding: Boolean = false,
    onExportPdf:    () -> Unit = {},
    onExportCsv:    () -> Unit = {},
) {
    val navController    = rememberNavController()
    val startDestination = if (showOnboarding) Screen.Onboarding.route
                           else Screen.Dashboard.route

    NavHost(
        navController    = navController,
        startDestination = startDestination,
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onAddEntry    = { navController.navigate(Screen.Entry.route) },
                onExport      = { navController.navigate(Screen.Export.route) },
                onViewHistory = { navController.navigate(Screen.History.route) },
                onSupervisors = { navController.navigate(Screen.Supervisors.route) },
            )
        }
        composable(Screen.ActiveDrive.route) {
            ActiveDriveScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Entry.route) {
            ManualEntryScreen(
                onBack  = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(Screen.History.route) {
            DriveHistoryScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Supervisors.route) {
            SupervisorManagementScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Export.route) {
            ExportScreen(
                onBack      = { navController.popBackStack() },
                onExportPdf = onExportPdf,
                onExportCsv = onExportCsv,
            )
        }
    }
}
