package com.buddy.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Botón del mapa (cuadrado redondeado crema, como iOS MapIconButton): el mismo en el
 * mapa de un lugar y en "Buscar en el mapa" para acercar, alejar y mi ubicación
 * (Keyla, 25 Nov 2026: reusar el componente).
 */
@Composable
fun MapControlButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    iconRotation: Float = 0f,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(52.dp)
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFFBF2))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.Black,
             modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = iconRotation })
    }
}
