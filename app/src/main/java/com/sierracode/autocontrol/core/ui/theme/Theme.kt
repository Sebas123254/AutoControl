package com.sierracode.autocontrol.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary              = Graphite,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFD6DCE5),
    onPrimaryContainer   = GraphiteDark,

    secondary            = Steel,
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFDCE3EC),
    onSecondaryContainer = GraphiteDark,

    tertiary             = WarningLight,
    onTertiary           = Color.White,
    tertiaryContainer    = Color(0xFFFFE9C7),
    onTertiaryContainer  = Color(0xFF2A1A00),

    error                = Crimson,
    onError              = Color.White,
    errorContainer       = Color(0xFFFFDAD5),
    onErrorContainer     = Color(0xFF410001),

    background           = SurfaceLight,
    onBackground         = Graphite,
    surface              = CardLight,
    onSurface            = Graphite,
    surfaceVariant       = Color(0xFFE7EAF0),
    onSurfaceVariant     = Color(0xFF49454F)
)

private val Dark = darkColorScheme(
    // primary dark = SteelLight: 9.32:1 sobre background, 8.20:1 sobre surface
    primary              = SteelLight,
    onPrimary            = Graphite,
    primaryContainer     = Color(0xFF3A4757),
    onPrimaryContainer   = Color(0xFFD6DCE5),

    secondary            = Teal,
    onSecondary          = Color(0xFF00332E),
    secondaryContainer   = Color(0xFF1F4F4B),
    onSecondaryContainer = Color(0xFFB2DFDA),

    tertiary             = WarningDark,
    onTertiary           = Color(0xFF2A1A00),
    tertiaryContainer    = Color(0xFF5C3F00),
    onTertiaryContainer  = Color(0xFFFFE9C7),

    // error dark = CrimsonLight: 8.68:1 sobre background
    error                = CrimsonLight,
    onError              = Color(0xFF410001),
    errorContainer       = Color(0xFF93000A),
    onErrorContainer     = Color(0xFFFFDAD5),

    background           = SurfaceDark,
    onBackground         = Color(0xFFE6E1E5),
    surface              = CardDark,
    onSurface            = Color(0xFFE6E1E5),
    surfaceVariant       = Color(0xFF232A34),
    onSurfaceVariant     = Color(0xFFCAC4D0)
)

@Composable
fun AutoControlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Fase 2: default false para preservar identidad de marca.
    // El toggle de Ajustes llega en Fase 3 (requiere SettingsStore).
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> Dark
        else -> Light
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AutoControlTypography,
        shapes      = AutoControlShapes,
        content     = content
    )
}