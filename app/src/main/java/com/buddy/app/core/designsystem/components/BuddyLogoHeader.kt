package com.buddy.app.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.buddy.app.R

/**
 * Cabecera con el logo de Buddy, centrado y fijo arriba — espejo del
 * ToolbarItem(placement: .principal) { Image("BuddyLogo") } de iOS en Inicio y
 * Trips. Mismo alto: 30.
 */
@Composable
fun BuddyLogoHeader(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.buddy_logo),
            contentDescription = "Buddy",
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(30.dp),
        )
    }
}
