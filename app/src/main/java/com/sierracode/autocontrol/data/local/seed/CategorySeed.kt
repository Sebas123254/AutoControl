package com.sierracode.autocontrol.data.local.seed

import com.sierracode.autocontrol.data.local.entity.CategoryEntity

object CategorySeed {
    fun all(): List<CategoryEntity> = listOf(
        // FUEL
        c("fuel_gas", "FUEL", "Gasolina", "local_gas_station"),
        c("fuel_diesel", "FUEL", "Diésel", "local_gas_station"),
        c("fuel_gas_lp", "FUEL", "Gas LP", "propane_tank"),
        c("fuel_ev", "FUEL", "Carga eléctrica", "ev_station"),
        // MAINT
        c("maint_oil", "MAINT", "Aceite", "oil_barrel"),
        c("maint_filters", "MAINT", "Filtros", "filter_alt"),
        c("maint_brakes", "MAINT", "Frenos", "disc_full"),
        c("maint_susp", "MAINT", "Suspensión", "car_repair"),
        c("maint_align", "MAINT", "Alineación", "straighten"),
        c("maint_balance", "MAINT", "Balanceo", "tire_repair"),
        c("maint_coolant", "MAINT", "Refrigerante", "thermostat"),
        c("maint_battery", "MAINT", "Batería", "battery_charging_full"),
        c("maint_spark", "MAINT", "Bujías", "bolt"),
        c("maint_trans", "MAINT", "Transmisión", "settings"),
        c("maint_ac", "MAINT", "Aire acondicionado", "ac_unit"),
        c("maint_general", "MAINT", "Servicio general", "build"),
        // REPAIR (Se quitó el rep_other)
        c("rep_motor", "REPAIR", "Motor", "settings_suggest"),
        c("rep_trans", "REPAIR", "Transmisión", "sync_alt"),
        c("rep_elec", "REPAIR", "Sistema eléctrico", "electrical_services"),
        c("rep_body", "REPAIR", "Carrocería", "directions_car"),
        c("rep_steer", "REPAIR", "Dirección", "rotate_right"),
        // OTHER (Se renombró other_misc)
        c("other_ins", "OTHER", "Seguro", "verified_user"),
        c("other_tax", "OTHER", "Impuestos", "receipt_long"),
        c("other_plates", "OTHER", "Placas", "badge"),
        c("other_verif", "OTHER", "Verificación", "fact_check"),
        c("other_wash", "OTHER", "Lavado", "local_car_wash"),
        c("other_park", "OTHER", "Estacionamiento", "local_parking"),
        c("other_acc", "OTHER", "Accesorios", "shopping_bag"),
        c("other_misc", "OTHER", "Otro", "more_horiz"),
        // MOD
        c("mod_audio", "MOD", "Audio", "speaker"),
        c("mod_susp", "MOD", "Suspensión", "height"),
        c("mod_exhaust", "MOD", "Escape", "waves"),
        c("mod_light", "MOD", "Iluminación", "lightbulb"),
        c("mod_style", "MOD", "Estética", "palette"),
        c("mod_perf", "MOD", "Performance", "speed"),
        c("mod_interior", "MOD", "Interior", "chair"),
        c("mod_wheels", "MOD", "Rines/Llantas", "circle"),
        c("mod_other", "MOD", "Otras modificaciones", "tune")
    )
    private fun c(id: String, g: String, n: String, i: String) =
        CategoryEntity(id = id, group = g, name = n, iconKey = i)
}