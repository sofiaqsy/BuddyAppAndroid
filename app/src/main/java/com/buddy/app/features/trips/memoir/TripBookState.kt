package com.buddy.app.features.trips.memoir

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Espejo de NotificationCenter .memoirPageSaved (iOS): tick observable que
 * incrementa tras cada guardado — TripFeedCard lo observa para recargar
 * las portadas locales.
 */
object MemoirEvents {
    var saveTick by mutableStateOf(0)
}

/**
 * Estado del Trip Book — port de TripBookViewModel.swift.
 * pages + página actual + modo edición, con caché de canvas por página
 * mientras se edita, flush a disco al salir.
 */
class TripBookState(
    val journeyId: String,
    private val persistence: MemoirPersistence,
    private val scope: CoroutineScope,
) {
    val pages = mutableStateListOf<CollagePage>()
    var currentPageIndex by mutableIntStateOf(0)
    var isEditing by mutableStateOf(false)
    var editingCanvas by mutableStateOf(CanvasState())
    var isLoadingPage by mutableStateOf(false)

    private val vmCache = mutableMapOf<String, CanvasState>()

    init {
        pages.addAll(persistence.load(journeyId))
        if (pages.isEmpty()) pages.add(CollagePage())
        assignBackgroundStrips()
    }

    // ── Enter / Exit edit ────────────────────────────────────────────────────

    fun enterEdit(index: Int) {
        currentPageIndex = index
        isEditing = true
        val id = pages[index].id
        vmCache[id]?.let { editingCanvas = it; return }
        loadPage(index)
    }

    fun exitEdit(canvasW: Float, canvasH: Float) {
        if (canvasW > 0f) {
            editingCanvas.canvasW = canvasW
            editingCanvas.canvasH = canvasH
        }
        vmCache[pages[currentPageIndex].id] = editingCanvas
        flushCacheToDisk()
        vmCache.clear()
        isEditing = false
        saveAsync()
    }

    // ── Navegación entre páginas dentro del editor ───────────────────────────

    fun navigateToNext() {
        if (currentPageIndex < pages.size - 1) switchPage(currentPageIndex + 1)
    }

    fun navigateToPrevious() {
        if (currentPageIndex > 0) switchPage(currentPageIndex - 1)
    }

    private fun switchPage(index: Int) {
        vmCache[pages[currentPageIndex].id] = editingCanvas
        currentPageIndex = index
        val id = pages[index].id
        vmCache[id]?.let { editingCanvas = it; return }
        loadPage(index)
    }

    private fun loadPage(index: Int) {
        isLoadingPage = true
        val page = pages[index]
        scope.launch {
            val (items, bg) = withContext(Dispatchers.IO) {
                persistence.buildItems(page, journeyId) to
                    page.backgroundImageFile?.let { persistence.loadBackground(it, journeyId) }
            }
            val canvas = CanvasState().apply {
                backgroundRGBA = page.backgroundRGBA
                backgroundBitmap = bg
                this.items.addAll(items)
            }
            if (pages.getOrNull(index)?.id == page.id) {
                vmCache[page.id] = canvas
                editingCanvas = canvas
            }
            isLoadingPage = false
        }
    }

    // ── Add / Delete páginas ─────────────────────────────────────────────────

    fun addPage() {
        if (isEditing) vmCache[pages[currentPageIndex].id] = editingCanvas
        var newPage = CollagePage()
        val newCanvas = CanvasState()
        val stripFile = "bg_strip_${pages.size % 3}.jpg"
        if (persistence.backgroundStripExists(stripFile)) {
            newPage = newPage.copy(backgroundImageFile = stripFile)
            newCanvas.backgroundBitmap = persistence.loadBackground(stripFile, journeyId)
        }
        pages.add(newPage)
        currentPageIndex = pages.size - 1
        vmCache[newPage.id] = newCanvas
        editingCanvas = newCanvas
        isEditing = true
        saveAsync()
    }

    fun deletePage(index: Int) {
        if (pages.size <= 1) return
        vmCache.remove(pages[index].id)
        pages.removeAt(index)
        if (currentPageIndex >= pages.size) currentPageIndex = pages.size - 1
        if (isEditing) isEditing = false
        saveAsync()
    }

    // ── Privados ─────────────────────────────────────────────────────────────

    private fun flushCacheToDisk() {
        for ((pageId, canvas) in vmCache) {
            val idx = pages.indexOfFirst { it.id == pageId }
            if (idx < 0) continue
            var snap = persistence.snapshot(canvas.items.toList(), canvas.backgroundRGBA, pages[idx], journeyId)
            val thumb = persistence.generateThumbnail(
                canvas.sortedItems, canvas.backgroundBitmap, canvas.backgroundRGBA,
                canvas.canvasW, canvas.canvasH, pageId, journeyId,
            )
            if (thumb != null) snap = snap.copy(thumbnailFileName = thumb)
            pages[idx] = snap.copy(editVersion = pages[idx].editVersion + 1)
        }
    }

    private fun assignBackgroundStrips() {
        var changed = false
        for (i in pages.indices) {
            if (pages[i].backgroundImageFile != null) continue
            val stripFile = "bg_strip_${i % 3}.jpg"
            if (persistence.backgroundStripExists(stripFile)) {
                pages[i] = pages[i].copy(backgroundImageFile = stripFile)
                changed = true
            }
        }
        if (changed) saveAsync()
    }

    private fun saveAsync() {
        val snapshot = pages.toList()
        scope.launch(Dispatchers.IO) {
            persistence.save(snapshot, journeyId)
            // Notificar DESPUÉS de que el guardado termine (como iOS)
            MemoirEvents.saveTick++
        }
    }
}
