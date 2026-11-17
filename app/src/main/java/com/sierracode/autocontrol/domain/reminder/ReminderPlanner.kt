package com.sierracode.autocontrol.domain.reminder

import com.sierracode.autocontrol.data.local.entity.DocumentEntity
import com.sierracode.autocontrol.data.local.entity.MaintenanceScheduleEntity
import com.sierracode.autocontrol.data.local.entity.ReminderEntity
import com.sierracode.autocontrol.data.repository.ReminderRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderPlanner @Inject constructor(
    private val reminderRepo: ReminderRepository
) {

    /**
     * Crea o actualiza el recordatorio de un documento.
     * Resetea el stage si el documento es nuevo o si cambió la fecha.
     */
    suspend fun planDocument(doc: DocumentEntity) {
        // Borramos el anterior para asegurar que el stage se resetee al editar.
        // (Si solo editó las notas, el stage se resetea, pero es aceptable.
        //   Si quisiéramos ser ultra-precisos, compararíamos con el anterior).
        reminderRepo.deleteBySource("DOCUMENT", doc.id)

        val expiry = doc.expiryDateEpochDay ?: return

        // Trigger inicial: inicio del día en que comienza a avisar
        val firstAlertDay = expiry - doc.reminderLeadDays
        val triggerMillis = LocalDate.ofEpochDay(firstAlertDay)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val reminder = ReminderEntity(
            vehicleId = doc.vehicleId,
            sourceType = "DOCUMENT",
            sourceId = doc.id,
            title = "Documento por vencer: ${doc.name}",
            body = "Tu documento vence pronto. ¡Revisa AutoControl!",
            triggerAtEpochMillis = triggerMillis,
            templateKey = "document_expiring",
            templateArgs = ReminderResolver.encodeDocumentExpiringArgs(doc.name),
            lastNotifiedStage = null
        )
        reminderRepo.upsert(reminder)
    }

    /**
     * Crea o actualiza el recordatorio de un mantenimiento.
     * Si el schedule es solo por km, usa "now" como trigger para que el worker lo evalúe a diario.
     */
    suspend fun planMaintenance(schedule: MaintenanceScheduleEntity, serviceName: String) {
        reminderRepo.deleteBySource("MAINTENANCE", schedule.id)

        var triggerMillis = System.currentTimeMillis() // Default: evaluar diario si es por km

        if (schedule.lastDateEpochDay != null && schedule.intervalMonths != null) {
            val nextDate = LocalDate.ofEpochDay(schedule.lastDateEpochDay)
                .plusMonths(schedule.intervalMonths.toLong())
            val firstAlertDay = nextDate.toEpochDay() - schedule.remindBeforeDays
            triggerMillis = LocalDate.ofEpochDay(firstAlertDay)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }

        val reminder = ReminderEntity(
            vehicleId = schedule.vehicleId,
            sourceType = "MAINTENANCE",
            sourceId = schedule.id,
            title = "Mantenimiento: $serviceName",
            body = "Tu vehículo podría necesitar este servicio pronto.",
            triggerAtEpochMillis = triggerMillis,
            templateKey = "maintenance_due",
            templateArgs = ReminderResolver.encodeMaintenanceDueArgs(serviceName),
            lastNotifiedStage = null
        )
        reminderRepo.upsert(reminder)
    }

    suspend fun deleteDocumentReminder(id: String) {
        reminderRepo.deleteBySource("DOCUMENT", id)
    }

    suspend fun deleteMaintenanceReminder(scheduleId: String) {
        reminderRepo.deleteBySource("MAINTENANCE", scheduleId)
    }
}