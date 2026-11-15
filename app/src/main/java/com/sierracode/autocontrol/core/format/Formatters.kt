package com.sierracode.autocontrol.core.format

import androidx.compose.runtime.Composable
import com.sierracode.autocontrol.core.ui.LocalAppConfig
import com.sierracode.autocontrol.data.preferences.EfficiencyFormat
import com.sierracode.autocontrol.data.preferences.UnitSystem
import com.sierracode.autocontrol.domain.calc.UnitConverter
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun formatMoneyCents(cents: Long?): String {
    if (cents == null) return "—"
    val config = LocalAppConfig.current
    val locale = Locale.getDefault()
    val currency = try {
        Currency.getInstance(config.currencyCode)
    } catch (e: Exception) {
        Currency.getInstance("MXN")
    }

    val money = NumberFormat.getCurrencyInstance(locale).apply {
        this.currency = currency
        maximumFractionDigits = 2
    }

    val suffix = if (config.currencyCode == "MXN" && locale.language != "en") " ${config.currencyCode}" else ""
    return "${money.format(cents / 100.0)}$suffix"
}

@Composable
fun formatKm(km: Long?): String {
    if (km == null) return "—"
    val config = LocalAppConfig.current
    return if (config.unitSystem == UnitSystem.METRIC) {
        "${NumberFormat.getIntegerInstance().format(km)} km"
    } else {
        val miles = UnitConverter.kmToMiles(km).roundToLong()
        "${NumberFormat.getIntegerInstance().format(miles)} mi"
    }
}

@Composable
fun formatEfficiency(kmPerLiter: Double?): String {
    if (kmPerLiter == null) return "—"
    val config = LocalAppConfig.current
    val fmt = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }
    return when (config.efficiencyFormat) {
        EfficiencyFormat.KM_PER_L -> "${fmt.format(kmPerLiter)} km/L"
        EfficiencyFormat.L_PER_100KM -> "${fmt.format(UnitConverter.kmPerLiterToLPer100Km(kmPerLiter))} L/100km"
        EfficiencyFormat.MPG_US -> "${fmt.format(UnitConverter.kmPerLiterToMpg(kmPerLiter))} MPG"
    }
}

@Composable
fun formatVolume(litersMilli: Long?): String {
    if (litersMilli == null) return "—"
    val config = LocalAppConfig.current
    return if (config.unitSystem == UnitSystem.METRIC) {
        val l = litersMilli / 1000.0
        "${NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(l)} L"
    } else {
        val gal = UnitConverter.litersMilliToGallons(litersMilli)
        "${NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(gal)} gal"
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
    val suffix = if (config.unitSystem == UnitSystem.METRIC) "/km" else "/mi"
    return formatMoneyCents((costPerUnit * 100).roundToLong()) + suffix
}

fun formatDate(epochDay: Long?): String {
    if (epochDay == null) return "—"
    val locale = Locale.getDefault()
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    return LocalDate.ofEpochDay(epochDay).format(formatter)
}

fun formatDouble(v: Double?, digits: Int = 2, suffix: String = ""): String =
    if (v == null) "—" else "%.${digits}f%s".format(Locale.US, v, suffix)