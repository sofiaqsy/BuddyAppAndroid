package com.buddy.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.ui.graphics.vector.ImageVector
import com.buddy.app.R

/**
 * Espejo de `enum AppTab` (iOS): inicio, trips, conexiones, yo.
 * Misma terminología y orden; iconos outline/filled equivalen a
 * los pares SF Symbols (house / house.fill, etc.).
 */
enum class AppTab(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
    val activeIcon: ImageVector,
) {
    Inicio("inicio", R.string.tab_inicio, Icons.Outlined.Home, Icons.Filled.Home),
    Trips("trips", R.string.tab_trips, Icons.Outlined.Map, Icons.Filled.Map),
    Conexiones("conexiones", R.string.tab_conexiones, Icons.Outlined.People, Icons.Filled.People),
    Yo("yo", R.string.tab_yo, Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle),
}
