package com.sierracode.autocontrol.core.format

import com.sierracode.autocontrol.data.local.entity.VehicleEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VehicleFormatTest {

    @Test
    fun `displayName con nickname devuelve nickname`() {
        val vehicle = createVehicle(
            brand = "Ford",
            model = "Fiesta",
            trim = "ST",
            nickname = "Rayo McQueen"
        )
        assertEquals("Rayo McQueen", vehicle.displayName())
    }

    @Test
    fun `displayName sin nickname devuelve brand model trim`() {
        val vehicle = createVehicle(
            brand = "Volkswagen",
            model = "Golf",
            trim = "GTI",
            nickname = null
        )
        assertEquals("Volkswagen Golf GTI", vehicle.displayName())
    }

    @Test
    fun `displayName con trim nulo o vacio omite trim`() {
        val vehicleWithNullTrim = createVehicle(
            brand = "Toyota",
            model = "Corolla",
            trim = null,
            nickname = null
        )
        assertEquals("Toyota Corolla", vehicleWithNullTrim.displayName())

        val vehicleWithEmptyTrim = createVehicle(
            brand = "Toyota",
            model = "Corolla",
            trim = "   ",
            nickname = ""
        )
        assertEquals("Toyota Corolla", vehicleWithEmptyTrim.displayName())
    }

    @Test
    fun `subtitle sin nickname devuelve anio motor y placas`() {
        val vehicle = createVehicle(
            brand = "Ford",
            model = "Fiesta",
            year = 2018,
            engine = "1.6 Turbo",
            cylinders = 4,
            plates = "XYZ-123",
            nickname = null
        )
        assertEquals("2018 • 1.6 Turbo • XYZ-123", vehicle.subtitle())
    }

    @Test
    fun `subtitle con nickname empieza con brand model trim`() {
        val vehicle = createVehicle(
            brand = "Ford",
            model = "Fiesta",
            trim = "ST",
            year = 2018,
            engine = "1.6 Turbo",
            cylinders = 4,
            plates = "XYZ-123",
            nickname = "Rayo McQueen"
        )
        assertEquals("Ford Fiesta ST • 2018 • 1.6 Turbo • XYZ-123", vehicle.subtitle())
    }

    @Test
    fun `subtitle sin motor pero con cilindros usa N cil`() {
        val vehicle = createVehicle(
            brand = "Honda",
            model = "Civic",
            year = 2020,
            engine = null,
            cylinders = 4,
            plates = "ABC-456",
            nickname = null
        )
        assertEquals("2020 • 4 cil. • ABC-456", vehicle.subtitle())
    }

    @Test
    fun `subtitle sin placas omite placas`() {
        val vehicle = createVehicle(
            brand = "Mazda",
            model = "3",
            year = 2022,
            engine = "2.5L",
            cylinders = 4,
            plates = null,
            nickname = null
        )
        assertEquals("2022 • 2.5L", vehicle.subtitle())
    }

    @Test
    fun `subtitle con nickname sin trim omite trim en el prefijo`() {
        val vehicle = createVehicle(
            brand = "Nissan",
            model = "Tsuru",
            trim = null,
            year = 2010,
            engine = null,
            cylinders = 4,
            plates = null,
            nickname = "El Inmortal"
        )
        assertEquals("Nissan Tsuru • 2010 • 4 cil.", vehicle.subtitle())
    }

    @Test
    fun `subtitle con todo vacio salvo anio devuelve solo anio`() {
        val vehicle = createVehicle(
            brand = "Chevrolet",
            model = "Chevy",
            year = 2005,
            nickname = null,
            engine = null,
            cylinders = null,
            plates = null
        )
        assertEquals("2005", vehicle.subtitle())
    }

    private fun createVehicle(
        brand: String = "Test",
        model: String = "Car",
        year: Int = 2020,
        trim: String? = null,
        nickname: String? = null,
        engine: String? = null,
        cylinders: Int? = null,
        plates: String? = null
    ): VehicleEntity {
        return VehicleEntity(
            brand = brand,
            model = model,
            year = year,
            trim = trim,
            nickname = nickname,
            engine = engine,
            cylinders = cylinders,
            plates = plates
        )
    }
}
