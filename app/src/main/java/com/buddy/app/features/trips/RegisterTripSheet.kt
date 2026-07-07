package com.buddy.app.features.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.buddy.app.core.data.model.ApiPlaceResult
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.core.designsystem.components.BuddySheet
import com.buddy.app.core.designsystem.components.BuddyTextField

/**
 * Registro de trip — versión compacta del RegisterTripView de iOS:
 * busca el lugar (mismo /search/places) y crea el journey al elegirlo.
 * Fechas y detalles llegan en una iteración posterior.
 */
@Composable
fun RegisterTripSheet(
    results: List<ApiPlaceResult>,
    onQueryChange: (String) -> Unit,
    onSelect: (ApiPlaceResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    BuddySheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.edge)) {
            Text("¿A dónde viajas?", style = BuddyType.Title2, color = BuddyColor.Ink)
            Spacer(Modifier.height(Spacing.sm))
            BuddyTextField(
                value = query,
                onValueChange = { query = it; onQueryChange(it) },
                placeholder = "Busca una ciudad o lugar…",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.sm))
            results.forEach { place ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(place) }
                        .padding(vertical = Spacing.sm),
                ) {
                    Text(place.title, style = BuddyType.Headline, color = BuddyColor.Ink)
                    if (place.subtitle != null) {
                        Text(place.subtitle, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
                    }
                }
                HorizontalDivider(color = BuddyColor.Hairline)
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
