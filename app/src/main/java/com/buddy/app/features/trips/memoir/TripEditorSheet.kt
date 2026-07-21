package com.buddy.app.features.trips.memoir

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.buddy.app.core.data.model.ApiJourney

/**
 * Espejo de TripEditorSheet (TripsView.swift): editor DIRECTO sin book view.
 *
 * "Tu historia empieza aquí" / "+" → onEdit(-1): si el libro solo tiene la
 * página vacía del init se edita esa, si no se crea una página nueva.
 * Tap en una página existente → onEdit(index): editor en esa página.
 * Al salir del editor (isEditing = false) se cierra la pantalla completa.
 */
@Composable
fun TripEditorSheet(
    journey: ApiJourney,
    initialPage: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val persistence = remember { MemoirPersistence(context.applicationContext) }
    val book = remember(journey.id) { TripBookState(journey.id, persistence, scope) }
    var didStart by remember { mutableStateOf(false) }

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
        if (didStart && !book.isEditing) onDismiss()
    }

    if (book.isEditing) {
        TripCanvasEditor(book)
    }
}
