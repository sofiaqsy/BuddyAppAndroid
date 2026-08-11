package com.buddy.app.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.core.data.model.ApiPlaceCard
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.features.profile.data.ApiBuddyProfile
import com.buddy.app.features.profile.data.ApiUser
import com.buddy.app.features.profile.data.ApiUserSticker
import com.buddy.app.features.profile.data.ProfileApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val api: ProfileApi,
) : ViewModel() {

    data class State(
        val user: ApiUser? = null,
        val isLoadingUser: Boolean = true,
        val stickers: List<ApiUserSticker> = emptyList(),
        val journeys: List<ApiJourney> = emptyList(),
        val shares: List<ApiPlaceCard> = emptyList(),
        /**
         * Si los trips llegaron DE VERDAD.
         *
         * Sin esto, `journeys.isEmpty()` es ambiguo: puede ser "no tiene
         * viajes" o "todavía no sé", y la cabecera pintaba las dos igual,
         * afirmando "0 trips" sobre alguien con siete. Es información falsa
         * sobre una persona real, y quien abra y cierre rápido se queda con
         * ella.
         */
        val tripsConocidos: Boolean = false,
        val loadFailed: Boolean = false,
    )

    var state by mutableStateOf(State())
        private set

    suspend fun cargar(travelerId: String) {
        if (state.user != null) return
        withContext(Dispatchers.IO) {
            val user = async { runCatching { api.user(travelerId) }.getOrNull() }
            val stickers = async { runCatching { api.stickers(travelerId) }.getOrDefault(emptyList()) }
            val trips = async { runCatching { api.trips(travelerId) }.getOrNull() }
            val shares = async { runCatching { api.shares(travelerId).items }.getOrDefault(emptyList()) }

            val cargado = user.await()
            state = state.copy(
                user = cargado,
                isLoadingUser = false,
                loadFailed = cargado == null,
                stickers = stickers.await(),
                journeys = trips.await()?.items ?: emptyList(),
                tripsConocidos = trips.await() != null,
                shares = shares.await(),
            )
        }
    }
}

/**
 * Perfil público de otra persona — espejo de UserProfileView (iOS).
 *
 * El mismo perfil del tab Yo, en modo lectura. Deliberadamente la misma
 * disposición y los mismos componentes: quien ve el perfil de un buddy tiene
 * que reconocer de inmediato que está viendo "lo mismo que tengo yo", porque
 * eso es lo que le dice qué puede llegar a construir. Una pantalla distinta
 * rompería esa lectura.
 *
 * Lo que NO aparece es todo lo que es una acción sobre uno mismo: el menú de
 * cuenta, editar la bio, cambiar el avatar, "Añadir lugar" y el CTA de hacerse
 * buddy. Ver el perfil de alguien no ofrece nada que hacer sobre él salvo mirar
 * lo que aportó.
 */
@Composable
fun UserProfileScreen(
    travelerId: String,
    /** Nombre y avatar de donde se venía tocando. La cabecera los pinta
     *  mientras llega /users/:id, así que nunca sale gris — es la misma imagen
     *  y ya está en caché. */
    previewName: String? = null,
    previewAvatarUrl: String? = null,
    onBack: () -> Unit,
    vm: UserProfileViewModel = hiltViewModel(),
) {
    LaunchedEffect(travelerId) { vm.cargar(travelerId) }
    val state = vm.state
    val nombre = state.user?.fullName ?: previewName ?: "Buddy"

    Column(
        Modifier.fillMaxSize().background(BuddyColor.Canvas).verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Volver", tint = BuddyColor.Ink)
            }
        }

        // Sin antetítulo "PERFIL": la pantalla ya se abrió tocando una cara, así
        // que nadie duda de qué está viendo. Etiquetarlo solo empujaba el nombre
        // hacia abajo.
        Text(
            nombre,
            style = BuddyType.Title1, color = BuddyColor.Ink, maxLines = 2,
            modifier = Modifier.padding(horizontal = Spacing.edge, vertical = Spacing.md),
        )

        // ── Cabecera: avatar + una línea de contexto ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.edge, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(BuddyColor.SurfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                val avatar = state.user?.avatarUrl ?: previewAvatarUrl
                if (avatar != null) {
                    AsyncImage(
                        model = avatar, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(88.dp).clip(CircleShape),
                    )
                } else {
                    Icon(Icons.Filled.Person, null, Modifier.size(36.dp), tint = BuddyColor.InkMuted)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Nunca afirma un número que todavía no se conoce: mientras no
                // se sabe, la línea se pinta atenuada con un relleno que no se
                // lee, reservando su sitio sin decir nada.
                val meta = metaLinea(state)
                Text(
                    meta ?: "3 trips · 2 stickers",
                    style = BuddyType.Footnote, color = BuddyColor.InkMuted,
                    modifier = if (meta == null) Modifier.alpha(0.25f) else Modifier,
                )
                state.user?.memberSince?.let { desde ->
                    mesDeAlta(desde)?.let {
                        Text("Viajando desde $it", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
                    }
                }
            }
        }

        if (!state.user?.bio.isNullOrBlank()) {
            Text(
                state.user!!.bio!!,
                style = BuddyType.Callout, color = BuddyColor.Ink,
                modifier = Modifier.padding(horizontal = Spacing.edge, vertical = Spacing.sm),
            )
        }

        state.user?.buddyProfile?.let { bp ->
            TarjetaDeBuddy(bp, Modifier.padding(horizontal = Spacing.edge, vertical = Spacing.md))
        }

        if (state.stickers.isNotEmpty()) {
            Seccion("STICKERS", state.stickers.size) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.edge),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    items(state.stickers, key = { it.id }) { s ->
                        Column(
                            Modifier.width(72.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                Modifier.size(64.dp).clip(CircleShape).background(BuddyColor.SurfaceRaised),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (s.stickerCatalog?.imageUrl != null) {
                                    AsyncImage(
                                        model = s.stickerCatalog.imageUrl, contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(64.dp).clip(CircleShape),
                                    )
                                } else {
                                    Icon(Icons.Filled.Star, null, Modifier.size(24.dp), tint = BuddyColor.InkMuted)
                                }
                            }
                            Text(
                                s.stickerCatalog?.name ?: "Sticker",
                                style = BuddyType.Caption1, color = BuddyColor.Ink, maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        if (state.shares.isNotEmpty()) {
            Seccion("LUGARES QUE RECOMIENDA", state.shares.size) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.edge),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.shares, key = { it.id }) { lugar ->
                        Column(Modifier.width(132.dp)) {
                            Box(
                                Modifier.fillMaxWidth().height(96.dp)
                                    .clip(RoundedCornerShape(Radius.sm))
                                    .background(BuddyColor.SurfaceRaised),
                            ) {
                                lugar.coverUrl?.let {
                                    AsyncImage(
                                        model = it, contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(lugar.name, style = BuddyType.Caption1, color = BuddyColor.Ink, maxLines = 1)
                        }
                    }
                }
            }
        }

        if (state.journeys.isNotEmpty()) {
            Seccion("TRIPS", state.journeys.size) {
                // Rejilla de 3 en columnas, sin LazyVerticalGrid: esta pantalla
                // ya scrollea entera y anidar dos scrolls verticales no compila
                // ni se lee bien.
                Column(
                    Modifier.padding(horizontal = Spacing.edge),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    state.journeys.chunked(3).forEach { fila ->
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            fila.forEach { journey ->
                                CeldaDeTrip(journey, Modifier.weight(1f))
                            }
                            // Rellena la última fila incompleta para que las
                            // celdas conserven su ancho en vez de estirarse.
                            repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        val vacio = !state.isLoadingUser && state.stickers.isEmpty() &&
            state.shares.isEmpty() && state.journeys.isEmpty()
        if (state.loadFailed) {
            Estado("No pudimos cargar este perfil")
        } else if (vacio) {
            // Un buddy recién aprobado no ha aportado nada todavía. No es un
            // error ni un vacío que haya que disimular.
            val pila = nombre.trim().split(" ").firstOrNull() ?: nombre
            Estado("$pila todavía no ha compartido nada")
        }

        Spacer(Modifier.height(Spacing.xl))
    }
}

/** Quién es como buddy: si está disponible, dónde y a cuánta gente ha ayudado. */
@Composable
private fun TarjetaDeBuddy(bp: ApiBuddyProfile, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(BuddyColor.Surface)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.size(7.dp).clip(CircleShape)
                    .background(if (bp.isAvailable) BuddyColor.Accent else BuddyColor.InkFaint),
            )
            Text(
                if (bp.isAvailable) "Buddy disponible" else "Buddy",
                style = BuddyType.FootnoteBold, color = BuddyColor.Ink,
            )
            Spacer(Modifier.weight(1f))
            bp.totalHelps?.takeIf { it > 0 }?.let {
                Text(
                    if (it == 1) "1 apoyo" else "$it apoyos",
                    style = BuddyType.Caption1, color = BuddyColor.InkMuted,
                )
            }
        }
        // Sin las especialidades: este perfil responde "quién es y dónde está",
        // no "qué me puede resolver" — lo segundo es del flujo de pedir ayuda,
        // que ya pregunta la intención antes de buscar buddy. Listarlas aquí
        // invitaba a elegir persona por su etiqueta, y el emparejamiento no
        // funciona así.
        val cobertura = bp.coverageNames
        if (cobertura.isNotEmpty()) {
            Text(cobertura.joinToString(" · "), style = BuddyType.Caption1, color = BuddyColor.InkMuted)
        }
    }
}

@Composable
private fun CeldaDeTrip(journey: ApiJourney, modifier: Modifier = Modifier) {
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(Radius.sm))
            .background(BuddyColor.SurfaceRaised),
    ) {
        journey.coverUrl?.let {
            AsyncImage(
                model = it, contentDescription = journey.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            journey.destination?.name ?: journey.title.orEmpty(),
            style = BuddyType.Caption2, color = Color.White, maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
        )
    }
}

@Composable
private fun Seccion(titulo: String, cuenta: Int, contenido: @Composable () -> Unit) {
    Spacer(Modifier.height(Spacing.lg))
    Row(
        Modifier.padding(horizontal = Spacing.edge, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titulo, style = BuddyType.Eyebrow, letterSpacing = 2.sp, color = BuddyColor.InkMuted)
        Text("$cuenta", style = BuddyType.Caption1, color = BuddyColor.InkFaint)
    }
    contenido()
}

@Composable
private fun Estado(texto: String) {
    Text(
        texto,
        style = BuddyType.Callout, color = BuddyColor.InkMuted, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
    )
}

private fun metaLinea(state: UserProfileViewModel.State): String? {
    if (!state.tripsConocidos) return null
    val trips = state.journeys.size
    val tripsLabel = if (trips == 1) "1 trip" else "$trips trips"
    val stickers = state.stickers.size
    val stickersLabel = when {
        stickers == 0 -> null
        stickers == 1 -> "1 sticker"
        else -> "$stickers stickers"
    }
    return listOfNotNull(tripsLabel, stickersLabel).joinToString(" · ")
}

private val formatoMes = DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "PE"))

private fun mesDeAlta(iso: String): String? = runCatching {
    OffsetDateTime.parse(iso).format(formatoMes).replaceFirstChar { it.uppercaseChar() }
}.getOrNull()
