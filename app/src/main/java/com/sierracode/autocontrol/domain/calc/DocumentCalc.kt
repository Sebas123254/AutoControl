package com.sierracode.autocontrol.domain.calc

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