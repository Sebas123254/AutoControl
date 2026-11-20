package com.sierracode.autocontrol.core.util

/**
 * Parser numérico único.
 * - Acepta ',' o '.' como separador decimal.
 * - Si aparecen ambos, el ÚLTIMO es decimal y el otro es de miles.
 * - Un mismo separador repetido (p. ej. "1.250.500") se trata como separador de miles.
 * - Ignora espacios (incluye NBSP). Devuelve null si el texto es vacío o inválido.
 */
object NumberParser {
    private val VALID = Regex("^-?\\d+(\\.\\d+)?$")

    fun parse(input: String?): Double? {
        if (input.isNullOrBlank()) return null
        val trimmed = input.replace(" ", "").replace("\u00A0", "")
        if (trimmed.isEmpty()) return null

        val sign = if (trimmed.startsWith("-")) "-" else ""
        val body = trimmed.removePrefix("-").removePrefix("+")
        if (body.isEmpty()) return null

        val commas = body.count { it == ',' }
        val dots = body.count { it == '.' }
        val lastComma = body.lastIndexOf(',')
        val lastDot = body.lastIndexOf('.')

        val decimalSeparator: Char? = when {
            commas > 0 && dots > 0 -> if (lastComma > lastDot) ',' else '.'
            commas > 1 || dots > 1 -> null
            commas == 1 -> ','
            dots == 1 -> '.'
            else -> null
        }
        val groupSeparator: Char? = when (decimalSeparator) {
            ',' -> '.'
            '.' -> ','
            else -> when {
                commas > 1 -> ','
                dots > 1 -> '.'
                else -> null
            }
        }

        val normalized = buildString {
            append(sign)
            for (c in body) {
                when {
                    c.isDigit() -> append(c)
                    c == decimalSeparator -> append('.')
                    c == groupSeparator -> {}
                    else -> return null
                }
            }
        }

        if (!VALID.matches(normalized)) return null
        return normalized.toDoubleOrNull()
    }
}