package com.megamaced.crate.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Colour roles Material's scheme doesn't define. Kept theme-aware rather than
 * hardcoded at the call site so both light and dark stay legible.
 */
@Immutable
data class CrateExtendedColors(
    /** A gain against the recorded purchase price; the loss side uses `error`. */
    val gain: Color,
)

private val LocalCrateExtendedColors =
    staticCompositionLocalOf { CrateExtendedColors(gain = GainLight) }

/** The extended roles for the current theme. Provided by [CrateTheme]. */
val crateColors: CrateExtendedColors
    @Composable get() = LocalCrateExtendedColors.current

@Composable
fun CrateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You on Android 12+ by default; brand palette otherwise. Callers
    // can opt out (Settings → force brand scheme) by passing false.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> {
            CrateDarkColorScheme
        }

        else -> {
            CrateLightColorScheme
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge is enabled in MainActivity; here we just align
            // the system-bar icon colours with the chosen scheme so they
            // remain legible over whatever the app draws behind them.
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val extended = CrateExtendedColors(gain = if (darkTheme) GainDark else GainLight)

    CompositionLocalProvider(LocalCrateExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CrateTypography,
            shapes = CrateShapes,
            content = content,
        )
    }
}
