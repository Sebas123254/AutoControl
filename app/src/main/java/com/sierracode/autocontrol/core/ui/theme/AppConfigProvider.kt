package com.sierracode.autocontrol.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.sierracode.autocontrol.data.preferences.EfficiencyFormat
import com.sierracode.autocontrol.data.preferences.UnitSystem

data class AppConfig(
    val currencyCode: String = "MXN",
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val efficiencyFormat: EfficiencyFormat = EfficiencyFormat.KM_PER_L
)

val LocalAppConfig = staticCompositionLocalOf { AppConfig() }