package com.sierracode.autocontrol.domain.calc

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToLong

object UnitConverter {
    const val KM_PER_MILE = 1.609344
    const val LITERS_PER_GALLON = 3.785411784

    // Distancia
    fun milesToKm(miles: Double): Long = (miles * KM_PER_MILE).roundToLong()
    fun kmToMiles(km: Long): Double = km / KM_PER_MILE

    // Volumen
    fun gallonsToLitersMilli(gallons: Double): Long = (gallons * LITERS_PER_GALLON * 1000).roundToLong()
    fun litersMilliToGallons(milli: Long): Double = (milli / 1000.0) / LITERS_PER_GALLON

    // Eficiencia
    fun kmPerLiterToMpg(kmPerLiter: Double): Double = kmPerLiter * LITERS_PER_GALLON / KM_PER_MILE
    fun mpgToKmPerLiter(mpg: Double): Double = mpg * KM_PER_MILE / LITERS_PER_GALLON
    fun kmPerLiterToLPer100Km(kmPerLiter: Double): Double = if (kmPerLiter == 0.0) 0.0 else 100.0 / kmPerLiter

    // Costo por distancia
    fun costPerKmToCostPerMile(costPerKm: Double): Double = costPerKm * KM_PER_MILE
    fun costPerMileToCostPerKm(costPerMile: Double): Double = costPerMile / KM_PER_MILE

    // Seguridad en conversiones de ida y vuelta (HALF_UP)
    fun safeDoubleToLong(value: Double): Long = BigDecimal(value).setScale(0, RoundingMode.HALF_UP).toLong()
}