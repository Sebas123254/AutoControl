package com.sierracode.autocontrol.domain.calc

class UnitConverterTest {

    @Test
    fun `conversion de distancia es bidireccional y precisa`() {
        val km = 100L
        val miles = UnitConverter.kmToMiles(km)
        val kmBack = UnitConverter.milesToKm(miles)
        // Tolerancia por redondeo de Double a Long
        assertTrue(Math.abs(km - kmBack) <= 1)
    }

    @Test
    fun `conversion de volumen es bidireccional`() {
        val litersMilli = 100_000L // 100 L
        val gallons = UnitConverter.litersMilliToGallons(litersMilli)
        val back = UnitConverter.gallonsToLitersMilli(gallons)
        assertTrue(Math.abs(litersMilli - back) <= 1)
    }

    @Test
    fun `mpg y km por litro son coherentes`() {
        val kmL = 15.0
        val mpg = UnitConverter.kmPerLiterToMpg(kmL)
        val back = UnitConverter.mpgToKmPerLiter(mpg)
        assertEquals(kmL, back, 0.001)
    }

    @Test
    fun `L por 100 km se invierte correctamente`() {
        val kmL = 10.0
        val l100 = UnitConverter.kmPerLiterToLPer100Km(kmL)
        assertEquals(10.0, l100, 0.001)
    }
}