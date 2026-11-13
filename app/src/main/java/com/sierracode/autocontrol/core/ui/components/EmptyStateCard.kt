package com.sierracode.autocontrol.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import com.sierracode.autocontrol.core.ui.theme.AutoControlTheme

/**
 * Estado vacío unificado.
 *
 * Sustituye al antiguo `EmptyState` y a `EmptyStateCard`:
 * - Si `actionLabel`/`onActionClick` son no nulos, aparece un FilledTonalButton
 *   (menor peso visual que `Button`).
 * - El icono es opcional para mantener compatibilidad con las llamadas existentes
 *   que no lo pasan.
 */
@Composable
fun EmptyStateCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onActionClick != null) {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onActionClick) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EmptyStateCardPreview() {
    AutoControlTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            EmptyStateCard(
                icon = Icons.Default.Info,
                title = "Sin datos suficientes",
                description = "Registra al menos 2 cargas de tanque lleno para ver tu rendimiento.",
                actionLabel = "Registrar carga",
                onActionClick = {}
            )
            EmptyStateCard(
                title = "Solo informativo",
                description = "Esta variante no tiene acción."
            )
        }
    }
}