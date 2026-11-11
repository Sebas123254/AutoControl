package com.sierracode.autocontrol.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("settings")

@Singleton
class SettingsStore @Inject constructor(private val context: Context) {

    private object K {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val ACTIVE_VEHICLE = stringPreferencesKey("active_vehicle")
        val DARK = booleanPreferencesKey("dark_mode")
        val CURRENCY = stringPreferencesKey("currency")
        val UNITS = stringPreferencesKey("units") // km | mi
        val IS_PRO = booleanPreferencesKey("is_pro")
    }

    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[K.ONBOARDED] ?: false }
    val activeVehicleId: Flow<String?> = context.dataStore.data.map { it[K.ACTIVE_VEHICLE] }
    val darkMode: Flow<Boolean?> = context.dataStore.data.map { it[K.DARK] }
    val currency: Flow<String> = context.dataStore.data.map { it[K.CURRENCY] ?: "MXN" }
    val isPro: Flow<Boolean> = context.dataStore.data.map { it[K.IS_PRO] ?: false }

    suspend fun setOnboarded(v: Boolean) = context.dataStore.edit { it[K.ONBOARDED] = v }
    suspend fun setActiveVehicle(id: String?) = context.dataStore.edit { p ->
        if (id == null) p.remove(K.ACTIVE_VEHICLE) else p[K.ACTIVE_VEHICLE] = id
    }
    suspend fun setDark(v: Boolean?) = context.dataStore.edit { p ->
        if (v == null) p.remove(K.DARK) else p[K.DARK] = v
    }
    suspend fun setPro(v: Boolean) = context.dataStore.edit { it[K.IS_PRO] = v }
}