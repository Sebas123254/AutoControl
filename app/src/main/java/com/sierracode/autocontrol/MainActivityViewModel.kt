package com.sierracode.autocontrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.core.ui.AppConfig
import com.sierracode.autocontrol.data.preferences.EfficiencyFormat
import com.sierracode.autocontrol.data.preferences.SettingsStore
import com.sierracode.autocontrol.data.preferences.UnitSystem
import com.sierracode.autocontrol.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val vehicleRepo: VehicleRepository
) : ViewModel() {

    val darkMode = settingsStore.darkMode.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val appConfig = settingsStore.activeVehicleId.flatMapLatest { id ->
        if (id == null) {
            combine(
                settingsStore.currencyCode,
                settingsStore.unitSystem,
                settingsStore.efficiencyFormat,
                settingsStore.isPro,
                settingsStore.dynamicColor
            ) { values ->
                AppConfig(
                    currencyCode = values[0] as String,
                    unitSystem = values[1] as UnitSystem,
                    efficiencyFormat = values[2] as EfficiencyFormat,
                    isPro = values[3] as Boolean,
                    dynamicColor = values[4] as Boolean
                )
            }
        } else {
            combine(
                vehicleRepo.observe(id),
                settingsStore.efficiencyFormat,
                settingsStore.isPro,
                settingsStore.dynamicColor
            ) { vehicle, eff, pro, dynamic ->
                if (vehicle != null) {
                    AppConfig(
                        currencyCode = vehicle.currencyCode,
                        unitSystem = if (vehicle.unitSystem == "METRIC") UnitSystem.METRIC else UnitSystem.US,
                        efficiencyFormat = if (vehicle.unitSystem == "METRIC") eff else EfficiencyFormat.MPG_US,
                        isPro = pro,
                        dynamicColor = dynamic
                    )
                } else {
                    AppConfig(dynamicColor = dynamic)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppConfig())
}