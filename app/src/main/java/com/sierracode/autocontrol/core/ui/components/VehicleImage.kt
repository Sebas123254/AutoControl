package com.sierracode.autocontrol.core.ui.components

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sierracode.autocontrol.core.util.FileUtils

/**
 * Resuelve la fuente de imagen para Coil:
 * - Si es una URI temporal (content:// o file://), la devuelve parseada.
 * - Si es un nombre relativo guardado en almacenamiento interno, devuelve el [java.io.File] si existe.
 * - Si no existe o es nulo/vacío, devuelve null.
 */
fun getVehicleImageModel(context: Context, photoUri: String?): Any? {
    if (photoUri.isNullOrBlank()) return null
    return if (photoUri.startsWith("content://") || photoUri.startsWith("file://")) {
        try {
            Uri.parse(photoUri)
        } catch (_: Exception) {
            null
        }
    } else {
        val file = FileUtils.getFile(context, photoUri)
        if (file != null && file.exists()) file else null
    }
}

/**
 * Avatar para el vehículo (cuadrado con esquinas redondeadas o circular).
 * Si hay fotografía disponible y válida, la muestra; en caso contrario,
 * recurre al ícono oficial de automóvil con el estilo característico de la app.
 */
@Composable
fun VehicleAvatar(
    photoUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    val model = remember(photoUri) { getVehicleImageModel(context, photoUri) }
    var hasError by remember(photoUri) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        if (model != null && !hasError) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { hasError = true }
            )
        } else {
            Icon(
                imageVector = Icons.Default.DirectionsCar,
                contentDescription = contentDescription,
                modifier = Modifier.size(size * 0.55f),
                tint = contentColor
            )
        }
    }
}

/**
 * Banner fotográfico para el vehículo (destacado en Garaje o Dashboard).
 */
@Composable
fun VehicleBanner(
    photoUri: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    contentDescription: String? = null,
    onError: () -> Unit = {}
) {
    val context = LocalContext.current
    val model = remember(photoUri) { getVehicleImageModel(context, photoUri) }
    var hasError by remember(photoUri) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (model != null && !hasError) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = {
                    hasError = true
                    onError()
                }
            )
        } else {
            Icon(
                imageVector = Icons.Default.DirectionsCar,
                contentDescription = contentDescription,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
