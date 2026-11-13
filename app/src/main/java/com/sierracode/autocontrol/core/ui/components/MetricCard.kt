package com.sierracode.autocontrol.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sierracode.autocontrol.core.ui.theme.AutoControlTheme

/**
 * Tarjeta de métrica. Extraída de DashboardScreen para reutilizarla en Stats.
 * `subtitle` reemplaza el Text explicativo que hoy se coloca debajo de la card.
 * `onClick` permite drill-down (Stats → Gastos filtrados).
 */
@Composable
fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier) { content() }
    } else {
        Card(modifier = modifier) { content() }
    }
}

@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MetricCardPreview() {
    AutoControlTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricCard(title = "Gasto del mes", value = "$4,250.00", modifier = Modifier.weight(1f))
            MetricCard(
                title = "Costo por km",
                value = "$2.31/km",
                modifier = Modifier.weight(1f),
                subtitle = "Histórico"
            )
        }
    }
}