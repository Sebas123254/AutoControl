package com.sierracode.autocontrol.core.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// PALETA DE MARCA (sin cambios respecto a la original)
// ============================================================
val Graphite     = Color(0xFF1F242C)  // primary light
val GraphiteDark = Color(0xFF14181E)
val Steel        = Color(0xFF3A4757)  // secondary light
val Amber        = Color(0xFFE6A23C)  // tertiary dark / warning dark
val Teal         = Color(0xFF2FA39A)  // secondary dark / success dark
val Crimson      = Color(0xFFC0392B)  // error light

// ============================================================
// VARIANTES ACCESIBLES PARA TEMA OSCURO
// Corrigen ratios < 4.5:1 detectados en Fase 1 (Bloque 1 §2).
// ============================================================
// primary dark: 9.32:1 sobre #0E1116, 8.20:1 sobre #1A1F27
val SteelLight   = Color(0xFFA8B8CC)
// error dark:   8.68:1 sobre #0E1116
val CrimsonLight = Color(0xFFEF9A8E)

// ============================================================
// SEMÁNTICOS (light) — para StatusChip y textos de estado
// ============================================================
// 5.64:1 sobre #F6F7F9, 6.04:1 sobre #FFFFFF
val WarningLight = Color(0xFF8A5A0C)
// 5.33:1 sobre #F6F7F9
val SuccessLight = Color(0xFF166E67)
// 4.78:1 sobre #F6F7F9
val InfoLight    = Color(0xFF1F6FA8)

// ============================================================
// SEMÁNTICOS (dark)
// ============================================================
// 8.64:1 sobre #0E1116
val WarningDark  = Color(0xFFE6A23C)
// 6.16:1 sobre #0E1116
val SuccessDark  = Color(0xFF2FA39A)
// 9.69:1 sobre #0E1116
val InfoDark     = Color(0xFF7FBEE6)

// ============================================================
// SURFACES (sin cambios)
// ============================================================
val SurfaceLight = Color(0xFFF6F7F9)
val SurfaceDark  = Color(0xFF0E1116)
val CardLight    = Color(0xFFFFFFFF)
val CardDark     = Color(0xFF1A1F27)