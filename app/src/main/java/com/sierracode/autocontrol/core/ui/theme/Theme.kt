package com.sierracode.autocontrol.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

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
fun AutoControlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // AÑADIDO: Soporte para color dinámico (Material You)
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Si el usuario tiene Android 12+ y dynamicColor está habilitado, usamos los colores del sistema
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Si no, usamos la paleta de marca
        darkTheme -> Dark
        else -> Light
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}