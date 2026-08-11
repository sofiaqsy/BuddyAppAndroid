package com.buddy.app.features.trips.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.buddy.app.core.data.model.ApiPlaceBuddy
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Spacing

/**
 * Tarjeta del rail — espejo de PlacePhotoCard (iOS).
 *
 * Con FOTO, y no un pin con el nombre al lado. Un rail de filas idénticas no
 * agrega nada sobre los marcadores que ya están en el mapa; la foto sí: dice
 * cómo se ve el sitio, que es lo que decide si vale la pena ir.
 *
 * Sin foto cae a un degradado por índice en vez de a un gris: cuatro paletas
 * cálidas alternándose mantienen el rail legible como una fila de lugares
 * distintos aunque ninguno tenga imagen todavía.
 */
private val paletas = listOf(
    listOf(Color(0xFF4A2820), Color(0xFF6E3B2D)),
    listOf(Color(0xFF3D2B1A), Color(0xFF6B4226)),
    listOf(Color(0xFF4A3D35), Color(0xFF7A6558)),
    listOf(Color(0xFF5C3E1A), Color(0xFF8B6428)),
)

@Composable
fun PlacePhotoCard(
    spot: ApiGuideSpot,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(145.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, BuddyColor.Brand, RoundedCornerShape(14.dp))
                } else Modifier,
            )
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(90.dp)) {
            if (spot.coverUrl != null) {
                AsyncImage(
                    model = spot.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(paletas[index % paletas.size].map { it.copy(alpha = 0.82f) }),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Photo, contentDescription = null,
                        Modifier.size(26.dp), tint = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
            Text(
                spot.name,
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BuddyColor.Ink,
                maxLines = 1,
            )
            Text(
                spot.categoryName ?: "Lugar",
                fontSize = 10.sp, color = BuddyColor.InkMuted, maxLines = 1,
            )
        }
    }
}

/**
 * Ficha del lugar DENTRO del panel — espejo de PlaceGuideDetailSheet (iOS).
 *
 * No es un diálogo ni una hoja nueva: reemplaza el contenido del mismo panel.
 * Levantar algo encima del mapa se siente como una interrupción; esto se lee
 * como la respuesta a haber tocado el lugar, y el mapa sigue ahí detrás
 * mostrando dónde queda.
 *
 * Sin "añadir foto" todavía: Android no tiene el editor de recomendaciones, y
 * un botón que no lleva a ningún lado es peor que su ausencia. Compartir sí
 * está, por la hoja del sistema.
 */
@Composable
fun PlaceGuideDetail(
    spot: ApiGuideSpot,
    presenceText: String?,
    fotos: List<FotoDeLugar>,
    isLoadingFotos: Boolean,
    buddies: List<ApiPlaceBuddy>,
    isLoadingBuddies: Boolean,
    onOpenBuddy: (ApiPlaceBuddy) -> Unit,
    onNavigate: () -> Unit,
    onShare: () -> Unit,
    onOpenPhoto: (FotoDeLugar) -> Unit,
    onClose: () -> Unit,
) {
    var tab by remember(spot.id) { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().padding(top = 10.dp)) {
        // ── Header ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // El nombre se queda con TODO el espacio sobrante y los botones
            // quedan pegados al margen derecho. Antes el título y un Spacer se
            // repartían ese sobrante a medias, así que los botones flotaban a
            // media fila, sin alinearse ni con el nombre ni con el borde.
            Text(
                spot.name,
                fontSize = 19.sp, fontWeight = FontWeight.Bold, color = BuddyColor.Ink,
                maxLines = 1, modifier = Modifier.weight(1f),
            )
            // Compartir junto a "cómo llegar" porque son las dos cosas que se
            // hacen CON un lugar. En secundario: llegar es la acción de quien ya
            // decidió ir; compartir es para otra persona.
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(BuddyColor.GroupedBg)
                    .clickable(onClick = onShare),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Share, contentDescription = "Compartir ${spot.name}",
                    Modifier.size(14.dp), tint = BuddyColor.Ink,
                )
            }
            // Llegar en el color de marca y cerrar en gris: son la acción y su
            // salida, no dos opciones del mismo peso.
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(BuddyColor.Brand)
                    .clickable(onClick = onNavigate),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.NearMe, contentDescription = "Cómo llegar a ${spot.name}",
                    Modifier.size(14.dp), tint = BuddyColor.InkInverse,
                )
            }
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(BuddyColor.GroupedBg)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close, contentDescription = "Cerrar",
                    Modifier.size(13.dp), tint = BuddyColor.InkMuted,
                )
            }
        }

        if (presenceText != null) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(BuddyColor.Accent))
                Text(
                    presenceText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = BuddyColor.Ink, maxLines = 1,
                )
            }
        }

        // ── Pestañas ──
        // Ligeras a propósito: las fotos son las protagonistas, no la
        // navegación. Sin iconos y sin contador en la etiqueta — el número vive
        // dentro de cada pestaña, no compitiendo aquí arriba.
        val titulos = listOf("Fotos", "Info", "Buddies")
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 8.dp, end = 8.dp)) {
            titulos.forEachIndexed { i, titulo ->
                Column(
                    Modifier.weight(1f).clickable { tab = i },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        titulo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (tab == i) BuddyColor.Brand else BuddyColor.InkMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.5.dp)
                            .background(if (tab == i) BuddyColor.Brand else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(color = BuddyColor.Hairline)

        when (tab) {
            0 -> FotosTab(fotos, isLoadingFotos, onOpenPhoto)
            1 -> InfoTab(spot)
            else -> BuddiesTab(buddies, isLoadingBuddies, onOpenBuddy)
        }
    }
}

@Composable
private fun FotosTab(
    fotos: List<FotoDeLugar>,
    isLoading: Boolean,
    onOpenPhoto: (FotoDeLugar) -> Unit,
) {
    when {
        isLoading -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), Alignment.Center) {
            CircularProgressIndicator(Modifier.size(22.dp), color = BuddyColor.Brand, strokeWidth = 2.dp)
        }

        fotos.isEmpty() -> EstadoVacio("Todavía no hay fotos de este lugar")

        else -> LazyRow(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(fotos, key = { i, f -> "$i-${f.url}" }) { _, foto ->
                AsyncImage(
                    model = foto.url,
                    contentDescription = foto.autor?.let { "Foto de $it" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(115.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BuddyColor.SurfaceRaised)
                        .clickable { onOpenPhoto(foto) },
                )
            }
        }
    }
}

@Composable
private fun InfoTab(spot: ApiGuideSpot) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Solo la categoría curada, y solo si existe: un lugar sin clasificar
        // no se anuncia como nada. Es mejor no decir nada que decir algo falso.
        if (spot.categoryName != null) {
            Text(
                spot.categoryName!!,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BuddyColor.Brand,
            )
        }
        if (spot.estaPendiente) {
            Text(
                "Este lugar está esperando aprobación. Por ahora solo tú lo ves.",
                style = BuddyType.Footnote, color = BuddyColor.InkMuted,
            )
        }
        if (spot.categoryName == null && !spot.estaPendiente) {
            Text(
                "Sin datos todavía. La guía de este lugar la construye su comunidad.",
                style = BuddyType.Footnote, color = BuddyColor.InkMuted,
            )
        }
    }
}

/**
 * Los buddies del destino, en FILA y no en lista vertical.
 *
 * La lista con divisores dice "registro": se lee de arriba abajo, una entrada
 * por renglón. Estos no son registros, son las personas que están ahí, y aquí
 * la pregunta es "¿quién hay?" y no "¿quiénes son, en orden?". Además el panel
 * es bajo: en vertical entraban dos y medio, y el que asoma en el borde
 * derecho de una fila dice, solo con asomarse, que hay más.
 */
@Composable
private fun BuddiesTab(
    buddies: List<ApiPlaceBuddy>,
    isLoading: Boolean,
    onOpenBuddy: (ApiPlaceBuddy) -> Unit,
) {
    when {
        isLoading -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), Alignment.Center) {
            CircularProgressIndicator(Modifier.size(22.dp), color = BuddyColor.Brand, strokeWidth = 2.dp)
        }

        buddies.isEmpty() -> EstadoVacio("Aún no hay buddies en este lugar")

        else -> LazyRow(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(buddies, key = { it.travelerId ?: it.fullName.orEmpty() }) { buddy ->
                // La cara Y el nombre abren el perfil: son la misma persona, y
                // acertar en una cara de 52dp con el dedo no siempre sale a la
                // primera. Sin traveler_id no hay perfil que abrir, y entonces
                // tampoco hay gesto — mejor que uno que no lleva a nada.
                val navegable = buddy.travelerId != null
                Column(
                    Modifier
                        .width(64.dp)
                        .then(
                            if (navegable) Modifier.clickable { onOpenBuddy(buddy) } else Modifier,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(BuddyColor.SurfaceRaised),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (buddy.avatarUrl != null) {
                            AsyncImage(
                                model = buddy.avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        } else {
                            Icon(
                                Icons.Filled.Person, contentDescription = null,
                                Modifier.size(22.dp), tint = BuddyColor.InkMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        buddy.fullName?.trim()?.split(" ")?.firstOrNull() ?: "Buddy",
                        style = BuddyType.Caption2, color = BuddyColor.Ink,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun EstadoVacio(texto: String) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, style = BuddyType.Footnote, color = BuddyColor.InkMuted)
    }
}
