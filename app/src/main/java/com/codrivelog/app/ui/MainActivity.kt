package com.codrivelog.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.codrivelog.app.ui.theme.CoDriveLogTheme

/**
 * Single activity host for the entire Compose navigation graph.
 * Annotated with [AndroidEntryPoint] to enable Hilt injection.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CoDriveLogTheme {
                CoDriveLogNavHost()
            }
        }
    }
}
