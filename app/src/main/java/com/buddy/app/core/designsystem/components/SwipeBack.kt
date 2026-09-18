package com.buddy.app.core.designsystem.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Pantalla completa que se cierra "jalando a la derecha", como el volver de iOS.
 *
 * Dos caminos al mismo onBack:
 * - El gesto/botón de volver del sistema (BackHandler). Varias pantallas
 *   completas no lo tenían: en el chat abierto desde Inicio, el gesto de volver
 *   se saltaba el chat en vez de cerrarlo.
 * - Arrastrar desde el borde izquierdo: la pantalla sigue al dedo y, al soltar
 *   pasado un tercio del ancho (o con un gesto rápido), se cierra; si no, vuelve
 *   a su sitio. Solo desde el borde para no pelear con los scrolls horizontales
 *   del contenido.
 *
 * Con la navegación por gestos de Android el sistema puede quedarse el borde;
 * entonces entra por BackHandler, que llega al mismo onBack.
 */
@Composable
fun SwipeBackScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onBack)

    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val edgePx = with(LocalDensity.current) { 40.dp.toPx() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = offset.value }
                .pointerInput(widthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.position.x > edgePx) return@awaitEachGesture
                        val tracker = VelocityTracker()
                        var dragging = false
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            pressed = change.pressed
                            val dx = change.position.x - down.position.x
                            if (!dragging && dx > viewConfiguration.touchSlop) dragging = true
                            if (dragging) {
                                tracker.addPosition(change.uptimeMillis, change.position)
                                scope.launch { offset.snapTo(dx.coerceAtLeast(0f)) }
                                change.consume()
                            }
                        }
                        if (dragging) {
                            val velocity = tracker.calculateVelocity().x
                            scope.launch {
                                if (offset.value > widthPx / 3f || velocity > 1200f) {
                                    offset.animateTo(widthPx, tween(180))
                                    onBack()
                                    offset.snapTo(0f)
                                } else {
                                    offset.animateTo(0f, spring())
                                }
                            }
                        }
                    }
                },
        ) {
            content()
        }
    }
}
