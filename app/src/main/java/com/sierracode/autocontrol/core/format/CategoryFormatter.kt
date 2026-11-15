package com.sierracode.autocontrol.core.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sierracode.autocontrol.R

@Composable
fun localizedCategoryName(categoryId: String, fallbackName: String): String {
    return when (categoryId) {
        "fuel_gas" -> stringResource(R.string.cat_fuel_gas)
        "fuel_diesel" -> stringResource(R.string.cat_fuel_diesel)
        "fuel_gas_lp" -> stringResource(R.string.cat_fuel_gas_lp)
        "fuel_ev" -> stringResource(R.string.cat_fuel_ev)
        "maint_oil" -> stringResource(R.string.cat_maint_oil)
        "maint_filters" -> stringResource(R.string.cat_maint_filters)
        "maint_brakes" -> stringResource(R.string.cat_maint_brakes)
        "maint_susp" -> stringResource(R.string.cat_maint_susp)
        "maint_align" -> stringResource(R.string.cat_maint_align)
        "maint_balance" -> stringResource(R.string.cat_maint_balance)
        "maint_coolant" -> stringResource(R.string.cat_maint_coolant)
        "maint_battery" -> stringResource(R.string.cat_maint_battery)
        "maint_spark" -> stringResource(R.string.cat_maint_spark)
        "maint_trans" -> stringResource(R.string.cat_maint_trans)
        "maint_ac" -> stringResource(R.string.cat_maint_ac)
        "maint_general" -> stringResource(R.string.cat_maint_general)
        "rep_motor" -> stringResource(R.string.cat_rep_motor)
        "rep_trans" -> stringResource(R.string.cat_rep_trans)
        "rep_elec" -> stringResource(R.string.cat_rep_elec)
        "rep_body" -> stringResource(R.string.cat_rep_body)
        "rep_steer" -> stringResource(R.string.cat_rep_steer)
        "other_ins" -> stringResource(R.string.cat_other_ins)
        "other_tax" -> stringResource(R.string.cat_other_tax)
        "other_plates" -> stringResource(R.string.cat_other_plates)
        "other_verif" -> stringResource(R.string.cat_other_verif)
        "other_wash" -> stringResource(R.string.cat_other_wash)
        "other_park" -> stringResource(R.string.cat_other_park)
        "other_acc" -> stringResource(R.string.cat_other_acc)
        "other_misc" -> stringResource(R.string.cat_other_misc)
        "mod_audio" -> stringResource(R.string.cat_mod_audio)
        "mod_susp" -> stringResource(R.string.cat_mod_susp)
        "mod_exhaust" -> stringResource(R.string.cat_mod_exhaust)
        "mod_light" -> stringResource(R.string.cat_mod_light)
        "mod_style" -> stringResource(R.string.cat_mod_style)
        "mod_perf" -> stringResource(R.string.cat_mod_perf)
        "mod_interior" -> stringResource(R.string.cat_mod_interior)
        "mod_wheels" -> stringResource(R.string.cat_mod_wheels)
        "mod_other" -> stringResource(R.string.cat_mod_other)
        else -> fallbackName
    }
}

@Composable
fun localizedPriority(priority: String): String {
    return when (priority) {
        "ALTA" -> stringResource(R.string.priority_alta)
        "MEDIA" -> stringResource(R.string.priority_media)
        "BAJA" -> stringResource(R.string.priority_baja)
        else -> priority
    }
}