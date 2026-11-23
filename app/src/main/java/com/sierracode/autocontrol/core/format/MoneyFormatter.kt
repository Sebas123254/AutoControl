package com.sierracode.autocontrol.core.format

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Formateador de moneda centralizado (es-MX, 2 decimales).
 * Siempre muestra la divisa exactamente una vez.
 */
object MoneyFormatter {
    private val locale = Locale("es", "MX")
    private val symbols = DecimalFormatSymbols(locale).apply {
        currencySymbol = "$"
    }

    // "$#,##0.00" garantiza coma como separador de miles y punto como decimal
    private val decimalFormat = DecimalFormat("$#,##0.00", symbols)

    private fun safeFormat(amount: Double): String = synchronized(decimalFormat) {
        decimalFormat.format(amount)
    }

    /**
     * Formatea centavos a formato monetario sin código de divisa.
     * Ejemplo: format(247500L) -> "$2,475.00"
     * Si cents es null devuelve "—" (nunca "$0.00").
     */
    fun format(cents: Long?): String {
        if (cents == null) return "—"
        return safeFormat(cents / 100.0)
    }

    /**
     * Formatea centavos con el código de divisa al final (para cifras principales).
     * Ejemplo: formatWithCode(247500L, "MXN") -> "$2,475.00 MXN"
     * Si cents es null devuelve "—" (nunca "$0.00").
     */
    fun formatWithCode(cents: Long?, currencyCode: String = "MXN"): String {
        if (cents == null) return "—"
        val code = currencyCode.trim()
        val suffix = if (code.isNotEmpty()) " $code" else ""
        return "${safeFormat(cents / 100.0)}$suffix"
    }

    /**
     * Formatea costo por unidad de distancia.
     * Ejemplo: formatPerDistance(71L, "km") -> "$0.71/km"
     * Si cents es null devuelve "—" (nunca "$0.00").
     */
    fun formatPerDistance(cents: Long?, unit: String): String {
        if (cents == null) return "—"
        val cleanUnit = unit.removePrefix("/")
        return "${safeFormat(cents / 100.0)}/$cleanUnit"
    }
}
