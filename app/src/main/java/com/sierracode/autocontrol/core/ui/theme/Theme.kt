package com.sierracode.autocontrol.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val Light = lightColorScheme(
    primary = Graphite, onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = Steel, tertiary = Amber,
    background = SurfaceLight, surface = CardLight,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE7EAF0),
    error = Crimson
)
private val Dark = darkColorScheme(
    primary = Steel, onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = Teal, tertiary = Amber,
    background = SurfaceDark, surface = CardDark,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF232A34),
    error = Crimson
)

@Composable
fun AutoControlTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = Typography, // <-- AHORA SÍ SE APLICA
        content = content
    )
}