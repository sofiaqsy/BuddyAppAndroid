package com.buddy.app.core.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modelos de respuesta de buddy-core — espejo de APIModels.swift.
 * OJO: la mayoría de endpoints devuelven snake_case (filas de DB directas);
 * iOS lo resuelve con convertFromSnakeCase global, aquí con @SerialName por
 * campo porque algunos endpoints (location/resolve, places/:id/context)
 * construyen camelCase a mano y una estrategia global rompería esos.
 */

// POST /location/resolve
@Serializable
data class ApiLocationResolution(
    val destinationId: String,
    val destinationName: String,
    val distanceMeters: Int,
    val matchedBy: String,     // "polygon" | "radius"
    val confidence: Double,
)

// GET /places/:id/context
@Serializable
data class ApiPlaceContext(
    val buddies: Int,
    val totalBuddies: Int,
    val stories: Int,
    val status: String,        // "active" | "growing" | "busy" | "pioneer"
)

// GET /destinations
@Serializable
data class ApiDestination(
    val id: String,
    val name: String,
    val city: String,
    val country: String,
    val lat: Double,
    val lng: Double,
    @SerialName("radius_meters") val radiusMeters: Int? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val active: Boolean,
)

// GET /search/places
@Serializable
data class ApiPlaceResult(
    val id: String,
    val source: String,        // "place" | "destination" | "nominatim"
    val title: String,
    val subtitle: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

@Serializable
data class ApiUserRef(
    val id: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class ApiDestinationRef(
    val id: String? = null,
    val name: String,
    val city: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

// GET /feed/stories y /travelers/me/journeys — subset de APIJourney (iOS)
@Serializable
data class ApiJourney(
    val id: String,
    val title: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val status: String,
    @SerialName("is_public") val isPublic: Boolean? = null,
    @SerialName("likes_count") val likesCount: Int? = null,
    @SerialName("arrival_at") val arrivalAt: String? = null,
    @SerialName("departure_at") val departureAt: String? = null,
    val destination: ApiDestinationRef? = null,
    val users: ApiUserRef? = null,
    @SerialName("buddy_count") val buddyCount: Int? = null,
    @SerialName("destination_id") val destinationId: String? = null,
    @SerialName("moment_count") val momentCount: Int? = null,
    @SerialName("place_count") val placeCount: Int? = null,
    @SerialName("sticker_count") val stickerCount: Int? = null,
    @SerialName("page_thumbs") val pageThumbs: List<String>? = null,
    @SerialName("trip_id") val tripId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

// Página del feed "Historias de viajeros"
@Serializable
data class FeedPage(
    val items: List<ApiJourney>,
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
)

// MARK: – Matching & Help Requests

// GET /matching/requests/:id/status — estado de búsqueda de buddy
@Serializable
data class ApiMatchingStatus(
    val status: String,         // "searching" | "matched" | "failed" | "cancelled" | "none"
    val position: Int? = null,  // candidato actual (1-based), solo cuando searching
    val total: Int? = null,     // total de candidatos, solo cuando searching
    val buddy: ApiUserRef? = null,  // solo cuando status == "matched"
)

// GET /matching/matches o POST /matching/requests/:id/accept
@Serializable
data class ApiMatch(
    val id: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("traveler_id") val travelerId: String,
    @SerialName("buddy_id") val buddyId: String,
    val status: String,         // "pending" | "accepted" | "active" | "completed"
    @SerialName("matched_at") val matchedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val traveler: ApiUserRef? = null,
    val buddy: ApiUserRef? = null,
)

// POST /matching/requests — crear solicitud de ayuda
@Serializable
data class ApiHelpRequest(
    @SerialName("destination_id") val destinationId: String? = null,
    val category: String,       // "transport" | "accommodation" | "food" | etc.
    val description: String? = null,
)

// GET /places/:id/recent-help — actividad local en un destino
@Serializable
data class ApiRecentHelp(
    val id: String,
    @SerialName("completed_at") val completedAt: String? = null,
    val buddy: ApiUserRef? = null,
)

// GET /community/pulse — pulso global de la red cuando no hay actividad local
@Serializable
data class ApiPulseItem(
    val type: String,          // "traveling" | "helped" | "ready"
    val city: String,
    val count: Int? = null,
    val at: String? = null,
) {
    val id: String get() = "$type-$city-${at ?: 0}"
}

@Serializable
data class ApiPulseResponse(
    val items: List<ApiPulseItem>,
)

// GET /feed/place-shares — lugares que la comunidad ya documentó por aquí.
// Espejo de APIPlaceCard (iOS). El item es el LUGAR, no la foto: el carrusel
// dibuja una card por URL de coverUrls, así que ese arreglo —y no photoCount—
// es lo que decide qué se ve y en qué orden.
@Serializable
data class ApiPlaceCard(
    val id: String,
    val name: String,
    /** Con esto se le puede pedir al mapa la guía completa del destino en vez
     *  de abrir un mapa nuevo. Sin él la card no navega. */
    @SerialName("destination_id") val destinationId: String? = null,
    @SerialName("destination_name") val destinationName: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("cover_urls") val coverUrls: List<String>? = null,
    /** Las mismas fotos que [coverUrls] pero cada una CON SU AUTOR.
     *
     *  Un lugar puede estar documentado por varias personas y el carrusel dibuja
     *  una tarjeta por foto: con un solo autor por tarjeta, todas quedaban
     *  firmadas por quien publicó la última. Solo lo manda /feed/place-shares. */
    @SerialName("cover_photos") val coverPhotos: List<ApiPlaceCoverPhoto>? = null,
    /** Autor de la portada. Sirve de respaldo donde no hay [coverPhotos] —el
     *  perfil, donde todas son de la misma persona—, nunca para firmar una foto
     *  concreta si sí lo hay. */
    @SerialName("cover_author_name") val coverAuthorName: String? = null,
    @SerialName("cover_author_avatar_url") val coverAuthorAvatarUrl: String? = null,
    /** Qué ES el lugar ("Café", "Alojamiento"), del catálogo curado de spots —
     *  no una categoría de ayuda. */
    val category: String? = null,
    /** "approved" | "pending". Ausente en los endpoints que solo devuelven
     *  aprobados; nulo se lee como aprobado. */
    val status: String? = null,
    @SerialName("photo_count") val photoCount: Int = 0,
    @SerialName("is_new") val isNew: Boolean = false,
    @SerialName("buddy_count") val buddyCount: Int = 0,
    val buddies: List<ApiPlaceBuddy> = emptyList(),
) {
    /** Propuesto y aún sin aprobar. Solo debería llegar true a quien lo propuso:
     *  el backend no manda pendientes a nadie más. */
    val estaPendiente: Boolean get() = status == "pending"

    /** "6 buddies en Villa Rica" — nombrar el destino evita dar a entender que
     *  esos buddies están dentro del local. */
    val buddyLabel: String?
        get() {
            if (buddyCount <= 0) return null
            val noun = if (buddyCount == 1) "buddy" else "buddies"
            return if (destinationName != null) "$buddyCount $noun en $destinationName"
            else "$buddyCount $noun"
        }

    companion object {
        /** Tarjetas de relleno para el esqueleto: permiten dibujar el carrusel
         *  REAL en vez de una silueta parecida. Cualquier réplica hecha a mano
         *  se desalinea y el layout salta al llegar los datos.
         *
         *  coverUrl nulo a propósito — el placeholder de la imagen ya se pinta
         *  solo, y una URL falsa dispararía una petición condenada a fallar. */
        fun placeholders(n: Int = 3): List<ApiPlaceCard> = (0 until n).map { i ->
            ApiPlaceCard(
                id = "placeholder-$i",
                name = "Nombre del lugar",
                coverAuthorName = "Buddy",
                category = "Categoría",
            )
        }
    }
}

@Serializable
data class ApiPlaceBuddy(
    /** traveler_id y no id: es lo que manda place_cards_nearby, y es lo que
     *  hace falta para abrir el perfil de esa persona. */
    @SerialName("traveler_id") val travelerId: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/** Una foto del carrusel con quién la aportó. */
@Serializable
data class ApiPlaceCoverPhoto(
    val url: String,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("author_avatar_url") val authorAvatarUrl: String? = null,
)

@Serializable
data class ApiPlaceCardsResponse(
    val items: List<ApiPlaceCard>,
)
