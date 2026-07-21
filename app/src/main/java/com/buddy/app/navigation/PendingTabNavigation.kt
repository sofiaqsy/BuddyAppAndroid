package com.buddy.app.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Puente entre el tap en una notificación push (Activity/Intent) y el estado
 * de tab de BuddyRoot (Compose) — no hay NavHost a nivel de tab, así que
 * MainActivity deja aquí el tab destino y BuddyRoot lo consume una vez.
 */
object PendingTabNavigation {
    private val _target = MutableStateFlow<AppTab?>(null)
    val target = _target.asStateFlow()

    fun request(tab: AppTab) {
        _target.value = tab
    }

    fun consume() {
        _target.value = null
    }
}
