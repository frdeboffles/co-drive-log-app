package com.codrivelog.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

// ---- Custom baseline palette (Colorado green + sky blue) ----
// Used on devices below Android 12 that don't support dynamic color.

private val md_primary       = Color(0xFF1B6E3E)  // Colorado pine green
private val md_on_primary    = Color(0xFFFFFFFF)
private val md_primary_cont  = Color(0xFFB3EDCA)
private val md_on_prim_cont  = Color(0xFF002112)

private val md_secondary     = Color(0xFF1565C0)  // Rocky Mountain sky blue
private val md_on_secondary  = Color(0xFFFFFFFF)
private val md_sec_cont      = Color(0xFFD5E4FF)
private val md_on_sec_cont   = Color(0xFF001B3D)

private val md_tertiary      = Color(0xFF6B4C11)  // Golden plains amber
private val md_on_tertiary   = Color(0xFFFFFFFF)
private val md_tert_cont     = Color(0xFFFFDFA7)
private val md_on_tert_cont  = Color(0xFF231B00)

private val md_error         = Color(0xFFB3261E)
private val md_on_error      = Color(0xFFFFFFFF)
private val md_error_cont    = Color(0xFFF9DEDC)
private val md_on_error_cont = Color(0xFF410E0B)

private val md_background    = Color(0xFFFBFDF8)
private val md_on_background = Color(0xFF191C1A)
private val md_surface       = Color(0xFFFBFDF8)
private val md_on_surface    = Color(0xFF191C1A)
private val md_surface_var   = Color(0xFFDCE5DC)
private val md_on_surf_var   = Color(0xFF404942)
private val md_outline       = Color(0xFF707972)

private val LightColorScheme = lightColorScheme(
    primary             = md_primary,
    onPrimary           = md_on_primary,
    primaryContainer    = md_primary_cont,
    onPrimaryContainer  = md_on_prim_cont,
    secondary           = md_secondary,
    onSecondary         = md_on_secondary,
    secondaryContainer  = md_sec_cont,
    onSecondaryContainer = md_on_sec_cont,
    tertiary            = md_tertiary,
    onTertiary          = md_on_tertiary,
    tertiaryContainer   = md_tert_cont,
    onTertiaryContainer = md_on_tert_cont,
    error               = md_error,
    onError             = md_on_error,
    errorContainer      = md_error_cont,
    onErrorContainer    = md_on_error_cont,
    background          = md_background,
    onBackground        = md_on_background,
    surface             = md_surface,
    onSurface           = md_on_surface,
    surfaceVariant      = md_surface_var,
    onSurfaceVariant    = md_on_surf_var,
    outline             = md_outline,
)

// Dark variants — inverted lightness while keeping the same hues
private val DarkColorScheme = darkColorScheme(
    primary             = Color(0xFF88D6A8),
    onPrimary           = Color(0xFF00391D),
    primaryContainer    = Color(0xFF00532C),
    onPrimaryContainer  = md_primary_cont,
    secondary           = Color(0xFFA8C8FF),
    onSecondary         = Color(0xFF003063),
    secondaryContainer  = Color(0xFF004490),
    onSecondaryContainer = md_sec_cont,
    tertiary            = Color(0xFFEDC06D),
    onTertiary          = Color(0xFF3A2A00),
    tertiaryContainer   = Color(0xFF533D00),
    onTertiaryContainer = md_tert_cont,
    error               = Color(0xFFF2B8B5),
    onError             = Color(0xFF601410),
    errorContainer      = Color(0xFF8C1D18),
    onErrorContainer    = md_error_cont,
    background          = Color(0xFF191C1A),
    onBackground        = Color(0xFFE1E3DF),
    surface             = Color(0xFF191C1A),
    onSurface           = Color(0xFFE1E3DF),
    surfaceVariant      = md_on_surf_var,
    onSurfaceVariant    = Color(0xFFBFC9C0),
    outline             = Color(0xFF8A938B),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Application-wide Material 3 theme.
 *
 * On Android 12+ (API 31) dynamic color is used; older devices fall back to
 * a custom Colorado-inspired palette (pine green + sky blue).
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
        typography  = Typography(),
        shapes      = AppShapes,
        content     = content,
    )
}
