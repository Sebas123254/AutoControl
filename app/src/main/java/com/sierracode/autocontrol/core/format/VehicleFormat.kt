package com.sierracode.autocontrol.core.format

import com.sierracode.autocontrol.data.local.entity.VehicleEntity

fun VehicleEntity.displayName(): String {
    val nick = nickname?.trim()
    if (!nick.isNullOrEmpty()) {
        return nick
    }
    return fullBrandModelTrim()
}

fun VehicleEntity.subtitle(): String {
    val parts = mutableListOf<String>()
    val nick = nickname?.trim()
    if (!nick.isNullOrEmpty()) {
        val full = fullBrandModelTrim()
        if (full.isNotEmpty()) {
            parts.add(full)
        }
    }
    if (year > 0) {
        parts.add(year.toString())
    }
    val engineText = engine?.trim()
    val motorOrCyl = when {
        !engineText.isNullOrEmpty() -> engineText
        cylinders != null && cylinders > 0 -> "$cylinders cil."
        else -> null
    }
    if (motorOrCyl != null) {
        parts.add(motorOrCyl)
    }
    val plateText = plates?.trim()
    if (!plateText.isNullOrEmpty()) {
        parts.add(plateText)
    }
    return parts.joinToString(" • ")
}

private fun VehicleEntity.fullBrandModelTrim(): String {
    return listOfNotNull(
        brand.trim().takeIf { it.isNotEmpty() },
        model.trim().takeIf { it.isNotEmpty() },
        trim?.trim()?.takeIf { it.isNotEmpty() }
    ).joinToString(" ")
}
