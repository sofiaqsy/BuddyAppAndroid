package com.buddy.app.features.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.core.data.model.ApiUserRef
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.features.trips.TravelerStoriesViewModel

private val Edge = 20.dp
private val CardShape = RoundedCornerShape(16.dp)

/**
 * Pestaña Comunidad, espejo de CommunityView (iOS, Keyla 19 Nov 2026): lo que
 * viven otros viajeros, en filas como Inicio. Los trips propios viven en Yo ->
 * "Mis trips".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    modifier: Modifier = Modifier,
    onOpenProfile: (travelerId: String, name: String?, avatarUrl: String?) -> Unit = { _, _, _ -> },
    onOpenDestination: (destinationId: String, name: String) -> Unit = { _, _ -> },
    scrollToTopToken: Int = 0,
    viewModel: TravelerStoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (state.stories.isEmpty()) viewModel.load() }
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopToken) { if (scrollToTopToken > 0) listState.animateScrollToItem(0) }
    var viewer by remember { mutableStateOf<Triple<String, List<ApiJourney>, ApiJourney>?>(null) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val stories = state.stories

    PullToRefreshBox(
        isRefreshing = state.isLoading && stories.isNotEmpty(),
        onRefresh = { viewModel.load() },
        modifier = modifier.fillMaxSize().background(BuddyColor.Canvas),
    ) {
        LazyColumn(state = listState, contentPadding = PaddingValues(top = topInset + 16.dp, bottom = 32.dp),
                   modifier = Modifier.fillMaxSize().testTag("communityList")) {
            item(key = "title") {
                Column(Modifier.padding(horizontal = Edge), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Comunidad", style = BuddyType.DisplayXL, color = BuddyColor.Brand, modifier = Modifier.semantics { heading() })
                    Text("Trips de viajeros cerca de ti", style = BuddyType.Footnote, color = BuddyColor.InkMuted)
                }
                Spacer(Modifier.height(24.dp))
            }
            when {
                state.isLoading && stories.isEmpty() -> item(key = "skeleton") { Skeleton() }
                state.failed -> item(key = "failed") { Failed { viewModel.load() } }
                stories.isEmpty() -> item(key = "empty") { Empty() }
                else -> {
                    val travelers = CommunityLogic.travelers(stories)
                    if (travelers.size > 1) item(key = "travelers") {
                        TravelersRow(travelers, onOpenProfile); Spacer(Modifier.height(36.dp))
                    }
                    item(key = "recent") {
                        StoryRow("Recientes", "Lo último que compartieron", stories, true, null, { viewer = Triple("Recientes", stories, it) }, onOpenProfile)
                        Spacer(Modifier.height(36.dp))
                    }
                    CommunityLogic.bigGroups(stories).forEach { (name, items) ->
                        item(key = "dest-$name") {
                            val id = CommunityLogic.destId(items)
                            StoryRow("En $name", "${items.size} trips", items, false,
                                     id?.let { { onOpenDestination(it, name) } }, { viewer = Triple("En $name", items, it) }, onOpenProfile)
                            Spacer(Modifier.height(36.dp))
                        }
                    }
                    val singles = CommunityLogic.singles(stories)
                    if (singles.isNotEmpty()) item(key = "more") {
                        StoryRow("Más trips", "Otros destinos de viajeros", singles, true, null, { viewer = Triple("Más trips", singles, it) }, onOpenProfile)
                    }
                }
            }
        }
    }

    viewer?.let { (row, list, j) ->
        // La fila entera: deslizar sigue con la siguiente publicación.
        com.buddy.app.features.profile.StoryViewerDialog(
            journey = j,
            thumbsProvider = { x -> x.pageThumbs?.takeIf { it.isNotEmpty() } ?: listOfNotNull(x.coverUrl) },
            onDismiss = { viewer = null },
            journeys = list,
            // La tarjeta de ESTA fila (la misma publicación puede estar en dos).
            sourceKeyPrefix = "$row|",
        )
    }
}

@Composable
private fun TravelersRow(travelers: List<ApiUserRef>, onOpenProfile: (String, String?, String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.padding(horizontal = Edge), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Viajeros", style = BuddyType.Title3, color = BuddyColor.Brand, modifier = Modifier.semantics { heading() })
            Text("${travelers.size}", style = BuddyType.FootnoteBold, color = BuddyColor.Brand,
                 modifier = Modifier.background(BuddyColor.PaleSage, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = Edge), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(travelers, key = { it.id!! }) { u ->
                val name = CommunityLogic.capitalized(u.fullName)
                Column(Modifier.width(72.dp).clickable { onOpenProfile(u.id!!, u.fullName, u.avatarUrl) }
                    .semantics(mergeDescendants = true) { contentDescription = "Perfil de $name" }
                    .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(70.dp).border(2.dp, BuddyColor.Brand, CircleShape).padding(3.dp), contentAlignment = Alignment.Center) {
                        Avatar(u.avatarUrl, name, 64)
                    }
                    Text(CommunityLogic.firstName(u.fullName), style = BuddyType.Caption1, color = BuddyColor.Ink, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun StoryRow(title: String, subtitle: String, items: List<ApiJourney>, showsPlace: Boolean,
                     onOpenMap: (() -> Unit)?, onOpenStory: (ApiJourney) -> Unit,
                     onOpenProfile: (String, String?, String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // "En <destino>" abre el mapa de ese destino (iOS).
        Column(Modifier.padding(horizontal = Edge).then(if (onOpenMap != null) Modifier.clickable(onClick = onOpenMap) else Modifier),
               verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = BuddyType.Title3, color = BuddyColor.Brand, modifier = Modifier.semantics { heading() })
                if (onOpenMap != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Abrir el mapa", tint = BuddyColor.Brand, modifier = Modifier.size(18.dp))
            }
            Text(subtitle, style = BuddyType.Footnote, color = BuddyColor.InkMuted)
        }
        val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
        com.buddy.app.core.designsystem.components.FollowViewer(rowState) { k ->
            if (k.startsWith("$title|")) items.indexOfFirst { "$title|${it.id}" == k } else -1
        }
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = Edge, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.id }) { j -> StoryCard(j, showsPlace, { onOpenStory(j) }, onOpenProfile, "$title|") }
        }
    }
}

/** Tarjeta vertical: foto 200x250, destino, fotos y autor (iOS StoryCarouselCard). */
@Composable
private fun StoryCard(j: ApiJourney, showsPlace: Boolean, onOpen: () -> Unit, onOpenProfile: (String, String?, String?) -> Unit, keyPrefix: String = "") {
    val cover = j.pageThumbs?.firstOrNull() ?: j.coverUrl
    val place = CommunityLogic.destName(j)
    val author = CommunityLogic.capitalized(j.users?.fullName)
    val photos = j.pageThumbs?.size ?: 0
    Column(Modifier.width(200.dp)
        .shadow(6.dp, CardShape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.05f))
        .clip(CardShape).background(BuddyColor.Surface).testTag("storyCard")) {
        Box(Modifier.width(200.dp).height(250.dp).background(BuddyColor.PaleSage)
            .then(com.buddy.app.core.designsystem.components.photoSourceModifier(keyPrefix + j.id)).clickable(onClick = onOpen)
            .semantics { contentDescription = "Trip de $author en $place" }) {
            AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (showsPlace) {
                Row(Modifier.padding(10.dp).height(28.dp).background(BuddyColor.Surface, CircleShape).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Filled.Place, null, tint = BuddyColor.Brand, modifier = Modifier.size(14.dp))
                    Text(place, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BuddyColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (photos > 1) {
                Text("$photos fotos", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                     modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 10.dp)
                         .background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        val uid = j.users?.id
        Row(Modifier.fillMaxWidth().then(if (uid != null) Modifier.clickable { onOpenProfile(uid, j.users?.fullName, j.users?.avatarUrl) } else Modifier)
            .padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(j.users?.avatarUrl, author, 26)
            Text(author, style = BuddyType.FootnoteBold, color = BuddyColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Avatar(url: String?, name: String, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(BuddyColor.PaleSage), contentAlignment = Alignment.Center) {
        Text(name.take(1), fontSize = (size * 0.4f).sp, fontWeight = FontWeight.SemiBold, color = BuddyColor.Brand)
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun Skeleton() {
    Column(Modifier.padding(horizontal = Edge).fillMaxWidth().clip(CardShape).background(BuddyColor.Surface)
        .semantics { contentDescription = "Cargando trips" }) {
        Box(Modifier.fillMaxWidth().height(300.dp).background(BuddyColor.GroupedBg))
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(24.dp).background(BuddyColor.GroupedBg, CircleShape))
            Box(Modifier.width(100.dp).height(13.dp).background(BuddyColor.GroupedBg, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun Failed(onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("No pudimos cargar los trips", style = BuddyType.Callout, color = BuddyColor.InkMuted)
        Text("Reintentar", style = BuddyType.FootnoteBold, color = BuddyColor.Ink,
             modifier = Modifier.clip(CircleShape).background(BuddyColor.Surface).border(1.dp, BuddyColor.Border, CircleShape)
                 .clickable(onClick = onRetry).padding(horizontal = 24.dp, vertical = 10.dp))
    }
}

@Composable
private fun Empty() {
    Column(Modifier.fillMaxWidth().padding(horizontal = Edge, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Aún nadie ha contado su paso por aquí", style = BuddyType.Callout, color = BuddyColor.Ink)
        Text("Cuando termines tu trip, el tuyo será el primero.", style = BuddyType.Footnote, color = BuddyColor.InkMuted, textAlign = TextAlign.Center)
    }
}

