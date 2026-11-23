package com.sierracode.autocontrol.core.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.sierracode.autocontrol.core.ui.LocalAppConfig
import com.sierracode.autocontrol.data.preferences.EfficiencyFormat
import com.sierracode.autocontrol.data.preferences.UnitSystem
import com.sierracode.autocontrol.domain.calc.UnitConverter
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong

private val numberFormats = ConcurrentHashMap<String, NumberFormat>()

/** Idioma actual leído de forma observable: si el usuario cambia el idioma, la pantalla se recompone. */
@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

private fun decimalFormat(locale: Locale): NumberFormat =
    numberFormats.getOrPut("${locale.toLanguageTag()}|dec2") {
        NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }
    }

private fun integerFormat(locale: Locale): NumberFormat =
    numberFormats.getOrPut("${locale.toLanguageTag()}|int") {
        NumberFormat.getIntegerInstance(locale)
    }

// NumberFormat no es thread-safe: se sincroniza al usar la instancia compartida.
private fun NumberFormat.safeFormat(value: Double): String = synchronized(this) { format(value) }
private fun NumberFormat.safeFormat(value: Long): String = synchronized(this) { format(value) }

@Composable
fun formatMoneyCents(cents: Long?): String {
    if (cents == null) return "—"
    return MoneyFormatter.format(cents)
}

@Composable
fun formatMoneyCentsWithCode(cents: Long?): String {
    if (cents == null) return "—"
    val config = LocalAppConfig.current
    return MoneyFormatter.formatWithCode(cents, config.currencyCode)
}

@Composable
fun formatKm(km: Long?): String {
    if (km == null) return "—"
    val config = LocalAppConfig.current
    val nf = integerFormat(currentLocale())
    return if (config.unitSystem == UnitSystem.METRIC) {
        "${nf.safeFormat(km)} km"
    } else {
        "${nf.safeFormat(UnitConverter.kmToMiles(km).roundToLong())} mi"
    }
}

@Composable
fun formatEfficiency(kmPerLiter: Double?): String {
    if (kmPerLiter == null) return "—"
    val config = LocalAppConfig.current
    val fmt = decimalFormat(currentLocale())
    return when (config.efficiencyFormat) {
        EfficiencyFormat.KM_PER_L -> "${fmt.safeFormat(kmPerLiter)} km/L"
        EfficiencyFormat.L_PER_100KM -> "${fmt.safeFormat(UnitConverter.kmPerLiterToLPer100Km(kmPerLiter))} L/100km"
        EfficiencyFormat.MPG_US -> "${fmt.safeFormat(UnitConverter.kmPerLiterToMpg(kmPerLiter))} MPG"
    }
}

@Composable
fun formatVolume(litersMilli: Long?): String {
    if (litersMilli == null) return "—"
    val config = LocalAppConfig.current
    val nf = decimalFormat(currentLocale())
    return if (config.unitSystem == UnitSystem.METRIC) {
        "${nf.safeFormat(litersMilli / 1000.0)} L"
    } else {
        "${nf.safeFormat(UnitConverter.litersMilliToGallons(litersMilli))} gal"
    }
}

@Composable
fun formatCostPerDistance(costPerKmCents: Double?): String {
    if (costPerKmCents == null) return "—"
    val config = LocalAppConfig.current
    val costPerUnit = if (config.unitSystem == UnitSystem.METRIC) {
        costPerKmCents / 100.0
    } else {
        UnitConverter.costPerKmToCostPerMile(costPerKmCents) / 100.0
    }
    val unit = if (config.unitSystem == UnitSystem.METRIC) "km" else "mi"
    return MoneyFormatter.formatPerDistance((costPerUnit * 100).roundToLong(), unit)
}

fun formatDate(epochDay: Long?): String {
    if (epochDay == null) return "—"
    val locale = Locale.getDefault()
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    return LocalDate.ofEpochDay(epochDay).format(formatter)
}

fun formatDouble(v: Double?, digits: Int = 2, suffix: String = ""): String =
    if (v == null) "—" else "%.${digits}f%s".format(Locale.US, v, suffix)