package com.megamaced.crate.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Nextcloud-blue palette: matches the colour story used by NC Collectives
// and the wider Nextcloud client suite (Files, News, Passwords, Talk) so
// Crate sits visually alongside them when a user has multiple Nextcloud
// apps installed. Primary is Nextcloud's brand blue (#0082C9), paired
// with slate-grey secondary, moss tertiary and clean white/parchment
// neutrals.
private val NcBluePrimary = Color(0xFF0082C9)
private val NcBlueOnPrimary = Color(0xFFFFFFFF)
private val NcBluePrimaryContainer = Color(0xFFCDE5FF)
private val NcBlueOnPrimaryContainer = Color(0xFF001D36)

private val SlateSecondary = Color(0xFF515E70)
private val SlateOnSecondary = Color(0xFFFFFFFF)
private val SlateSecondaryContainer = Color(0xFFD5E3F7)
private val SlateOnSecondaryContainer = Color(0xFF0D1B2A)

private val MossTertiary = Color(0xFF5A6447)
private val MossOnTertiary = Color(0xFFFFFFFF)
private val MossTertiaryContainer = Color(0xFFDDEAC3)
private val MossOnTertiaryContainer = Color(0xFF181E09)

private val ErrorLight = Color(0xFFBA1A1A)
private val OnErrorLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFFFDAD6)
private val OnErrorContainerLight = Color(0xFF410002)

private val BackgroundLight = Color(0xFFFBFCFE)
private val OnBackgroundLight = Color(0xFF1B1B1F)
private val SurfaceLight = Color(0xFFFBFCFE)
private val OnSurfaceLight = Color(0xFF1B1B1F)
private val SurfaceVariantLight = Color(0xFFE0E2EC)
private val OnSurfaceVariantLight = Color(0xFF44474E)
private val OutlineLight = Color(0xFF74777F)
private val OutlineVariantLight = Color(0xFFC4C6CF)

private val NcBluePrimaryDark = Color(0xFF9BCAFF)
private val NcBlueOnPrimaryDark = Color(0xFF003258)
private val NcBluePrimaryContainerDark = Color(0xFF00497D)
private val NcBlueOnPrimaryContainerDark = Color(0xFFCDE5FF)

private val SlateSecondaryDark = Color(0xFFB8C7DA)
private val SlateOnSecondaryDark = Color(0xFF233140)
private val SlateSecondaryContainerDark = Color(0xFF3A4757)
private val SlateOnSecondaryContainerDark = Color(0xFFD5E3F7)

private val MossTertiaryDark = Color(0xFFC1CDA9)
private val MossOnTertiaryDark = Color(0xFF2D351C)
private val MossTertiaryContainerDark = Color(0xFF434C31)
private val MossOnTertiaryContainerDark = Color(0xFFDDEAC3)

private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)
private val ErrorContainerDark = Color(0xFF93000A)
private val OnErrorContainerDark = Color(0xFFFFDAD6)

private val BackgroundDark = Color(0xFF13161B)
private val OnBackgroundDark = Color(0xFFE3E2E6)
private val SurfaceDark = Color(0xFF13161B)
private val OnSurfaceDark = Color(0xFFE3E2E6)
private val SurfaceVariantDark = Color(0xFF44474E)
private val OnSurfaceVariantDark = Color(0xFFC4C6CF)
private val OutlineDark = Color(0xFF8E9099)
private val OutlineVariantDark = Color(0xFF44474E)

internal val CrateLightColorScheme = lightColorScheme(
    primary = NcBluePrimary,
    onPrimary = NcBlueOnPrimary,
    primaryContainer = NcBluePrimaryContainer,
    onPrimaryContainer = NcBlueOnPrimaryContainer,
    secondary = SlateSecondary,
    onSecondary = SlateOnSecondary,
    secondaryContainer = SlateSecondaryContainer,
    onSecondaryContainer = SlateOnSecondaryContainer,
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
    primary = NcBluePrimaryDark,
    onPrimary = NcBlueOnPrimaryDark,
    primaryContainer = NcBluePrimaryContainerDark,
    onPrimaryContainer = NcBlueOnPrimaryContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = SlateOnSecondaryDark,
    secondaryContainer = SlateSecondaryContainerDark,
    onSecondaryContainer = SlateOnSecondaryContainerDark,
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
