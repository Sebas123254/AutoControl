package com.sierracode.autocontrol.core.ui

import android.content.Context
import androidx.annotation.StringRes
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.data.backup.BackupError

sealed interface UiError {
    fun asString(context: Context): String

    data class Resource(@StringRes val resId: Int, val formatArgs: List<Any> = emptyList()) : UiError {
        override fun asString(context: Context): String {
            return if (formatArgs.isEmpty()) {
                context.getString(resId)
            } else {
                context.getString(resId, *formatArgs.toTypedArray())
            }
        }
    }

    data class Raw(val message: String) : UiError {
        override fun asString(context: Context): String = message
    }

    companion object {
        fun from(throwable: Throwable): UiError {
            return when (throwable) {
                is BackupError.CannotRead -> Resource(R.string.backup_error_cannot_read)
                is BackupError.CannotWrite -> Resource(R.string.backup_error_cannot_write)
                is BackupError.MissingJson -> Resource(R.string.backup_error_missing_json)
                is BackupError.NewerVersion -> Resource(
                    R.string.backup_error_newer_version,
                    listOf(throwable.found, throwable.supported)
                )
                else -> Resource(R.string.error_generic)
            }
        }
    }
}
