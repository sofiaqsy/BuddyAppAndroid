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
    /** Abre a pantalla completa y sin paso intermedio.
     *
     *  Para contenido que ES una pantalla —una conversación, por ejemplo— y no
     *  una hoja de opciones: a media altura se ve un trozo de chat asomando
     *  desde abajo y hay que arrastrar para leerlo. skipPartiallyExpanded quita
     *  ese estado intermedio, así que abre directamente entera. */
    fullHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = fullHeight,
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
        containerColor = BuddyColor.Canvas,
        // Sin el "agarre" cuando ocupa toda la pantalla: no hay a dónde
        // arrastrarla, así que solo sería un adorno que promete un gesto.
        dragHandle = if (fullHeight) null else { { androidx.compose.material3.BottomSheetDefaults.DragHandle() } },
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
