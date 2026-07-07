package com.buddy.app.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Spacing

/** Loading centrado — spinner brand sobre canvas. */
@Composable
fun BuddyLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BuddyColor.Brand, strokeWidth = 2.5.dp)
    }
}

/**
 * Empty state — espejo del patrón iOS: icono suave, título, mensaje muted,
 * CTA opcional. Calmo y premium, nunca ilustración genérica de Material.
 */
@Composable
fun BuddyEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(icon, contentDescription = null, Modifier.size(44.dp), tint = BuddyColor.InkFaint)
        Text(title, style = BuddyType.Headline, color = BuddyColor.Ink, textAlign = TextAlign.Center)
        Text(message, style = BuddyType.Subhead, color = BuddyColor.InkMuted, textAlign = TextAlign.Center)
        if (actionText != null && onAction != null) {
            BuddyTextButton(actionText, onClick = onAction)
        }
    }
}
