package com.buddy.app.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius

/**
 * CTA primario — espejo del patrón iOS: fondo Buddy Brown, texto blanco
 * footnoteBold, radius md (16), padding vertical 16, spinner blanco en loading.
 * Haptic al pulsar (equivale a Haptic.medium() de iOS).
 */
@Composable
fun BuddyPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = BuddyColor.Brand,
            contentColor = BuddyColor.InkInverse,
            disabledContainerColor = BuddyColor.BrandDisabled,
            disabledContentColor = BuddyColor.InkInverse,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = BuddyColor.InkInverse,
                strokeWidth = 2.dp,
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) Icon(icon, contentDescription = null, Modifier.size(18.dp))
                Text(text, style = BuddyType.FootnoteBold)
            }
        }
    }
}

/** Botón secundario — borde brand, fondo transparente. */
@Composable
fun BuddySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = RoundedCornerShape(Radius.md),
        border = BorderStroke(1.dp, BuddyColor.Brand),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = BuddyColor.Brand),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        Text(text, style = BuddyType.FootnoteBold)
    }
}

/** Link textual — brand, sin contenedor. */
@Composable
fun BuddyTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, style = BuddyType.FootnoteBold, color = BuddyColor.Brand)
    }
}
