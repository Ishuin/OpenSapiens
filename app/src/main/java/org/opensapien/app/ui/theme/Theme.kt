package org.opensapien.app.ui.theme

import android.app.Activity
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = EmberDark,
    onPrimary = Color(0xFF1A0E04),
    primaryContainer = Color(0xFF3A2410),
    onPrimaryContainer = EmberDark,
    secondary = OnSurfaceVariantDark,
    onSecondary = BackgroundDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnBackgroundDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
    error = SignalDark,
    onError = Color(0xFF1A0504),
    errorContainer = Color(0xFF3A1512),
    onErrorContainer = SignalDark,
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = EmberLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFBE6D4),
    onPrimaryContainer = Color(0xFF5C2A08),
    secondary = OnSurfaceVariantLight,
    onSecondary = Color(0xFFFFFFFF),
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnBackgroundLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineLight,
    error = SignalLight,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBDDDA),
    onErrorContainer = Color(0xFF6B1810),
)

/** Semantic colours Material 3 has no slot for. */
data class OpenSapienColors(val success: Color)

val LocalOpenSapienColors = staticCompositionLocalOf { OpenSapienColors(SuccessDark) }

/**
 * True when the user has switched animations off in system accessibility settings.
 * Screens read this to fall back to static equivalents instead of moving parts.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun OpenSapienTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val extra = OpenSapienColors(success = if (darkTheme) SuccessDark else SuccessLight)

    val context = LocalContext.current
    val reduceMotion = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalOpenSapienColors provides extra,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = OpenSapienTypography,
            content = content,
        )
    }
}
