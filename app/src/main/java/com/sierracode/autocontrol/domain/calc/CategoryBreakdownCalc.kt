package com.sierracode.autocontrol.domain.calc

data class CategoryAmountInput(
    val categoryId: String,
    val name: String,
    val amountCents: Long
)

data class CategoryBreakdownItem(
    val categoryId: String,
    val name: String,
    val amountCents: Long,
    val percentage: Double
)

/**
 * Calcula el desglose de gastos por categoría para el periodo seleccionado.
 * - Devuelve null si no hay datos válidos o la suma total es <= 0.
 * - Monto en centavos Long.
 * - Máx. [maxRows] filas (por defecto 4); las categorías sobrantes se agrupan en [otherLabel] ("Otros").
 */
fun calculateCategoryBreakdown(
    inputs: List<CategoryAmountInput>,
    otherLabel: String = "Otros",
    maxRows: Int = 4
): List<CategoryBreakdownItem>? {
    val valid = inputs.filter { it.amountCents > 0L }
    if (valid.isEmpty()) return null

    val grouped = valid.groupBy { it.categoryId }
        .map { (catId, items) ->
            CategoryAmountInput(
                categoryId = catId,
                name = items.first().name,
                amountCents = items.sumOf { it.amountCents }
            )
        }

    val totalCents = grouped.sumOf { it.amountCents }
    if (totalCents <= 0L) return null

    val sorted = grouped.sortedByDescending { it.amountCents }

    return if (sorted.size <= maxRows) {
        sorted.map { item ->
            CategoryBreakdownItem(
                categoryId = item.categoryId,
                name = item.name,
                amountCents = item.amountCents,
                percentage = (item.amountCents.toDouble() / totalCents.toDouble()) * 100.0
            )
        }
    } else {
        val top = sorted.take(maxRows - 1)
        val rest = sorted.drop(maxRows - 1)
        val otherCents = rest.sumOf { it.amountCents }

        val items = top.map { item ->
            CategoryBreakdownItem(
                categoryId = item.categoryId,
                name = item.name,
                amountCents = item.amountCents,
                percentage = (item.amountCents.toDouble() / totalCents.toDouble()) * 100.0
            )
        }.toMutableList()

        if (otherCents > 0L) {
            val existingOtherIndex = items.indexOfFirst {
                it.categoryId == "other" || it.categoryId == "other_misc" || it.name.equals(otherLabel, ignoreCase = true)
            }
            if (existingOtherIndex != -1) {
                val existing = items[existingOtherIndex]
                val combinedCents = existing.amountCents + otherCents
                items[existingOtherIndex] = existing.copy(
                    amountCents = combinedCents,
                    percentage = (combinedCents.toDouble() / totalCents.toDouble()) * 100.0
                )
            } else {
                items.add(
                    CategoryBreakdownItem(
                        categoryId = "other",
                        name = otherLabel,
                        amountCents = otherCents,
                        percentage = (otherCents.toDouble() / totalCents.toDouble()) * 100.0
                    )
                )
            }
        }
        items
    }
}
