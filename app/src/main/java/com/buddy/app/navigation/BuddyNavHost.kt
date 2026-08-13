package com.buddy.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.features.conexiones.ConexionesScreen
import com.buddy.app.features.home.InicioScreen
import com.buddy.app.features.messages.ChatScreen
import com.buddy.app.features.profile.YoScreen
import com.buddy.app.features.trips.TripsScreen

/**
 * Contenedor raíz — espejo de ContentView (iOS).
 * Los 4 tabs se mantienen vivos (igual que el TabView de iOS preserva
 * scroll/nav state); aquí usamos selección por estado en vez de NavHost
 * para el nivel tab, y cada tab tendrá su propio back stack interno.
 */
@Composable
fun BuddyRoot() {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Inicio) }
    // Conserva el estado de cada tab mientras no está en pantalla.
    val tabStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    // Tap en notificación push (ej. "buddy_approved") — ver PendingTabNavigation.
    val pendingTab by PendingTabNavigation.target.collectAsState()
    LaunchedEffect(pendingTab) {
        pendingTab?.let {
            android.util.Log.d("[BuddyRoot]", "consuming pendingTab=$it")
            selectedTab = it
            PendingTabNavigation.consume()
        }
    }
    var openChatId by rememberSaveable { mutableStateOf<String?>(null) }
    /** Conversación pendiente abierta desde "Consultar en X". */
    var conversacionAbierta by rememberSaveable { mutableStateOf(false) }
    var openChatCategory by rememberSaveable { mutableStateOf<String?>(null) }
    // Editor Memoir a pantalla completa — como iOS oculta tab bar y nav bar.
    // Triple (journey, initialPage, isStandaloneShare): -1 = nuevo momento,
    // índice = editar página. isStandaloneShare=true → "Compartir un lugar"
    // (Fase 2): publica solo al salir, sin botón "Publicar" aparte.
    var bookJourney by androidx.compose.runtime.remember {
        mutableStateOf<Triple<com.buddy.app.core.data.model.ApiJourney, Int, Boolean>?>(null)
    }
    // Mapa del trip a pantalla completa (espejo de TripDetailView en iOS)
    var mapJourney by androidx.compose.runtime.remember {
        mutableStateOf<com.buddy.app.core.data.model.ApiJourney?>(null)
    }
    /** El lugar que venía elegido al abrir el mapa, si se llegó tocando UNO
     *  concreto. Nulo cuando lo que se abrió es el destino entero. */
    var mapSpotId by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    /** Perfil de otra persona, como capa encima de la app.
     *
     *  Encima y no en lugar de: montarlo con un `return` desmontaría el tab de
     *  debajo y volver reiniciaría su scroll y su estado. */
    var perfilAbierto by androidx.compose.runtime.remember {
        mutableStateOf<Triple<String, String?, String?>?>(null)
    }

    // Mismo ViewModel (scope de Activity) que usa el tab Conexiones —
    // el badge refleja chatStore.totalUnread como en iOS.
    val conexionesVm: com.buddy.app.features.conexiones.ConexionesViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()
    val conexionesState by conexionesVm.state.collectAsState()
    // Misma instancia (scope de Activity) que usa InicioScreen — al cerrar el
    // chat hay que refrescar trip+match o la card del buddy queda huérfana.
    val homeVm: com.buddy.app.features.home.HomeViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()

    // Mismo TripsViewModel (scope de Activity) que usa el tab — para refrescar
    // la bitácora al volver del book.
    val tripsVm: com.buddy.app.features.trips.TripsViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()

    // "Consultar en X" → la conversación a pantalla completa, ANTES del
    // Scaffold igual que el chat: una hoja modal dejaba la barra de tabs
    // asomando y se leía como algo encima del Home, no como la conversación en
    // la que estás. Es el mismo chat en un momento anterior, así que ocupa la
    // pantalla entera como el chat.
    if (conversacionAbierta) {
        com.buddy.app.features.home.ConversacionPendienteHost(
            homeVm = homeVm,
            matchingVm = androidx.hilt.navigation.compose.hiltViewModel(),
            onOpenTrips = { conversacionAbierta = false; selectedTab = AppTab.Trips },
            onOpenConexiones = { conversacionAbierta = false; selectedTab = AppTab.Conexiones },
            onClose = { conversacionAbierta = false },
        )
        return
    }

    // Mapa del trip → pantalla completa sin tab bar (como TripDetailView, iOS)
    mapJourney?.let { journey ->
        com.buddy.app.features.trips.map.TripMapScreen(
            journey = journey,
            initialSpotId = mapSpotId,
            onBack = { mapJourney = null; mapSpotId = null },
        )
        return
    }

    // Editor abierto → pantalla completa SIN tab bar ni chrome (como iOS:
    // .toolbar(.hidden) + ignoresSafeArea). Flujo idéntico a TripEditorSheet:
    // del tap en "Tu historia empieza aquí" se entra DIRECTO al editor.
    bookJourney?.let { (journey, initialPage, isStandaloneShare) ->
        com.buddy.app.features.trips.memoir.TripEditorSheet(
            journey = journey,
            initialPage = initialPage,
            isStandaloneShare = isStandaloneShare,
            onDismiss = { bookJourney = null; tripsVm.load() },
        )
        return
    }

    // Si hay chat abierto, mostrar ChatScreen en overlay.
    // systemBarsPadding: el overlay vive fuera del Scaffold, sin él el header
    // queda bajo la barra de estado y el input bajo la barra de gestos.
    if (openChatId != null) {
        ChatScreen(
            matchId = openChatId!!,
            title = "Chat",
            initialCategory = openChatCategory,
            onBack = {
                openChatId = null; openChatCategory = null
                conexionesVm.load()
                homeVm.refreshTripState()
                // La fila "¿Una duda en X?" de Tu trip depende del match — si el
                // usuario cerró la ayuda dentro del chat, hay que recargarla.
                tripsVm.load()
            },
            modifier = Modifier.systemBarsPadding(),
        )
        return
    }

    // Box y no dos hermanos sueltos: el perfil se dibuja ENCIMA del Scaffold,
    // tapando también la barra de tabs — es una pantalla, no un tab más.
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = BuddyColor.Canvas,
        bottomBar = {
            BuddyTabBar(
                selected = selectedTab,
                unreadChats = conexionesState.totalUnread,
                onSelect = { selectedTab = it },
                onReselect = { /* scroll-to-top / reload — se conecta en Fase 4 */ },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        // `when` descompone el tab que dejas, y con él se pierde todo el estado
        // local: posición de scroll, sheets, campos a medio llenar. Los datos
        // seguían en el ViewModel (scope de Activity), pero la lista volvía
        // arriba y se sentía como si recargara. SaveableStateProvider guarda y
        // restaura ese estado por tab, que es lo que hace el TabView de iOS.
        tabStateHolder.SaveableStateProvider(selectedTab) {
        when (selectedTab) {
            AppTab.Inicio -> InicioScreen(
                modifier,
                onOpenTrips = { selectedTab = AppTab.Trips },
                onOpenConexiones = { selectedTab = AppTab.Conexiones },
                onOpenChat = { matchId, category ->
                    openChatCategory = category
                    openChatId = matchId
                },
                onStartConversation = { conversacionAbierta = true },
                // El mapa del destino al que pertenece el lugar — el mismo que
                // abre un trip. Sin destination_id no hay guía que abrir y el
                // tap no hace nada, igual que en iOS.
                onOpenProfile = { id, nombre, avatar -> perfilAbierto = Triple(id, nombre, avatar) },
                onOpenPlace = { card ->
                    val destId = card.destinationId
                    if (destId != null) {
                        // Tocar un LUGAR abre el mapa con ese lugar ya elegido;
                        // tocar una ciudad (comunidad viva) abre el destino
                        // entero. Se distinguen por el id: la ciudad viaja como
                        // tarjeta sintética cuyo id ES el del destino.
                        mapSpotId = card.id.takeIf { it != destId }
                        mapJourney = com.buddy.app.core.data.model.ApiJourney(
                            id = card.id,
                            title = card.destinationName ?: card.name,
                            status = "active",
                            destination = com.buddy.app.core.data.model.ApiDestinationRef(
                                id = destId,
                                name = card.destinationName ?: card.name,
                                city = card.destinationName ?: card.name,
                                lat = card.lat,
                                lng = card.lng,
                            ),
                            destinationId = destId,
                        )
                    }
                },
            )
            AppTab.Trips -> TripsScreen(
                modifier,
                onOpenConexiones = { selectedTab = AppTab.Conexiones },
                onOpenBook = { journey, page, isStandaloneShare -> bookJourney = Triple(journey, page, isStandaloneShare) },
                onOpenMap = { mapJourney = it; mapSpotId = null },
                onPublished = { selectedTab = AppTab.Inicio },
                isApprovedBuddy = conexionesState.isApprovedBuddy,
            )
            AppTab.Conexiones -> ConexionesScreen(modifier, onOpenTrips = { selectedTab = AppTab.Trips })
            AppTab.Yo -> YoScreen(modifier, onOpenTrips = { selectedTab = AppTab.Trips })
        }
        }
    }

    // El de debajo sigue compuesto, así que volver devuelve el Home tal como
    // estaba: mismo scroll, mismo carrusel. Con un `return` —el patrón de las
    // otras pantallas completas de aquí— se desmontaba y se reiniciaba.
    //
    // Se come los toques: si no, tocar una zona vacía del perfil llegaría a lo
    // que hay detrás.
    perfilAbierto?.let { (id, nombre, avatar) ->
        Box(
            Modifier
                .fillMaxSize()
                .background(BuddyColor.Canvas)
                .pointerInput(Unit) {},
        ) {
            com.buddy.app.features.profile.UserProfileScreen(
                travelerId = id,
                previewName = nombre,
                previewAvatarUrl = avatar,
                onBack = { perfilAbierto = null },
                vm = androidx.hilt.navigation.compose.hiltViewModel(key = id),
            )
        }
    }
    }
}
