package com.buddy.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.Radius
import com.buddy.app.core.designsystem.Spacing

/**
 * Card estándar — espejo del patrón iOS:
 * surface blanca, radius lg (20), padding lg (24), cardShadow suave
 * (iOS: black 6%, radius 10, y 4 → aquí elevation baja equivalente).
 */
@Composable
fun BuddyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = Spacing.lg,
    containerColor: Color = BuddyColor.Surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.lg)
    Surface(
        modifier = modifier
            .shadow(elevation = 3.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.06f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = containerColor,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Fila agrupada — fondo surfaceRaised, radius sm (14). Para listas de opciones. */
@Composable
fun BuddyGroupedRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .background(BuddyColor.SurfaceRaised, RoundedCornerShape(Radius.sm))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Spacing.md),
    ) { content() }
}
