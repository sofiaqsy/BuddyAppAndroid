package com.buddy.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Buddy tiene su propio lenguaje visual — Material 3 es solo el sistema de
 * implementación. El color scheme mapea los tokens de marca a los slots M3
 * para que los componentes de sistema (ripple, sheets, snackbars) hereden
 * la paleta cálida sin redecorar cada uso.
 *
 * Light-only por ahora: iOS no define modo oscuro todavía.
 */
private val BuddyLightScheme: ColorScheme = lightColorScheme(
    primary            = BuddyColor.Brand,
    onPrimary          = BuddyColor.InkInverse,
    primaryContainer   = BuddyColor.SurfaceRaised,
    onPrimaryContainer = BuddyColor.Ink,
    secondary          = BuddyColor.Accent,
    onSecondary        = BuddyColor.InkInverse,
    background         = BuddyColor.Canvas,
    onBackground       = BuddyColor.Ink,
    surface            = BuddyColor.Surface,
    onSurface          = BuddyColor.Ink,
    surfaceVariant     = BuddyColor.SurfaceRaised,
    onSurfaceVariant   = BuddyColor.InkMuted,
    outline            = BuddyColor.Border,
    outlineVariant     = BuddyColor.Hairline,
    error              = BuddyColor.ErrorRed,
    onError            = BuddyColor.InkInverse,
)

@Composable
fun BuddyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BuddyLightScheme,
        content = content,
    )
}
