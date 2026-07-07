package com.buddy.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Placeholder temporal de Fase 1 — se reemplaza al portar cada pantalla. */
@Composable
fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(BuddyColor.Canvas),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = title, style = BuddyType.Title2, color = BuddyColor.InkMuted)
    }
}
