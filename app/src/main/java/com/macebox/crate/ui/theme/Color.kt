package com.macebox.crate.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val CopperPrimary = Color(0xFF8B5A2B)
private val CopperOnPrimary = Color(0xFFFFFFFF)
private val CopperPrimaryContainer = Color(0xFFFFDCBE)
private val CopperOnPrimaryContainer = Color(0xFF2E1500)

private val BrownSecondary = Color(0xFF745943)
private val BrownOnSecondary = Color(0xFFFFFFFF)
private val BrownSecondaryContainer = Color(0xFFFFDCBE)
private val BrownOnSecondaryContainer = Color(0xFF2A1706)

private val MossTertiary = Color(0xFF5A6447)
private val MossOnTertiary = Color(0xFFFFFFFF)
private val MossTertiaryContainer = Color(0xFFDDEAC3)
private val MossOnTertiaryContainer = Color(0xFF181E09)

private val ErrorLight = Color(0xFFBA1A1A)
private val OnErrorLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFFFDAD6)
private val OnErrorContainerLight = Color(0xFF410002)

private val BackgroundLight = Color(0xFFFFF8F4)
private val OnBackgroundLight = Color(0xFF201A17)
private val SurfaceLight = Color(0xFFFFF8F4)
private val OnSurfaceLight = Color(0xFF201A17)
private val SurfaceVariantLight = Color(0xFFF3DFD2)
private val OnSurfaceVariantLight = Color(0xFF52443A)
private val OutlineLight = Color(0xFF847469)
private val OutlineVariantLight = Color(0xFFD7C2B6)

private val CopperPrimaryDark = Color(0xFFFFB68F)
private val CopperOnPrimaryDark = Color(0xFF4C2700)
private val CopperPrimaryContainerDark = Color(0xFF6D3D14)
private val CopperOnPrimaryContainerDark = Color(0xFFFFDCBE)

private val BrownSecondaryDark = Color(0xFFE4BFA1)
private val BrownOnSecondaryDark = Color(0xFF422B17)
private val BrownSecondaryContainerDark = Color(0xFF5B412C)
private val BrownOnSecondaryContainerDark = Color(0xFFFFDCBE)

private val MossTertiaryDark = Color(0xFFC1CDA9)
private val MossOnTertiaryDark = Color(0xFF2D351C)
private val MossTertiaryContainerDark = Color(0xFF434C31)
private val MossOnTertiaryContainerDark = Color(0xFFDDEAC3)

private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)
private val ErrorContainerDark = Color(0xFF93000A)
private val OnErrorContainerDark = Color(0xFFFFDAD6)

// Matches the launcher-icon background so the splash-into-app transition is seamless.
private val BackgroundDark = Color(0xFF1B1F2A)
private val OnBackgroundDark = Color(0xFFECE0D8)
private val SurfaceDark = Color(0xFF1B1F2A)
private val OnSurfaceDark = Color(0xFFECE0D8)
private val SurfaceVariantDark = Color(0xFF52443A)
private val OnSurfaceVariantDark = Color(0xFFD7C2B6)
private val OutlineDark = Color(0xFFA08D80)
private val OutlineVariantDark = Color(0xFF52443A)

internal val CrateLightColorScheme = lightColorScheme(
    primary = CopperPrimary,
    onPrimary = CopperOnPrimary,
    primaryContainer = CopperPrimaryContainer,
    onPrimaryContainer = CopperOnPrimaryContainer,
    secondary = BrownSecondary,
    onSecondary = BrownOnSecondary,
    secondaryContainer = BrownSecondaryContainer,
    onSecondaryContainer = BrownOnSecondaryContainer,
    tertiary = MossTertiary,
    onTertiary = MossOnTertiary,
    tertiaryContainer = MossTertiaryContainer,
    onTertiaryContainer = MossOnTertiaryContainer,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
)

internal val CrateDarkColorScheme = darkColorScheme(
    primary = CopperPrimaryDark,
    onPrimary = CopperOnPrimaryDark,
    primaryContainer = CopperPrimaryContainerDark,
    onPrimaryContainer = CopperOnPrimaryContainerDark,
    secondary = BrownSecondaryDark,
    onSecondary = BrownOnSecondaryDark,
    secondaryContainer = BrownSecondaryContainerDark,
    onSecondaryContainer = BrownOnSecondaryContainerDark,
    tertiary = MossTertiaryDark,
    onTertiary = MossOnTertiaryDark,
    tertiaryContainer = MossTertiaryContainerDark,
    onTertiaryContainer = MossOnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
)
