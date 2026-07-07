package com.buddy.app.core.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Escala tipográfica — espejo de `enum BT` (iOS).
 * Tamaños mapeados desde los text styles de SwiftUI:
 * largeTitle 34, title 28, title3 20, callout 16, subheadline 15, caption 12.
 */
object BuddyType {
    val DisplayXL     = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 41.sp)
    val DisplayHero   = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
    val DisplayLarge  = DisplayHero
    val Title1        = DisplayHero
    val DisplayMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp)
    val Title2        = DisplayMedium
    val Title3        = DisplayMedium
    val Subhead       = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
    val Headline      = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
    val Body          = TextStyle(fontSize = 16.sp, lineHeight = 21.sp)
    val Callout       = Body
    val Footnote      = Body
    val FootnoteBold  = Headline
    val Caption1      = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val Caption2      = Caption1
    val Eyebrow       = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
}
