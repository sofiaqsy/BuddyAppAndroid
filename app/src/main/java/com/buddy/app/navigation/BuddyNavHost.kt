package com.buddy.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.features.conexiones.ConexionesScreen
import com.buddy.app.features.home.InicioScreen
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
    // Mismo ViewModel (scope de Activity) que usa el tab Conexiones —
    // el badge refleja chatStore.totalUnread como en iOS.
    val conexionesVm: com.buddy.app.features.conexiones.ConexionesViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()
    val conexionesState by conexionesVm.state.collectAsState()

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
        when (selectedTab) {
            AppTab.Inicio -> InicioScreen(
                modifier,
                onOpenTrips = { selectedTab = AppTab.Trips },
                onOpenConexiones = { selectedTab = AppTab.Conexiones },
            )
            AppTab.Trips -> TripsScreen(modifier, onOpenConexiones = { selectedTab = AppTab.Conexiones })
            AppTab.Conexiones -> ConexionesScreen(modifier, onOpenTrips = { selectedTab = AppTab.Trips })
            AppTab.Yo -> YoScreen(modifier, onOpenTrips = { selectedTab = AppTab.Trips })
        }
    }
}
