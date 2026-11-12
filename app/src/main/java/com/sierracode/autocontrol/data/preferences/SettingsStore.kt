package com.sierracode.autocontrol.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("settings")

enum class UnitSystem { METRIC, US }
enum class EfficiencyFormat { KM_PER_L, L_PER_100KM, MPG_US }

@Singleton
class SettingsStore @Inject constructor(private val context: Context) {

    private object K {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val ACTIVE_VEHICLE = stringPreferencesKey("active_vehicle")
        val DARK = booleanPreferencesKey("dark_mode")
        val IS_PRO = booleanPreferencesKey("is_pro")
        val CURRENCY = stringPreferencesKey("currency_code")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val EFFICIENCY_FORMAT = stringPreferencesKey("efficiency_format")
    }

    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[K.ONBOARDED] ?: false }
    val activeVehicleId: Flow<String?> = context.dataStore.data.map { it[K.ACTIVE_VEHICLE] }
    val darkMode: Flow<Boolean?> = context.dataStore.data.map { it[K.DARK] }
    val isPro: Flow<Boolean> = context.dataStore.data.map { it[K.IS_PRO] ?: false }
    val currencyCode: Flow<String> = context.dataStore.data.map { it[K.CURRENCY] ?: "MXN" }
    val unitSystem: Flow<UnitSystem> = context.dataStore.data.map {
        if (it[K.UNIT_SYSTEM] == UnitSystem.US.name) UnitSystem.US else UnitSystem.METRIC
    }
    val efficiencyFormat: Flow<EfficiencyFormat> = context.dataStore.data.map {
        try { EfficiencyFormat.valueOf(it[K.EFFICIENCY_FORMAT] ?: EfficiencyFormat.KM_PER_L.name) }
        catch (e: Exception) { EfficiencyFormat.KM_PER_L }
    }

    suspend fun setOnboarded(v: Boolean) = context.dataStore.edit { it[K.ONBOARDED] = v }
    suspend fun setActiveVehicle(id: String?) = context.dataStore.edit { p ->
        if (id == null) p.remove(K.ACTIVE_VEHICLE) else p[K.ACTIVE_VEHICLE] = id
    }
    suspend fun setDark(v: Boolean?) = context.dataStore.edit { p ->
        if (v == null) p.remove(K.DARK) else p[K.DARK] = v
    }
    suspend fun setPro(v: Boolean) = context.dataStore.edit { it[K.IS_PRO] = v }
    suspend fun setCurrency(code: String) = context.dataStore.edit { it[K.CURRENCY] = code }

    // LÓGICA AÑADIDA: Auto-ajustar formato de eficiencia al cambiar sistema
    suspend fun setUnitSystem(system: UnitSystem) {
        context.dataStore.edit {
            it[K.UNIT_SYSTEM] = system.name
            if (system == UnitSystem.US) {
                it[K.EFFICIENCY_FORMAT] = EfficiencyFormat.MPG_US.name
            } else if (system == UnitSystem.METRIC && it[K.EFFICIENCY_FORMAT] == EfficiencyFormat.MPG_US.name) {
                it[K.EFFICIENCY_FORMAT] = EfficiencyFormat.KM_PER_L.name
            }
        }
    }
    suspend fun setEfficiencyFormat(format: EfficiencyFormat) = context.dataStore.edit { it[K.EFFICIENCY_FORMAT] = format.name }
}