package com.codrivelog.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
 * Export actions are handled here so that:
 *  - [ExportManager] can call [startActivity] on a real Activity context.
 *  - [ExportScreen] / [CoDriveLogNavHost] remain pure Compose and testable.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var exportManager: ExportManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CoDriveLogTheme {
                CoDriveLogNavHost(
                    onExportPdf = { exportPdf() },
                    onExportCsv = { exportCsv() },
                )
            }
        }
    }

    private fun exportPdf() {
        lifecycleScope.launch {
            val uri = exportManager.exportPdf()
            if (uri != null) {
                val intent = exportManager.buildOpenIntent(uri, "application/pdf")
                startActivity(
                    android.content.Intent.createChooser(intent, "Open PDF with…")
                )
            } else {
                Toast.makeText(this@MainActivity, "No drive sessions to export.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportCsv() {
        lifecycleScope.launch {
            val uri = exportManager.exportCsv()
            if (uri != null) {
                val intent = exportManager.buildOpenIntent(uri, "text/csv")
                startActivity(
                    android.content.Intent.createChooser(intent, "Open CSV with…")
                )
            } else {
                Toast.makeText(this@MainActivity, "No drive sessions to export.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
