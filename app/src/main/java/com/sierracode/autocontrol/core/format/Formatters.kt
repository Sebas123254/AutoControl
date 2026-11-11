package com.sierracode.autocontrol.core.format

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val mx = Locale("es", "MX")
private val money = NumberFormat.getCurrencyInstance(mx).apply { maximumFractionDigits = 2 }
private val kmFmt = NumberFormat.getIntegerInstance(mx)
private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", mx)

fun formatMoneyCents(cents: Long?): String =
    if (cents == null) "—" else money.format(cents / 100.0)

fun formatKm(km: Long?): String =
    if (km == null) "—" else "${kmFmt.format(km)} km"

fun formatDate(epochDay: Long?): String =
    if (epochDay == null) "—" else LocalDate.ofEpochDay(epochDay).format(dateFmt)

fun formatDouble(v: Double?, digits: Int = 2, suffix: String = ""): String =
    if (v == null) "—" else "%.${digits}f%s".format(mx, v, suffix)