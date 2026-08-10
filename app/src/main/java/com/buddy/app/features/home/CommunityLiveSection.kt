package com.buddy.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.buddy.app.core.data.model.ApiPulseItem
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Spacing

/**
 * Comunidad viva — espejo de communityLiveSection (iOS).
 *
 * SIEMPRE EL PULSO GLOBAL
 *
 * Antes mostraba la actividad local (recentHelp) y solo caía al pulso si no
 * había ninguna. iOS abandonó esa regla: la sección cuenta que la red está
 * viva, y para eso da igual dónde ocurrió la ayuda. Filtrando a `helped`,
 * porque los "traveling" cuentan gente sin nombrarla y aquí lo que importa es
 * que alguien concreto ayudó a otro alguien.
 *
 * TRES Y NO DIEZ
 *
 * Esto es una SEÑAL, y las señales saturan: al tercer evento el lector ya
 * concluyó "hay gente ayudando". Las demás solo convierten la sección en un
 * feed.
 *
 * Sin tarjeta ni divisores: son filas sueltas, como en Mail o Mensajes. El
 * recuadro que tenía las agrupaba en un bloque y competía con las tarjetas de
 * verdad que hay arriba y abajo.
 */
@Composable
fun CommunityLiveSection(
    communityPulse: List<ApiPulseItem>,
    isLoading: Boolean,
    formatTimeAgo: (String?) -> String,
    /** Tocar el lugar abre su mapa. Nil-safe: sin destino no navega. */
    onOpenDestination: (destinationId: String, name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ayudas = communityPulse.filter { it.type == "helped" }.take(3)

    Column(modifier.fillMaxWidth()) {
        Text(
            "COMUNIDAD VIVA",
            style = BuddyType.Eyebrow,
            letterSpacing = 1.5.sp,
            color = BuddyColor.Ink,
            modifier = Modifier.padding(horizontal = Spacing.edge),
        )
        Spacer(Modifier.height(Spacing.md))

        when {
            // Mientras carga se dibujan filas de relleno en vez de omitir la
            // sección: apareciendo después empujaba hacia abajo todo lo que
            // viene detrás, que es el salto que se siente como "la pantalla se
            // reacomoda".
            isLoading && ayudas.isEmpty() ->
                ApiPulseItem.placeholders().forEach { fila(it, formatTimeAgo, esEsqueleto = true) {} }

            ayudas.isNotEmpty() ->
                ayudas.forEach { item ->
                    fila(item, formatTimeAgo, esEsqueleto = false) {
                        item.destinationId?.let { onOpenDestination(it, item.city) }
                    }
                }

            // El tercer eslabón, que faltaba. Sin esta rama la sección se
            // DESVANECÍA tras haber dibujado su esqueleto y todo saltaba hacia
            // arriba. El problema nunca fue que estuviera vacía: fue que el
            // esqueleto prometió algo que después no llegaba.
            //
            // Texto neutro a propósito: "sé el primero en ayudar" convertiría
            // una sección informativa en un anuncio, y esto se lee en el
            // arranque, antes de que nadie haya pedido nada.
            else ->
                Text(
                    "Todavía no hay actividad reciente en esta zona. Las próximas ayudas aparecerán aquí.",
                    style = BuddyType.Footnote,
                    color = BuddyColor.InkMuted,
                    modifier = Modifier.padding(horizontal = Spacing.edge),
                )
        }
    }
}

/**
 * Dos líneas: la acción arriba, el contexto abajo. Quién (nombre), en qué
 * (categoría) y dónde/cuándo (línea 2), sin una palabra de más.
 */
@Composable
private fun fila(
    item: ApiPulseItem,
    formatTimeAgo: (String?) -> String,
    esEsqueleto: Boolean,
    onTocarLugar: () -> Unit,
) {
    val nombre = item.buddyName?.trim()?.split(" ")?.firstOrNull()
        ?.replaceFirstChar { it.uppercaseChar() } ?: "Un buddy"

    // .Top y no centrado: el avatar se alinea con la línea 1, que es la que
    // ancla la fila — igual que en Mail y Mensajes.
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.edge)
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 24dp. Se queda pequeño porque Buddy vende personas reales y una cara
        // comunica eso en 100 ms —ningún texto lo hace igual de rápido— aunque
        // a este tamaño no se distingan los rasgos. Solo baja lo suficiente
        // para no encabezar la fila.
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(BuddyColor.SurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (item.buddyAvatarUrl != null && !esEsqueleto) {
                AsyncImage(
                    model = item.buddyAvatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(24.dp).clip(CircleShape),
                )
            } else {
                Icon(Icons.Default.Person, contentDescription = null,
                     tint = BuddyColor.InkMuted, modifier = Modifier.size(11.dp))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // El nombre en medium y la acción en peso normal: el ojo tiene que
            // encontrar al sujeto rápido, pero la negrita plena es la firma
            // visual de una red social y convertiría el hecho en un post.
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = BuddyColor.Ink)) {
                        append(nombre)
                    }
                    withStyle(SpanStyle(color = BuddyColor.Ink)) {
                        append(" ${accionDe(item.category)}")
                    }
                },
                style = BuddyType.Footnote,
                maxLines = 2,
            )

            // Hora y lugar en extremos opuestos: la fila cierra tocando ambos
            // bordes sin robarle ancho a la frase, y la ciudad deja de
            // encabezar su línea — repetida en las tres, las hacía ver
            // plantilla.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatTimeAgo(item.at), style = BuddyType.Caption2,
                     color = BuddyColor.InkMuted, maxLines = 1)
                Spacer(Modifier.weight(1f))
                // El lugar lleva al mapa, igual que el nombre del destino en
                // las historias: el gesto ya está aprendido ahí y responde la
                // misma pregunta, "¿dónde es eso?".
                //
                // Sin destino navegable se queda idéntico y sin gesto: un
                // nombre que a veces navega y a veces no es aceptable; uno que
                // parece navegable y no lo hace, no.
                val navega = item.destinationId != null && !esEsqueleto
                Text(
                    item.city,
                    style = BuddyType.Caption2,
                    color = if (navega) BuddyColor.Ink else BuddyColor.InkMuted,
                    maxLines = 1,
                    modifier = if (navega) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onTocarLugar,
                        )
                    } else Modifier,
                )
            }
        }
    }
}

/**
 * Verbo + complemento corto, SIEMPRE la misma forma.
 *
 * El paralelismo importa más que la precisión: tres filas con la misma
 * estructura gramatical se procesan como un conjunto de un vistazo, mientras
 * que tres formas distintas obligan a re-parsear cada línea.
 *
 * Cortas, además: "resolvió una consulta sobre transporte" sonaba a ticket
 * cerrado, no a alguien ayudando.
 *
 * `general` y lo desconocido caen en la forma genérica: nunca se inventa un
 * detalle que el dato no tiene.
 */
private fun accionDe(category: String?): String = when (category) {
    "transport" -> "ayudó con transporte"
    "food", "food_recs" -> "recomendó dónde comer"
    "accommodation" -> "ayudó con alojamiento"
    "activities" -> "recomendó qué hacer"
    "shopping" -> "ayudó con compras"
    "translation" -> "tradujo para un viajero"
    "emergency" -> "asistió una urgencia"
    "recommendations" -> "dio recomendaciones"
    "airport_pickup" -> "recibió en el aeropuerto"
    "city_tour" -> "acompañó por la ciudad"
    else -> "ayudó a un viajero"
}
