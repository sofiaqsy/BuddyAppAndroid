package com.buddy.app.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Color tokens — traducción 1:1 de DesignSystem.swift (iOS).
 * Single source of truth: las vistas consumen SOLO estos tokens, nunca hex directos.
 *
 * Paleta Villa Rica "Forest & Terracotta" (Keyla, 19-20 Nov 2026), igual que
 * DesignSystem.swift: forest primario, sage secundario, charcoal para texto,
 * terracota solo para formas (nunca texto chico).
 */
object BuddyColor {
    // ── Backgrounds ────────────────────────────────────────────────
    val Canvas        = Color(0xFFF5F5F0)   // App background — warm off-white
    val Surface       = Color(0xFFFFFFFF)   // Cards — pure white
    val SurfaceRaised = Color(0xFFEEF0EA)   // Secondary bg / grouped rows
    val GroupedBg     = Color(0xFFE4E2DC)   // Sections, pickers

    // ── Text ────────────────────────────────────────────────────────
    val Ink        = Color(0xFF2E302E)   // Primary text — dark brown, never black
    val InkMuted   = Color(0xFF5B6159)   // Secondary text
    val InkFaint   = Color(0xFFA9AEA5)   // Placeholders / hints
    val InkInverse = Color(0xFFFFFFFF)   // Text on dark (CTAs)

    // ── Brand ───────────────────────────────────────────────────────
    val Brand         = Color(0xFF2F4A3E)   // CTAs, icons, active states, links
    val BrandHover    = Color(0xFF7B4435)   // Pressed / hover
    val BrandDeep     = Color(0xFF2B1C18)   // Dark backgrounds, deepest text
    val BrandDisabled = Color(0xFFCFC6BF)
    val BrandGradientDark = Color(0xFF4A2820) // dark end of brand gradient

    // ── Accent ──────────────────────────────────────────────────────
    // Solo para: available, success, location, buddy online. Nunca CTAs.
    val Accent = Color(0xFF6F8F6A)

    // ── Semantic ────────────────────────────────────────────────────
    val WarningAmber = Color(0xFFC48A3A)
    val ErrorRed     = Color(0xFFB65B55)

    // ── Borders / Lines ─────────────────────────────────────────────
    val Border   = Color(0xFFD9DED5)
    val Hairline = Color(0xFFE6E9E2)

    // ── Tab Bar ─────────────────────────────────────────────────────
    val TabBarBg       = Color(0xFFFBF8F4)
    val TabBarInactive = Color(0xFF5B6159)

    // ── Villa Rica (iOS 20 Nov 2026) ────────────────────────────────
    val Terracotta = Color(0xFFC87950)   // solo formas, nunca texto chico
    val Sage       = Color(0xFFA8B69B)
    val PaleSage   = Color(0xFFE3EADD)   // fondo de íconos de categoría
    val SageTint   = Color(0xFFE3EADD)   // botones secundarios ("Ver todos")
}
