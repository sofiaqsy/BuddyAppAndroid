package com.buddy.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius
import com.buddy.app.core.designsystem.Spacing

/**
 * La conversación ANTES de que exista un buddy — espejo de
 * PendingConversationView (iOS).
 *
 * "Consultar en Lima" abre esto, no un formulario. El cambio es de fondo, no
 * de estilo: pedir ayuda deja de ser una pantalla que se completa y pasa a ser
 * un hilo que se inicia. Por eso el tema se elige DENTRO de la conversación y
 * lo elegido se queda ahí arriba como algo ya dicho.
 *
 * Las líneas del sistema no llevan burbuja ni avatar: la app guía el proceso,
 * nunca simula ser una persona conversando.
 *
 * Y no hay campo de texto todavía. Sería una promesa falsa: no hay hilo al que
 * mandar nada hasta que alguien acepte. La barra de abajo dice exactamente eso
 * en vez de dejar el espacio vacío.
 */
@Composable
fun ConversacionPendiente(
    destinationName: String?,
    /** null mientras no se eligió tema: entonces se muestran las categorías. */
    categoriaElegida: String?,
    buscando: Boolean,
    onElegirCategoria: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ciudad = destinationName ?: "la zona"

    Column(modifier.fillMaxSize().background(BuddyColor.Canvas)) {
        // ── Header ────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(end = Spacing.edge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver",
                     tint = BuddyColor.Ink, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(if (categoriaElegida == null) "Nueva consulta" else "Buscando buddy",
                     style = BuddyType.Headline, color = BuddyColor.Ink, maxLines = 1)
                Text(ciudad, style = BuddyType.Caption1, color = BuddyColor.InkMuted, maxLines = 1)
            }
        }
        HorizontalDivider(color = BuddyColor.Border)

        // Anticipa lo que va a pasar, que es lo que hace falta saber aquí.
        Text(
            if (categoriaElegida == null)
                "Cuéntanos sobre qué necesitas ayuda y buscamos a alguien que conozca $ciudad."
            else
                "Estamos avisando a buddies de $ciudad. En cuanto alguien acepte, se une a esta conversación.",
            style = BuddyType.Caption1,
            color = BuddyColor.InkMuted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.edge, vertical = 10.dp),
        )
        HorizontalDivider(color = BuddyColor.Border.copy(alpha = 0.5f))

        Box(Modifier.weight(1f)) {
            if (categoriaElegida == null) selector(onElegirCategoria) else hilo(categoriaElegida, ciudad)
        }

        // ── Barra inferior ────────────────────────────────────────────────
        // Ocupa el sitio del campo de texto y dice por qué todavía no se puede
        // escribir. El área de abajo de un chat nunca queda vacía.
        Row(
            Modifier
                .fillMaxWidth()
                .background(BuddyColor.Surface)
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (categoriaElegida == null) {
                Text("Elige un tema para empezar",
                     style = BuddyType.Footnote, color = BuddyColor.InkMuted)
            } else {
                CircularProgressIndicator(Modifier.size(16.dp), color = BuddyColor.InkMuted, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Podrás escribir cuando un buddy se una",
                     style = BuddyType.Footnote, color = BuddyColor.InkMuted)
            }
        }
    }
}

/**
 * El selector va al CENTRO de la conversación, no anclado abajo.
 *
 * Como fila de chips en el composer se leía como una barra de herramientas; en
 * medio del hilo se lee como lo único que hay que hacer ahora.
 */
@Composable
private fun selector(onElegir: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.edge, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        categoriasConsulta.chunked(2).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { cat ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Radius.md))
                            .background(BuddyColor.Surface)
                            .border(1.dp, BuddyColor.Border, RoundedCornerShape(Radius.md))
                            .clickable { onElegir(cat.apiKey) }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BuddyColor.GroupedBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(cat.icono, contentDescription = null,
                                 tint = BuddyColor.Accent, modifier = Modifier.size(16.dp))
                        }
                        Text(cat.titulo, style = BuddyType.FootnoteBold, color = BuddyColor.Ink, maxLines = 1)
                        Text(cat.subtitulo, style = BuddyType.Caption1, color = BuddyColor.InkMuted, maxLines = 2)
                    }
                }
                if (fila.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Lo ya dicho. La categoría elegida se dibuja como un mensaje PROPIO —igual
 * que el mensaje real que lo reemplazará cuando exista el match—, para que la
 * llegada del buddy no cambie nada de lo que ya está en pantalla.
 */
@Composable
private fun hilo(categoria: String, ciudad: String) {
    val cat = categoriasConsulta.firstOrNull { it.apiKey == categoria }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.edge),
            horizontalArrangement = Arrangement.End) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(Radius.md))
                    .background(BuddyColor.Brand)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                cat?.let {
                    Icon(it.icono, contentDescription = null,
                         tint = BuddyColor.InkInverse, modifier = Modifier.size(15.dp))
                }
                Text(cat?.titulo ?: categoria,
                     style = BuddyType.FootnoteBold, color = BuddyColor.InkInverse)
            }
        }

        lineaDelSistema("Estamos contactando buddies en $ciudad…",
                        "Normalmente toma menos de 1 minuto.")
    }
}

/** Sin burbuja, sin avatar, centrada: es la app hablando, no una persona. */
@Composable
private fun lineaDelSistema(texto: String, nota: String? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(texto, style = BuddyType.Caption1, color = BuddyColor.InkMuted,
             textAlign = TextAlign.Center)
        nota?.let {
            Text(it, style = BuddyType.Caption1, color = BuddyColor.InkFaint,
                 textAlign = TextAlign.Center)
        }
    }
}

private data class CategoriaConsulta(
    val apiKey: String,
    val titulo: String,
    val subtitulo: String,
    val icono: ImageVector,
)

/** Las mismas seis de iOS, en el mismo orden. */
private val categoriasConsulta = listOf(
    CategoriaConsulta("transport", "Transporte", "Rutas y movilidad",
        Icons.Filled.DirectionsCar),
    CategoriaConsulta("food", "Comer", "Restaurantes y sabores locales",
        Icons.Filled.Coffee),
    CategoriaConsulta("shopping", "Compras", "Productos locales",
        Icons.Filled.ShoppingBag),
    CategoriaConsulta("activities", "Actividades", "Tours y experiencias",
        Icons.Filled.Hiking),
    CategoriaConsulta("accommodation", "Alojamiento", "Hoteles y hospedajes",
        Icons.Filled.Bed),
    CategoriaConsulta("recommendations", "Consejos", "Recomendaciones",
        Icons.Filled.Lightbulb),
)

/**
 * La conversación a PANTALLA COMPLETA, con su propia lógica de solicitud.
 *
 * Vive aquí y no dentro de InicioScreen porque tiene que renderizarse fuera
 * del Scaffold, como el chat real: una hoja modal deja la barra de tabs
 * asomando abajo y se lee como "algo encima del Home" en vez de como la
 * conversación en la que estás. El chat de Buddy siempre ha ocupado la
 * pantalla entera; esto es el mismo chat en un momento anterior.
 *
 * Y una sola superficie para todo el recorrido: elegir tema, buscar, y el chat
 * real cuando alguien acepta. El contenido se reemplaza; el usuario no navega.
 */
@androidx.compose.runtime.Composable
fun ConversacionPendienteHost(
    homeVm: HomeViewModel,
    matchingVm: com.buddy.app.features.matching.MatchingViewModel,
    onOpenTrips: () -> Unit,
    onOpenConexiones: () -> Unit,
    onClose: () -> Unit,
) {
    val state by homeVm.state.collectAsState()
    val searchState by matchingVm.state.collectAsState()
    var categoriaElegida by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<String?>(null)
    }

    /**
     * Espejo de submitHelpFromHome (iOS), con el contexto explícito del
     * selector en vez de "hay trip → usarlo siempre". Se movió aquí desde
     * InicioScreen sin cambiar la lógica: ahora la dispara el tema elegido
     * dentro de la conversación.
     */
    fun solicitar(category: String) {
        val ctx = state.effectiveHomeContext
        val esElTripConMatch = ctx is HomeContext.Trip && ctx.journeyId == state.activeJourney?.id
        val esPionero = state.communityContext?.totalBuddies == 0
        when {
            esElTripConMatch && state.activeMatchId != null -> Unit  // ya hay chat: el CTA lleva ahí
            esElTripConMatch && state.activeBuddyName != null -> onOpenConexiones()
            // Pioneer: sin buddies no hay nada que buscar — registra trip +
            // solicitud en silencio y navega a "Tu trip" (iOS).
            esPionero && (state.destinationId != null || state.userLat != null) ->
                matchingVm.pioneerRegister(
                    destinationId = state.destinationId,
                    lat = state.userLat, lng = state.userLng,
                    category = category,
                    cityName = state.destinationName,
                    onDone = { onClose(); onOpenTrips() },
                )
            state.destinationId != null ->
                matchingVm.findBuddy(
                    state.destinationId!!, category,
                    ensureJourney = ctx is HomeContext.Trip,
                )
            else -> { onClose(); onOpenTrips() }   // sin ubicación: registrar a mano
        }
    }

    fun cerrar() {
        // Cerrar mientras busca cancela la búsqueda: dejarla viva sin nada en
        // pantalla que lo diga es como se acumulan solicitudes olvidadas.
        if (searchState is com.buddy.app.features.matching.MatchingViewModel.SearchState.Searching) {
            matchingVm.cancelSearch()
        }
        categoriaElegida = null
        onClose()
    }

    val emparejado = searchState as? com.buddy.app.features.matching.MatchingViewModel.SearchState.Matched
    if (emparejado != null) {
        // Un buddy aceptó: la misma pantalla pasa a ser el chat.
        com.buddy.app.features.messages.ChatScreen(
            matchId = emparejado.matchId,
            title = emparejado.buddy?.fullName?.split(" ")?.firstOrNull() ?: "Chat",
            initialCategory = emparejado.category,
            onBack = {
                matchingVm.dismiss()
                categoriaElegida = null
                onClose()
                homeVm.refreshTripState()
            },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        ConversacionPendiente(
            destinationName = state.destinationName,
            // El tema sale del estado de búsqueda cuando existe: así sobrevive
            // a una recomposición y al reintento.
            categoriaElegida = (searchState as? com.buddy.app.features.matching.MatchingViewModel.SearchState.Searching)?.category
                ?: categoriaElegida,
            buscando = searchState is com.buddy.app.features.matching.MatchingViewModel.SearchState.Searching,
            onElegirCategoria = { categoriaElegida = it; solicitar(it) },
            onBack = { cerrar() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
