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
                settingsStore.isPro
            ) { c, u, e, p -> AppConfig(c, u, e, p) }
        } else {
            combine(
                vehicleRepo.observe(id),
                settingsStore.efficiencyFormat,
                settingsStore.isPro
            ) { vehicle, eff, pro ->
                if (vehicle != null) {
                    AppConfig(
                        currencyCode = vehicle.currencyCode,
                        unitSystem = if (vehicle.unitSystem == "METRIC") UnitSystem.METRIC else UnitSystem.US,
                        efficiencyFormat = if (vehicle.unitSystem == "METRIC") eff else EfficiencyFormat.MPG_US,
                        isPro = pro
                    )
                } else {
                    AppConfig()
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppConfig())
}