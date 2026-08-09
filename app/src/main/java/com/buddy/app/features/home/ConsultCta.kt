package com.buddy.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius

/**
 * El botón que reemplazó a la grilla de categorías — espejo de consultCTA (iOS).
 *
 * El cambio de verbo es todo el rediseño: el CTA ya no dispara una búsqueda,
 * ABRE UNA CONVERSACIÓN. La ayuda deja de ser una pantalla que se completa y
 * pasa a ser un hilo que se inicia.
 *
 * Un solo control para los tres momentos —nadie todavía, buscando, y buddy
 * asignado—. Comparten forma a propósito: el botón se va llenando a medida que
 * avanza la historia en vez de ser reemplazado por otra cosa.
 */
@Composable
fun ConsultCta(
    /** La CIUDAD del contexto, no la del lugar centrado en el carrusel: la
     *  consulta es sobre el destino completo. Atarlo a la foto del medio haría
     *  que el texto cambiara al deslizar, y eso enseñaría que las fotos SÍ son
     *  un selector — justo lo contrario de lo que el carrusel comunica. */
    destinationName: String?,
    activeBuddyName: String?,
    activeBuddyAvatarUrl: String?,
    activeBuddySubtitle: String?,
    activeBuddyHasUnread: Boolean,
    /** Categoría de una solicitud propia todavía sin atender. */
    searchingCategoryKey: String?,
    onOpenBuddyChat: () -> Unit,
    onStartConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buscando = searchingCategoryKey != null && activeBuddyName == null

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(BuddyColor.Surface)
            .border(1.dp, BuddyColor.Border, RoundedCornerShape(Radius.sm))
            .clickable { if (activeBuddyName != null) onOpenBuddyChat() else onStartConversation() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── Leading ───────────────────────────────────────────────────────
        if (activeBuddyName != null) {
            Box {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(BuddyColor.SurfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    if (activeBuddyAvatarUrl != null) {
                        AsyncImage(
                            model = activeBuddyAvatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(34.dp).clip(CircleShape),
                        )
                    } else {
                        Icon(
                            Icons.Default.Person, contentDescription = null,
                            tint = BuddyColor.InkMuted, modifier = Modifier.size(17.dp),
                        )
                    }
                }
                if (activeBuddyHasUnread) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(BuddyColor.ErrorRed),
                    )
                }
            }
        } else {
            Icon(
                Icons.Default.ChatBubble,
                contentDescription = null,
                // Apagado mientras busca: el protagonista ahí es el spinner de
                // la derecha, no el icono.
                tint = if (buscando) BuddyColor.InkMuted else BuddyColor.Ink,
                modifier = Modifier.size(18.dp),
            )
        }

        // ── Título + subtítulo ────────────────────────────────────────────
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                buildAnnotatedString {
                    if (activeBuddyName != null) {
                        // El nombre solo no decía qué es esa persona ni por qué
                        // está ahí. La etiqueta va en peso normal y el nombre en
                        // bold: lo que se lee primero sigue siendo quién.
                        withStyle(SpanStyle(color = BuddyColor.InkMuted)) {
                            append("Tu buddy asignado: ")
                        }
                        withStyle(SpanStyle(color = BuddyColor.Ink, fontWeight = FontWeight.SemiBold)) {
                            append(activeBuddyName)
                        }
                    } else {
                        withStyle(SpanStyle(color = BuddyColor.Ink, fontWeight = FontWeight.SemiBold)) {
                            append(
                                if (buscando) "Buscando buddy…"
                                else "Consultar en ${destinationName ?: "este lugar"}"
                            )
                        }
                    }
                },
                style = BuddyType.Footnote,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val subtitulo = when {
                activeBuddyName != null ->
                    activeBuddySubtitle ?: "Tu buddy en ${destinationName ?: "este lugar"}"
                searchingCategoryKey != null -> categoriaLabel(searchingCategoryKey)
                else -> null
            }
            if (subtitulo != null) {
                Text(
                    subtitulo,
                    style = BuddyType.Caption1,
                    color = BuddyColor.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // ── Trailing ──────────────────────────────────────────────────────
        if (buscando) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = BuddyColor.InkMuted,
                strokeWidth = 2.dp,
            )
        } else {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(BuddyColor.Brand),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = BuddyColor.InkInverse,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

/** Nombre legible de la categoría de ayuda, para el subtítulo de "Buscando". */
private fun categoriaLabel(key: String): String = when (key) {
    "transport" -> "Transporte"
    "food", "food_recs" -> "Comer"
    "shopping" -> "Compras"
    "activities" -> "Actividades"
    "accommodation" -> "Alojamiento"
    "recommendations" -> "Consejos"
    "translation" -> "Traducción"
    "emergency" -> "Emergencia"
    else -> "Tu consulta"
}
