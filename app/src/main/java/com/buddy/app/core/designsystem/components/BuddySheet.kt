package com.buddy.app.core.designsystem.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius
import com.buddy.app.core.designsystem.Spacing

/**
 * Bottom sheet estándar — adaptación Android de los .sheet de iOS.
 * Canvas cálido, esquinas xl (24). Los sheets iOS se convierten en
 * ModalBottomSheet (convención Android, cambio mandatorio de plataforma).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuddySheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
        containerColor = BuddyColor.Canvas,
        content = content,
    )
}

/** Header de sección — título bold + acción opcional a la derecha (patrón iOS). */
@Composable
fun BuddySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.edge, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = BuddyType.Title3, color = BuddyColor.Ink, modifier = Modifier.weight(1f))
        if (actionText != null && onAction != null) {
            BuddyTextButton(actionText, onClick = onAction)
        }
    }
}
