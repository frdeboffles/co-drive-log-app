package com.codrivelog.app.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.codrivelog.app.BuildConfig
import com.codrivelog.app.R
import com.codrivelog.app.ui.theme.CoDriveLogTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttributionsScreen(
    onBack: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_attributions)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_close))
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.attributions_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                AppDetailsCard(
                    appName = stringResource(R.string.app_name),
                    versionName = BuildConfig.VERSION_NAME,
                    authorName = stringResource(R.string.app_author_name),
                )
            }

            items(attributions) { attribution ->
                AttributionCard(attribution)
            }
        }
    }
}

@Composable
private fun AppDetailsCard(
    appName: String,
    versionName: String,
    authorName: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.label_about_app),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = appName,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.label_app_version_value, versionName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.label_app_author_value, authorName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AttributionCard(
    attribution: Attribution,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = attribution.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = attribution.license,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = attribution.purpose,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = attribution.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class Attribution(
    val name: String,
    val license: String,
    val purpose: String,
    val url: String,
)

private val attributions = listOf(
    Attribution(
        name = "MapLibre Android SDK",
        license = "BSD 3-Clause",
        purpose = "In-app vector map rendering for route viewing.",
        url = "https://github.com/maplibre/maplibre-native",
    ),
    Attribution(
        name = "OpenFreeMap",
        license = "OpenStreetMap data (ODbL)",
        purpose = "Hosted vector tile styles (Positron/Liberty/Bright).",
        url = "https://openfreemap.org",
    ),
    Attribution(
        name = "OpenStreetMap",
        license = "ODbL 1.0",
        purpose = "Base geospatial data for map tiles.",
        url = "https://www.openstreetmap.org/copyright",
    ),
    Attribution(
        name = "AndroidX Room",
        license = "Apache 2.0",
        purpose = "Local persistence layer for drive and route data.",
        url = "https://developer.android.com/jetpack/androidx/releases/room",
    ),
    Attribution(
        name = "Hilt (Dagger)",
        license = "Apache 2.0",
        purpose = "Dependency injection.",
        url = "https://github.com/google/dagger",
    ),
    Attribution(
        name = "PDFBox-Android",
        license = "Apache 2.0",
        purpose = "PDF generation and form filling for DR 2324 exports.",
        url = "https://github.com/TomRoush/PdfBox-Android",
    ),
)

@Preview(showBackground = true)
@Composable
private fun AttributionsScreenPreview() {
    CoDriveLogTheme {
        AttributionsScreen()
    }
}
