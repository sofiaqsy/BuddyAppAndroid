package com.buddy.app.features.trips.map

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Lo que el mapa necesita y /places?destination_id no da: la PORTADA de cada
 * lugar y sus fotos.
 *
 * El rail de abajo son tarjetas con foto, no filas con un pin, y sin imagen
 * todas se ven iguales — que es exactamente lo que el mapa ya te dice. Por eso
 * usa guide/spots (el mismo endpoint de la guía en iOS): trae cover_url ya
 * resuelta, incluso para los lugares que no la tenían guardada.
 */
interface MapApi {

    /** Los lugares del destino CON portada y categoría curada. */
    @GET("places/{id}/guide/spots")
    suspend fun guideSpots(
        @Path("id") destinationId: String,
        @Query("source") source: String = "destination",
        @Query("limit") limit: Int = 50,
    ): ApiGuideSpotsResponse

    /**
     * Las fotos de UN lugar (source=spot, no la ciudad entera).
     *
     * Vienen agrupadas por visita porque cada aporte tiene autor; el mapa las
     * aplana para mostrarlas como galería del sitio.
     */
    @GET("places/{id}/gallery")
    suspend fun spotGallery(
        @Path("id") spotId: String,
        @Query("source") source: String = "spot",
        @Query("limit") limit: Int = 20,
    ): ApiGalleryResponse

    @GET("destinations/{id}/buddies")
    suspend fun destinationBuddies(@Path("id") destinationId: String): ApiDestinationBuddiesResponse
}

@Serializable
data class ApiGuideSpotsResponse(val spots: List<ApiGuideSpot> = emptyList())

@Serializable
data class ApiGuideSpot(
    val id: String,
    val name: String,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    @SerialName("cover_url") val coverUrl: String? = null,
    /** "pending" mientras espera aprobación — solo llega a quien lo propuso. */
    val status: String? = null,
    @SerialName("place_category") val category: ApiSpotCategory? = null,
) {
    val categoryName: String? get() = category?.name
    val estaPendiente: Boolean get() = status == "pending"
}

@Serializable
data class ApiSpotCategory(val name: String? = null, val icon: String? = null)

@Serializable
data class ApiGalleryResponse(
    val visits: List<ApiGalleryVisit> = emptyList(),
    val totalPhotos: Int = 0,
    val buddyCount: Int = 0,
) {
    /**
     * Todas las fotos con su autor, en el orden en que llegaron las visitas.
     *
     * El servidor ya intercala a los contribuidores (nadie ocupa más de dos
     * seguidas), así que aplanar respeta esa mezcla: la galería se ve como el
     * lugar, no como el álbum de la persona que más subió.
     */
    fun fotos(): List<FotoDeLugar> = visits.flatMap { v ->
        v.photos.map { FotoDeLugar(url = it, autor = v.traveler?.fullName) }
    }
}

@Serializable
data class ApiGalleryVisit(
    @SerialName("journey_id") val journeyId: String,
    val traveler: com.buddy.app.core.data.model.ApiUserRef? = null,
    val photos: List<String> = emptyList(),
)

data class FotoDeLugar(val url: String, val autor: String?)

@Serializable
data class ApiDestinationBuddiesResponse(
    val buddies: List<com.buddy.app.core.data.model.ApiPlaceBuddy> = emptyList(),
)
