package com.buddy.app.features.trips.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * El panel del mapa que se jala hacia arriba y hacia abajo (iOS
 * PlaceGuideDetailSheet + TripDetailView.panelDrag, 23 Nov 2026):
 * - todo el panel lo mueve (encabezado, contenido y espacio en blanco);
 * - el mismo resorte siempre (iOS response 0.26, damping 0.92);
 * - decide abrir o cerrar con el final previsto y un umbral de 30 dp;
 * - abierto y con la lista arriba del todo, jalar hacia abajo mueve el panel
 *   y no estira la lista (sin rebote);
 * - un arrastre nunca dispara un toque: Compose cancela el clic cuando el
 *   panel se queda con el gesto.
 */
@Stable
class PanelSheet(private val scope: CoroutineScope, private val thresholdPx: Float) {
    var minPx by mutableStateOf(0f); private set
    var maxPx by mutableStateOf(0f); private set
    var expanded by mutableStateOf(false); private set
    /** Tercer alto, abajo del todo: solo el título, para ver el mapa (iOS, 24 Nov 2026). */
    var minimized by mutableStateOf(false); private set
    var peekPx by mutableStateOf(0f); private set
    val height = Animatable(0f)
    private var startPx = 0f
    private var dragging = false

    /** Topes del panel. Cerrado depende de lo que muestra (lista o ficha). */
    fun setBounds(min: Float, max: Float, peek: Float = min) {
        val first = minPx == 0f
        minPx = min; maxPx = max; peekPx = peek
        val target = target()
        scope.launch { if (first) height.snapTo(target) else if (!dragging) height.animateTo(target, Spring) }
    }

    private fun target() = if (expanded) maxPx else if (minimized) peekPx else minPx

    fun expand(value: Boolean) {
        expanded = value
        if (value) minimized = false
        scope.launch { height.animateTo(target(), Spring) }
    }

    fun minimize(value: Boolean) {
        minimized = value
        if (value) expanded = false
        scope.launch { height.animateTo(target(), Spring) }
    }

    /** La barrita: desde el mínimo vuelve al alto normal; si no, alterna. */
    fun toggle() = if (minimized) minimize(false) else expand(!expanded)

    fun start() { if (!dragging) { dragging = true; startPx = height.value } }

    /** delta > 0 es el dedo hacia abajo. */
    fun dragBy(delta: Float) {
        start()
        scope.launch { height.snapTo((height.value - delta).coerceIn(minOf(peekPx, minPx), maxPx)) }
    }

    /** velocity > 0 es hacia abajo, en px/s. */
    fun settle(velocity: Float) {
        if (!dragging) return
        dragging = false
        val predictedUp = (height.value - startPx) - velocity * 0.15f
        when {
            // Arriba: del mínimo al normal, del normal a todo.
            predictedUp > thresholdPx -> if (minimized) minimize(false) else expand(true)
            // Abajo: de todo al normal, del normal al mínimo.
            predictedUp < -thresholdPx -> if (expanded) expand(false) else minimize(true)
            else -> scope.launch { height.animateTo(target(), Spring) }
        }
    }

    /** Para listas que se desplazan dentro del panel abierto. */
    val nestedScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // El panel a medio camino (se estaba jalando hacia abajo): subir el
            // dedo primero lo devuelve arriba y recién después mueve la lista.
            if (source == NestedScrollSource.UserInput && available.y < 0 && height.value < maxPx && dragging) {
                dragBy(available.y); return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // La lista ya está arriba y el dedo sigue bajando: se mueve el panel.
            if (source == NestedScrollSource.UserInput && available.y > 0) {
                dragBy(available.y); return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!dragging) return Velocity.Zero
            settle(available.y); return available
        }
    }

    companion object {
        /** iOS spring(response: 0.26, dampingFraction: 0.92). */
        val Spring = spring<Float>(dampingRatio = 0.92f, stiffness = 584f)
    }
}
