package com.sierracode.autocontrol.domain.calc

import com.sierracode.autocontrol.data.local.entity.DocumentEntity

enum class DocumentStatus {
    VALID, EXPIRING_SOON, EXPIRED
}

data class DocumentEvaluation(
    val status: DocumentStatus,
    val daysRemaining: Long // Positivo = faltan días, Negativo = venció hace X días
)

fun evaluateDocument(
    expiryDateEpochDay: Long,
    todayEpochDay: Long,
    leadDays: Int
): DocumentEvaluation {
    val daysRemaining = expiryDateEpochDay - todayEpochDay
    val status = when {
        daysRemaining < 0 -> DocumentStatus.EXPIRED
        daysRemaining <= leadDays -> DocumentStatus.EXPIRING_SOON
        else -> DocumentStatus.VALID
    }
    return DocumentEvaluation(status, daysRemaining)
}

data class ExpiringDocumentItem(
    val document: DocumentEntity,
    val daysRemaining: Long,
    val isExpired: Boolean
)

/**
 * Filtra documentos vencidos o por vencer en ≤ [maxDaysAhead] días (por defecto 30),
 * excluyendo documentos sin fecha de vencimiento o con vencimiento superior a [maxDaysAhead] días.
 * Ordenados por fecha de vencimiento ascendente (los más urgentes o vencidos primero).
 * Limita el resultado a un máximo de [limit] documentos (por defecto 3).
 */
fun filterExpiringDocuments(
    documents: List<DocumentEntity>,
    todayEpochDay: Long,
    maxDaysAhead: Long = 30L,
    limit: Int = 3
): List<ExpiringDocumentItem> {
    return documents
        .filter { it.expiryDateEpochDay != null }
        .map { doc ->
            val daysRemaining = doc.expiryDateEpochDay!! - todayEpochDay
            ExpiringDocumentItem(
                document = doc,
                daysRemaining = daysRemaining,
                isExpired = daysRemaining < 0
            )
        }
        .filter { it.daysRemaining <= maxDaysAhead }
        .sortedBy { it.document.expiryDateEpochDay }
        .take(limit)
}
