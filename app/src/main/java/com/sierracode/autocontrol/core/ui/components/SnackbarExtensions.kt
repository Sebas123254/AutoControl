package com.sierracode.autocontrol.core.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.res.stringResource
import com.sierracode.autocontrol.R

/**
 * Snackbar con acción "Deshacer".
 *
 * Devuelve `true` si el usuario tocó "Deshacer" (y ejecutó `onUndo`).
 * Se usará en Fase 3 para eliminar gasto, documento, vehículo, objetivo
 * y recordatorio (alcance aprobado en D5).
 */
suspend fun SnackbarHostState.showUndoSnackbar(
    message: String,
    actionLabel: String,
    duration: SnackbarDuration = SnackbarDuration.Long,
    onUndo: () -> Unit
): Boolean {
    val result = showSnackbar(
        message = message,
        actionLabel = actionLabel,
        duration = duration
    )
    if (result == SnackbarResult.ActionPerformed) {
        onUndo()
        return true
    }
    return false
}