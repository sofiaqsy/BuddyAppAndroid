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
)

// Página del feed "Historias de viajeros"
@Serializable
data class FeedPage(
    val items: List<ApiJourney>,
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
)
