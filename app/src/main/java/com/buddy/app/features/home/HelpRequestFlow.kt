package com.buddy.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.core.designsystem.components.BuddyPrimaryButton

/**
 * Vista del selector de categoría para pedir ayuda.
 * Espejo de CategoryPickerView (iOS).
 */
@Composable
fun CategoryPickerView(
    buddyCount: Int,
    destinationName: String?,
    onRequest: (category: String, description: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BuddyColor.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.edge, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            "¿En qué necesitas ayuda?",
            style = BuddyType.DisplayMedium,
            color = BuddyColor.Ink,
        )

        if (destinationName != null) {
            Text(
                "en $destinationName",
                style = BuddyType.Headline,
                color = BuddyColor.Brand,
            )
        }

        Text(
            "$buddyCount buddies disponibles ahora",
            style = BuddyType.Caption1,
            color = BuddyColor.InkMuted,
        )

        // Categorías de ayuda
        val categories = listOf(
            HelpCategory("transport", "Transporte", Icons.Default.Directions),
            HelpCategory("accommodation", "Alojamiento", Icons.Default.Favorite),
            HelpCategory("food", "Comida", Icons.Default.Coffee),
            HelpCategory("other", "Otra cosa", Icons.Default.Info),
        )

        categories.forEach { cat ->
            CategoryCard(
                category = cat,
                isSelected = selectedCategory == cat.id,
                onClick = { selectedCategory = cat.id },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Descripción opcional
        TextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Detalles (opcional)") },
            modifier = Modifier
                .fillMaxWidth()
                .background(BuddyColor.Surface, RoundedCornerShape(Radius.md)),
            maxLines = 3,
        )

        BuddyPrimaryButton(
            text = "Buscar buddy",
            enabled = selectedCategory != null,
            onClick = { onRequest(selectedCategory!!, description.takeIf { it.isNotEmpty() }) },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Spacing.xl))
    }
}

@Composable
private fun CategoryCard(
    category: HelpCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(
                if (isSelected) BuddyColor.Brand.copy(alpha = 0.1f) else BuddyColor.Surface
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) BuddyColor.Brand else BuddyColor.Border,
                shape = RoundedCornerShape(Radius.md)
            )
            .clickable(onClick = onClick)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = if (isSelected) BuddyColor.Brand else BuddyColor.InkMuted,
            modifier = Modifier.size(24.dp),
        )

        Text(
            category.label,
            style = BuddyType.Headline,
            color = if (isSelected) BuddyColor.Brand else BuddyColor.Ink,
            modifier = Modifier.weight(1f),
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = if (isSelected) BuddyColor.Brand else BuddyColor.InkMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Vista de búsqueda en progreso.
 * Espejo de SearchingView (iOS).
 */
@Composable
fun SearchingView(
    buddyCount: Int,
    isExpandingSearch: Boolean,
    category: String?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryLabel = when (category) {
        "transport" -> "Transporte"
        "accommodation" -> "Alojamiento"
        "food" -> "Comida"
        "translation" -> "Traducir"
        "activities" -> "Qué hacer"
        "emergency" -> "Seguridad"
        else -> "Ayuda"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BuddyColor.Canvas)
            .padding(Spacing.edge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            color = BuddyColor.Brand,
            modifier = Modifier.size(56.dp),
            strokeWidth = 3.dp,
        )

        Spacer(modifier = Modifier.height(Spacing.xl))

        Text(
            if (isExpandingSearch) "Ampliando búsqueda…" else "Buscando buddy…",
            style = BuddyType.Headline,
            color = BuddyColor.Ink,
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        Text(
            "Para: $categoryLabel",
            style = BuddyType.Callout,
            color = BuddyColor.Brand,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        Text(
            "Te conectaremos con la primera persona disponible",
            style = BuddyType.Caption1,
            color = BuddyColor.InkMuted,
        )

        Spacer(modifier = Modifier.weight(1f))

        BuddyPrimaryButton(
            text = "Cancelar",
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg),
        )
    }
}

/**
 * Vista cuando se encuentra un buddy.
 * Aquí se muestra el perfil del buddy y opciones para aceptar/rechazar.
 */
@Composable
fun MatchedBuddyView(
    buddyName: String,
    buddyAvatarUrl: String?,
    buddyRating: Float = 4.8f,
    reviewCount: Int = 23,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BuddyColor.Canvas)
            .padding(Spacing.edge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("¡Buddy encontrado!", style = BuddyType.DisplayMedium, color = BuddyColor.Ink)

        Spacer(modifier = Modifier.height(Spacing.xl))

        // Avatar del buddy
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(Radius.lg))
                .background(BuddyColor.SurfaceRaised)
                .border(3.dp, BuddyColor.Brand.copy(alpha = 0.2f), RoundedCornerShape(Radius.lg)),
            contentAlignment = Alignment.Center,
        ) {
            if (buddyAvatarUrl != null) {
                AsyncImage(
                    model = buddyAvatarUrl,
                    contentDescription = buddyName,
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.lg))

        Text(buddyName, style = BuddyType.Title2, color = BuddyColor.Ink)

        Spacer(modifier = Modifier.height(Spacing.sm))

        Text(
            "⭐ $buddyRating • $reviewCount reseñas",
            style = BuddyType.Callout,
            color = BuddyColor.InkMuted,
        )

        Spacer(modifier = Modifier.weight(1f))

        BuddyPrimaryButton(
            text = "¡Conectar con $buddyName!",
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth(),
        )

        BuddySecondaryButton(
            text = "Ver otro buddy",
            onClick = onDecline,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        )

        Spacer(modifier = Modifier.height(Spacing.lg))
    }
}

@Composable
fun BuddySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = BuddyColor.Surface,
            disabledContainerColor = BuddyColor.Surface.copy(alpha = 0.5f),
        ),
        enabled = enabled,
    ) {
        Text(text, color = BuddyColor.Ink, style = BuddyType.Headline)
    }
}

data class HelpCategory(
    val id: String,
    val label: String,
    val icon: ImageVector,
)
