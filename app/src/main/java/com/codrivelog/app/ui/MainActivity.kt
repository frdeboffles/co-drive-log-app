package com.codrivelog.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            // Don't render until we know whether onboarding is needed
            if (!uiState.ready) return@setContent

            CoDriveLogTheme {
                CoDriveLogNavHost(
                    showOnboarding = uiState.showOnboarding,
                    onExportPdf    = { exportPdf(uiState.studentName) },
                    onExportCsv    = { exportCsv(uiState.studentName) },
                )
            }
        }
    }

    private fun exportPdf(studentName: String) {
        lifecycleScope.launch {
            val uri = exportManager.exportPdf(studentName)
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
}
