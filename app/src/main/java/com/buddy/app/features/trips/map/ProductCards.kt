package com.buddy.app.features.trips.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.buddy.app.core.data.model.FincasProduct
import com.buddy.app.core.data.model.soles
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType

/** Lado de las tarjetas de producto y de las fotos del panel (iOS cardSide). */
val ProductCardSide: Dp = 114.dp

/**
 * Cara de la tarjeta de producto (iOS ProductCardFace): foto cuadrada, nombre,
 * precio y tamaño de UNA presentación. La usan el panel del lugar y la fila
 * "Productos locales" de Inicio, así se ve igual en los dos sitios.
 */
@Composable
fun ProductCardFace(
    product: FincasProduct,
    pres: FincasProduct.Presentation?,
    modifier: Modifier = Modifier,
    side: Dp = ProductCardSide,
    photoModifier: Modifier = Modifier,
) {
    Column(modifier.width(side), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(side).clip(RoundedCornerShape(10.dp)).background(BuddyColor.PaleSage).then(photoModifier),
            contentAlignment = Alignment.Center,
        ) {
            val url = product.coverFor(pres)
            if (url != null) {
                // Sin crossfade: una foto ya en memoria se dibuja en el primer
                // cuadro y no parpadea al mover el panel (iOS, 23 Nov 2026).
                SubcomposeAsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop,
                                     onSuccess = { com.buddy.app.core.designsystem.components.PhotoSources.noteAspect(url, it.painter.intrinsicSize) },
                                     modifier = Modifier.size(side))
            } else {
                Icon(Icons.Outlined.ShoppingBag, null, tint = BuddyColor.Brand)
            }
        }
        Text(product.name, style = BuddyType.Caption1, color = BuddyColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val price = pres?.price ?: product.entries().mapNotNull { it?.price }.minOrNull()
            if (price != null) Text(soles(price), style = BuddyType.FootnoteBold, color = BuddyColor.Brand, maxLines = 1)
            val label = pres?.label.orEmpty()
            if (label.isNotEmpty()) Text(label, style = BuddyType.Caption1, color = BuddyColor.InkMuted, maxLines = 1)
        }
    }
}


/**
 * Producto tocado en "Productos locales" de Inicio: el panel del lugar lo
 * muestra primero (Keyla, 23 Nov 2026). Se consume una vez y vale 10 s.
 */
object ProductFocus {
    private var pending: Triple<String, String, Long>? = null
    fun set(spotId: String, productId: String, presentationId: String?) {
        pending = Triple(spotId.lowercase(), "$productId-${presentationId ?: ""}", System.currentTimeMillis())
    }
    fun take(spotId: String): String? {
        // No se borra al leer: el panel puede rearmarse (llega otra lista de
        // lugares) y perdía el producto tocado. Vence a los 10 s.
        val p = pending ?: return null
        return if (p.first == spotId.lowercase() && System.currentTimeMillis() - p.third < 10_000) p.second else null
    }
}
