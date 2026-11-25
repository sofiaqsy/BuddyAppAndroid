package com.buddy.app.core.designsystem.components

import androidx.compose.material3.Badge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.buddy.app.core.designsystem.BuddyColor

/** Círculo rojo con el número de pendientes. Uno solo para la pestaña
 *  Conexiones y las filas de conversaciones, como iOS (Keyla, 25 Nov 2026). */
@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    Badge(containerColor = BuddyColor.ErrorRed,
          modifier = modifier.semantics { contentDescription = "$count sin responder" }) {
        Text("${count.coerceAtMost(99)}")
    }
}
