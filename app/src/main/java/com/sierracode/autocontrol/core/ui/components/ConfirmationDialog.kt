package com.sierracode.autocontrol.core.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.ui.theme.AutoControlTheme

/**
 * Diálogo de confirmación unificado.
 * `isDestructive = true` pinta el botón de confirmar en color de error.
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false,
    dismissLabel: String = stringResource(R.string.action_cancel)
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (isDestructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    )
}

@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConfirmationDialogPreview() {
    AutoControlTheme {
        ConfirmationDialog(
            title = "Eliminar gasto",
            message = "Se eliminará también su recibo. Esta acción no se puede deshacer.",
            confirmLabel = "Eliminar",
            onConfirm = {},
            onDismiss = {},
            isDestructive = true
        )
    }
}