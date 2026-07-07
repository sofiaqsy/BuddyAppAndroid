package com.buddy.app.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Color tokens — traducción 1:1 de DesignSystem.swift (iOS).
 * Single source of truth: las vistas consumen SOLO estos tokens, nunca hex directos.
 *
 * Buddy Brown (#6E3B2D) es el color primario de marca.
 * Soft sage (#6F9885) es el único accent permitido (disponibilidad / éxito).
 */
object BuddyColor {
    // ── Backgrounds ────────────────────────────────────────────────
    val Canvas        = Color(0xFFF8F4EE)   // App background — warm off-white
    val Surface       = Color(0xFFFFFFFF)   // Cards — pure white
    val SurfaceRaised = Color(0xFFF3EEE8)   // Secondary bg / grouped rows
    val GroupedBg     = Color(0xFFECE4DB)   // Sections, pickers

    // ── Text ────────────────────────────────────────────────────────
    val Ink        = Color(0xFF2B1C18)   // Primary text — dark brown, never black
    val InkMuted   = Color(0xFF6F625D)   // Secondary text
    val InkFaint   = Color(0xFFB8AEA8)   // Placeholders / hints
    val InkInverse = Color(0xFFFFFFFF)   // Text on dark (CTAs)

    // ── Brand ───────────────────────────────────────────────────────
    val Brand         = Color(0xFF6E3B2D)   // CTAs, icons, active states, links
    val BrandHover    = Color(0xFF7B4435)   // Pressed / hover
    val BrandDeep     = Color(0xFF2B1C18)   // Dark backgrounds, deepest text
    val BrandDisabled = Color(0xFFCFC6BF)
    val BrandGradientDark = Color(0xFF4A2820) // dark end of brand gradient

    // ── Accent ──────────────────────────────────────────────────────
    // Solo para: available, success, location, buddy online. Nunca CTAs.
    val Accent = Color(0xFF6F9885)

    // ── Semantic ────────────────────────────────────────────────────
    val WarningAmber = Color(0xFFC48A3A)
    val ErrorRed     = Color(0xFFB65B55)

    // ── Borders / Lines ─────────────────────────────────────────────
    val Border   = Color(0xFFE6DDD5)
    val Hairline = Color(0xFFEFE8E2)

    // ── Tab Bar ─────────────────────────────────────────────────────
    val TabBarBg       = Color(0xFFFBF8F4)
    val TabBarInactive = Color(0xFF8A7D76)
}
