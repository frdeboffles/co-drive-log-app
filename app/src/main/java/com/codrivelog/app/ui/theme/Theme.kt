package com.codrivelog.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme()
private val DarkColorScheme  = darkColorScheme()

/**
 * Application-wide Material 3 theme.
 *
 * On Android 12+ (API 31) dynamic color is used; older devices fall back to
 * the default Material 3 baseline palette.
 *
 * @param darkTheme    Whether to apply the dark variant.
 * @param dynamicColor Whether to use dynamic (wallpaper-sourced) colors.
 * @param content      Composable content displayed within the theme.
 */
@Composable
fun CoDriveLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else           dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography   = Typography(),
        content      = content,
    )
}
