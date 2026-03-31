package com.codrivelog.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.codrivelog.app.export.ExportManager
import com.codrivelog.app.ui.theme.CoDriveLogTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single activity host for the entire Compose navigation graph.
 * Annotated with [AndroidEntryPoint] to enable Hilt injection.
 *
 * Responsibilities:
 * - Reads the DataStore onboarding flag via [MainViewModel] and passes
 *   [showOnboarding] to [CoDriveLogNavHost] so the correct start destination
 *   is chosen without a blank flash.
 * - Handles file export via [ExportManager] and launches the resulting
 *   ACTION_VIEW intent so [ExportScreen] stays testable.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var exportManager: ExportManager

    private val viewModel: MainViewModel by viewModels()

    /** Launcher that requests location + notification permissions on first launch. */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results handled silently; service guards itself via hasLocationPermission() */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestRuntimePermissions()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            // Don't render until we know whether onboarding is needed
            if (!uiState.ready) return@setContent

            CoDriveLogTheme {
                CoDriveLogNavHost(
                    showOnboarding = uiState.showOnboarding,
                    onExportPdf    = { signatureName, signatureDate ->
                        exportPdf(
                            studentName = uiState.studentName,
                            permitNumber = uiState.permitNumber,
                            signatureName = signatureName,
                            signatureDate = signatureDate,
                        )
                    },
                    onExportCsv    = { exportCsv(uiState.studentName) },
                    onExportGeoJson = { exportGeoJson() },
                )
            }
        }
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun exportPdf(
        studentName: String,
        permitNumber: String,
        signatureName: String,
        signatureDate: String,
    ) {
        lifecycleScope.launch {
            val uri = exportManager.exportPdf(
                studentName = studentName,
                permitNumber = permitNumber,
                signatureName = signatureName,
                signatureDate = signatureDate,
            )
            if (uri != null) {
                startActivity(
                    android.content.Intent.createChooser(
                        exportManager.buildOpenIntent(uri, "application/pdf"),
                        "Open PDF with…",
                    )
                )
            } else {
                Toast.makeText(this@MainActivity, "No drive sessions to export.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportCsv(studentName: String) {
        lifecycleScope.launch {
            val uri = exportManager.exportCsv(studentName)
            if (uri != null) {
                startActivity(
                    android.content.Intent.createChooser(
                        exportManager.buildOpenIntent(uri, "text/csv"),
                        "Open CSV with…",
                    )
                )
            } else {
                Toast.makeText(this@MainActivity, "No drive sessions to export.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportGeoJson() {
        lifecycleScope.launch {
            val uri = exportManager.exportGeoJson()
            if (uri != null) {
                startActivity(
                    android.content.Intent.createChooser(
                        exportManager.buildOpenIntent(uri, "application/geo+json"),
                        "Open GeoJSON with…",
                    )
                )
            } else {
                Toast.makeText(this@MainActivity, "No routes to export.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
