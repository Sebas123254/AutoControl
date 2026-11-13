package com.sierracode.autocontrol.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import com.sierracode.autocontrol.core.ui.theme.AutoControlTheme
import com.sierracode.autocontrol.core.ui.theme.SuccessLight
import com.sierracode.autocontrol.core.ui.theme.WarningLight

enum class StatusVariant { OK, WARNING, ERROR, INFO, NEUTRAL }

/**
 * Chip de estado que NUNCA depende solo de color:
 * siempre incluye icono + texto.
 *
 * API principal: `variant` mapea a tokens semánticos.
 * Compatibilidad: `containerColor`/`contentColor` opcionales sobrescriben
 * los colores del variant. Permite que llamadas antiguas sigan compilando
 * tal cual durante la migración incremental de Fase 3.
 */
@Composable
fun StatusChip(
    text: String,
    icon: ImageVector,
    variant: StatusVariant = StatusVariant.NEUTRAL,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null
) {
    val (defaultContainer, defaultContent) = when (variant) {
        StatusVariant.OK ->
            SuccessLight.copy(alpha = 0.12f) to SuccessLight
        StatusVariant.WARNING ->
            WarningLight.copy(alpha = 0.12f) to WarningLight
        StatusVariant.ERROR ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        StatusVariant.INFO ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        StatusVariant.NEUTRAL ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val finalContainer = containerColor ?: defaultContainer
    val finalContent = contentColor ?: defaultContent

    Surface(
        color = finalContainer,
        contentColor = finalContent,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatusChipPreview() {
    AutoControlTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("Al día", Icons.Default.CheckCircle, StatusVariant.OK)
            StatusChip("Próximo", Icons.Default.Info, StatusVariant.WARNING)
            StatusChip("Vencido", Icons.Default.Warning, StatusVariant.ERROR)
            StatusChip("Info", Icons.Default.Info, StatusVariant.INFO)
            StatusChip("Neutro", Icons.Default.Info)
        }
    }
}