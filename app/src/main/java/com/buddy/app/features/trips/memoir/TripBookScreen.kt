package com.buddy.app.features.trips.memoir

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.core.designsystem.BuddyColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Trip Book — port de TripBookContainerView/TripBookView (iOS):
 * páginas swipeables, strip inferior de miniaturas con menú contextual,
 * "Nueva" página, tap → editor, y Cerrar+publicar si el trip está activo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TripBookScreen(
    journey: ApiJourney,
    onDismiss: () -> Unit,
    publishViewModel: MemoirPublishViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val persistence = remember { MemoirPersistence(context.applicationContext) }
    val book = remember(journey.id) { TripBookState(journey.id, persistence, scope) }

    // Crossfade book ↔ editor — easeInOut 0.22s como iOS
    androidx.compose.animation.Crossfade(
        targetState = book.isEditing,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "bookEditor",
    ) { editing ->
        if (editing) {
            TripCanvasEditor(book)
        } else {
            TripBookPages(book, journey, persistence, publishViewModel, onDismiss)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripBookPages(
    book: TripBookState,
    journey: ApiJourney,
    persistence: MemoirPersistence,
    publishViewModel: MemoirPublishViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showFinishConfirm by remember { mutableStateOf(false) }
    var isFinishing by remember { mutableStateOf(false) }
    var tripStatus by remember { mutableStateOf(journey.status ?: "planning") }
    val isActive = tripStatus == "active"

    val pagerState = rememberPagerState(initialPage = book.currentPageIndex) { book.pages.size }
    LaunchedEffect(pagerState.currentPage) { book.currentPageIndex = pagerState.currentPage }
    LaunchedEffect(book.currentPageIndex) {
        if (pagerState.currentPage != book.currentPageIndex && book.currentPageIndex < book.pages.size) {
            pagerState.animateScrollToPage(book.currentPageIndex)
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFEDEDED))) {

        // ── Páginas swipeables ───────────────────────────────────────────────
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            val page = book.pages.getOrNull(index) ?: return@HorizontalPager
            PageDisplay(
                page = page, journeyId = journey.id, persistence = persistence,
                onTap = { book.enterEdit(index) },
            )
        }

        // ── Top bar — padding top 56 como iOS (ignoresSafeArea) ─────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 56.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    journey.title ?: journey.destination?.name ?: "trip",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
                Text("Portadas · ${book.pages.size}", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            if (isActive) {
                if (isFinishing) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
                } else {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(BuddyColor.Ink)
                            .clickable { showFinishConfirm = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Text("Cerrar", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(BuddyColor.Brand.copy(alpha = 0.85f))
                        .clickable { book.enterEdit(book.currentPageIndex) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }

        // ── Strip inferior ───────────────────────────────────────────────────
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.92f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${book.currentPageIndex + 1} / ${book.pages.size}",
                fontSize = 11.sp, fontWeight = FontWeight.Medium, color = BuddyColor.InkMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                book.pages.forEachIndexed { index, page ->
                    PageThumbnail(
                        page = page, journeyId = journey.id, persistence = persistence,
                        isSelected = book.currentPageIndex == index,
                        onTap = { book.currentPageIndex = index },
                        onEdit = { book.enterEdit(index) },
                        onDelete = if (book.pages.size > 1) ({ book.deletePage(index) }) else null,
                    )
                }
                // Nueva página — borde punteado dash [4] como iOS
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .size(width = 58.dp, height = 86.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .drawBehind {
                                drawRoundRect(
                                    color = androidx.compose.ui.graphics.Color(0xFF6F625D).copy(alpha = 0.4f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 1.5.dp.toPx(),
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                            floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                                        ),
                                    ),
                                )
                            }
                            .clickable { book.addPage() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Nueva portada", tint = BuddyColor.InkMuted)
                    }
                    Text("Nueva", fontSize = 9.sp, fontWeight = FontWeight.Medium, color = BuddyColor.InkMuted)
                }
            }
        }
    }

    // ── Confirmación de publicación ──────────────────────────────────────────
    if (showFinishConfirm) {
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text("¿Cerrar y publicar trip?") },
            text = { Text("Tu trip quedará completado y visible para la comunidad.") },
            confirmButton = {
                TextButton(onClick = {
                    showFinishConfirm = false
                    isFinishing = true
                    scope.launch {
                        publishViewModel.publish(journey, book.pages.toList(), persistence)
                        tripStatus = "completed"
                        isFinishing = false
                        onDismiss()
                    }
                }) { Text("Cerrar y publicar", color = BuddyColor.Brand) }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirm = false }) { Text("Cancelar") }
            },
        )
    }
}

// ── Página a pantalla completa (solo lectura) ────────────────────────────────

@Composable
private fun PageDisplay(
    page: CollagePage,
    journeyId: String,
    persistence: MemoirPersistence,
    onTap: () -> Unit,
) {
    val thumbnail by produceState<Bitmap?>(null, page.id, page.editVersion) {
        value = withContext(Dispatchers.IO) {
            page.thumbnailFileName?.let { persistence.loadThumbnail(it, journeyId) }
        }
    }
    val bg by produceState<Bitmap?>(null, page.backgroundImageFile) {
        value = withContext(Dispatchers.IO) {
            page.backgroundImageFile?.let { persistence.loadBackground(it, journeyId) }
        }
    }

    Box(Modifier.fillMaxSize().clickable(onClick = onTap)) {
        if (bg != null) {
            Image(bg!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.55f)))
        } else {
            Box(Modifier.fillMaxSize().background(Color(MemoirPersistence.rgbaToArgb(page.backgroundRGBA))))
        }
        when {
            thumbnail != null -> Image(
                thumbnail!!.asImageBitmap(), null,
                Modifier.fillMaxSize().padding(bottom = 132.dp),
                contentScale = ContentScale.Crop,
            )
            page.itemSnapshots.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(bottom = 132.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.PhotoLibrary, contentDescription = null,
                    Modifier.size(48.dp), tint = BuddyColor.InkMuted,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Toca para agregar fotos",
                    fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    color = BuddyColor.InkMuted, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── Miniatura del strip ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageThumbnail(
    page: CollagePage,
    journeyId: String,
    persistence: MemoirPersistence,
    isSelected: Boolean,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var showMenu by remember { mutableStateOf(false) }
    val thumbnail by produceState<Bitmap?>(null, page.id, page.editVersion) {
        value = withContext(Dispatchers.IO) {
            page.thumbnailFileName?.let { persistence.loadThumbnail(it, journeyId) }
        }
    }
    Box {
        Box(
            Modifier
                .size(width = 58.dp, height = 86.dp)
                // Sombra como iOS: seleccionado 0.25/r6/y2, normal 0.1/r3/y2
                .shadow(if (isSelected) 6.dp else 3.dp, RoundedCornerShape(8.dp), clip = false)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(MemoirPersistence.rgbaToArgb(page.backgroundRGBA)))
                .then(
                    if (isSelected) Modifier.border(2.5.dp, BuddyColor.Brand, RoundedCornerShape(8.dp))
                    else Modifier,
                )
                .combinedClickable(onClick = onTap, onLongClick = { showMenu = true }),
        ) {
            thumbnail?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Editar portada") },
                onClick = { showMenu = false; onEdit() },
            )
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text("Eliminar portada", color = Color(0xFFE05B4E)) },
                    onClick = { showMenu = false; onDelete() },
                )
            }
        }
    }
}
