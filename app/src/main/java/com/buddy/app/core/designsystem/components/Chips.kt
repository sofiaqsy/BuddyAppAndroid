package com.buddy.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType

/**
 * Chip de selección — espejo de DestinationChip (iOS):
 * seleccionado = fondo brand + texto blanco; no = surfaceRaised + ink.
 */
@Composable
fun BuddyChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = BuddyType.FootnoteBold,
        color = if (selected) BuddyColor.InkInverse else BuddyColor.Ink,
        modifier = modifier
            .background(
                color = if (selected) BuddyColor.Brand else BuddyColor.SurfaceRaised,
                shape = RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Badge de estado — sage para disponible/success, amber para ocupado. */
@Composable
fun BuddyStatusBadge(text: String, available: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = BuddyType.Eyebrow,
        color = BuddyColor.InkInverse,
        modifier = modifier
            .background(
                color = if (available) BuddyColor.Accent else BuddyColor.WarningAmber,
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
