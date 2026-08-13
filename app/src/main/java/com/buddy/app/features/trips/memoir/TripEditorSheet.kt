package com.buddy.app.features.trips.memoir

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.buddy.app.core.data.model.ApiJourney
import kotlinx.coroutines.launch

/**
 * Espejo de TripEditorSheet (TripsView.swift): editor DIRECTO sin book view.
 *
 * "Tu historia empieza aquí" / "+" → onEdit(-1): si el libro solo tiene la
 * página vacía del init se edita esa, si no se crea una página nueva.
 * Tap en una página existente → onEdit(index): editor en esa página.
 * Al salir del editor (isEditing = false) se cierra la pantalla completa.
 *
 * isStandaloneShare (Fase 2 "Compartir un lugar"): sin tarjeta de trip ni
 * botón "Publicar" visible para este journey (TripsViewModel.visibleTrips lo
 * excluye a propósito — ver ese archivo), así que salir del editor con
 * contenido ES el acto de publicar, sin segundo paso manual.
 */
@Composable
fun TripEditorSheet(
    journey: ApiJourney,
    initialPage: Int,
    isStandaloneShare: Boolean = false,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val persistence = remember { MemoirPersistence(context.applicationContext) }
    val book = remember(journey.id) { TripBookState(journey.id, persistence, scope) }
    val publishVm: MemoirPublishViewModel = hiltViewModel()
    var didStart by remember { mutableStateOf(false) }
    var isPublishingShare by remember { mutableStateOf(false) }
    // Salir del editor de UNA página (el check del canvas) solo guarda esa
    // página — no es "terminé de compartir". Este confirm es el único lugar
    // donde esa decisión se toma explícitamente.
    var showShareConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Guard: igual que iOS — el arranque solo corre una vez
        if (didStart) return@LaunchedEffect
        didStart = true
        if (initialPage == -1) {
            // "Nuevo momento": si solo existe la página vacía del init,
            // editarla en vez de añadir otra (evita la página fantasma).
            val onlyEmptyPage = book.pages.size == 1 &&
                book.pages[0].itemSnapshots.isEmpty() &&
                book.pages[0].backgroundImageFile == null
            if (onlyEmptyPage) book.enterEdit(0) else book.addPage()
        } else {
            book.enterEdit(initialPage.coerceIn(0, book.pages.size - 1))
        }
    }

    // Espejo de onChange(bookVM.isEditing): salir del editor cierra la pantalla
    LaunchedEffect(book.isEditing) {
        if (!didStart || book.isEditing) return@LaunchedEffect
        if (isStandaloneShare) {
            val hasContent = book.pages.any { it.itemSnapshots.isNotEmpty() || it.backgroundImageFile != null }
            if (hasContent) {
                showShareConfirm = true   // guardaste la foto — ¿ya terminaste?
            } else {
                // Sin fotos — nada que compartir. El journey queda sin publicar
                // (is_public sigue false, nunca visible) y se descarta.
                onDismiss()
            }
        } else {
            onDismiss()
        }
    }

    if (book.isEditing) {
        // isStandaloneShare ES el caso de la recomendación: se entró desde
        // "Compartir un lugar" y lo que se guarda tiene que quedar publicado.
        TripCanvasEditor(book, publicaAlGuardar = isStandaloneShare)
    }

    if (showShareConfirm) {
        AlertDialog(
            onDismissRequest = { showShareConfirm = false; onDismiss() },
            title = { Text("¿Listo para compartir?") },
            confirmButton = {
                TextButton(onClick = {
                    showShareConfirm = false
                    if (!isPublishingShare) {
                        isPublishingShare = true
                        scope.launch {
                            publishVm.publish(journey, book.pages.toList(), persistence)
                            onDismiss()
                        }
                    }
                }) { Text("Compartir") }
            },
            dismissButton = {
                TextButton(onClick = { showShareConfirm = false; book.addPage() }) {
                    Text("Agregar otra foto")
                }
            },
        )
    }
}
