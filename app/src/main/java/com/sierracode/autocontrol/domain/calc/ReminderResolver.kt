package com.sierracode.autocontrol.domain.reminder

import android.content.Context
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.data.local.entity.ReminderEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Resuelve el título y el cuerpo localizados de un `ReminderEntity`.
 *
 * Estrategia:
 * 1. Si hay `templateKey` + `templateArgs` válidos, resolver con `context.getString(...)`.
 * 2. Si no (o si falla la deserialización), caer al `title`/`body` persistido.
 *
 * Los recordatorios creados antes de la migración 6→7 no tienen `templateArgs`,
 * así que siempre caen al fallback. Eso es aceptable: mantienen su idioma original
 * pero no rompen.
 */
object ReminderResolver {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable data class DocumentExpiringArgs(val documentName: String)
    @Serializable data class MaintenanceDueArgs(val serviceName: String)

    fun resolveTitle(context: Context, reminder: ReminderEntity): String {
        return try {
            when (reminder.templateKey) {
                "document_expiring" -> {
                    val args = reminder.templateArgs
                        ?.let { json.decodeFromString<DocumentExpiringArgs>(it) }
                    args?.let {
                        context.getString(R.string.notif_doc_expiring_title, it.documentName)
                    } ?: reminder.title
                }
                "maintenance_due" -> {
                    val args = reminder.templateArgs
                        ?.let { json.decodeFromString<MaintenanceDueArgs>(it) }
                    args?.let {
                        context.getString(R.string.notif_maint_due_title, it.serviceName)
                    } ?: reminder.title
                }
                else -> reminder.title
            }
        } catch (e: Exception) {
            reminder.title
        }
    }

    fun resolveBody(context: Context, reminder: ReminderEntity): String {
        return try {
            when (reminder.templateKey) {
                "document_expiring" -> context.getString(R.string.notif_doc_expiring_body)
                "maintenance_due" -> context.getString(R.string.notif_maint_due_body)
                else -> reminder.body
            }
        } catch (e: Exception) {
            reminder.body
        }
    }

    fun encodeDocumentExpiringArgs(documentName: String): String =
        json.encodeToString(DocumentExpiringArgs.serializer(), DocumentExpiringArgs(documentName))

    fun encodeMaintenanceDueArgs(serviceName: String): String =
        json.encodeToString(MaintenanceDueArgs.serializer(), MaintenanceDueArgs(serviceName))
}