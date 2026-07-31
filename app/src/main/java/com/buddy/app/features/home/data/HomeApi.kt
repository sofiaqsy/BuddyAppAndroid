package com.buddy.app.features.home.data

import com.buddy.app.core.data.model.ApiDestination
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.core.data.model.ApiLocationResolution
import com.buddy.app.core.data.model.ApiPlaceContext
import com.buddy.app.core.data.model.ApiPlaceResult
import com.buddy.app.core.data.model.ApiRecentHelp
import com.buddy.app.core.data.model.ApiPulseResponse
import com.buddy.app.core.data.model.FeedPage
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Endpoints del Home — los mismos que dispara InicioView al aparecer. */
interface HomeApi {

    /** 204 No Content cuando no hay match (por eso Response<...>). */
    @POST("location/resolve")
    suspend fun resolveLocation(@Body body: ResolveRequest): Response<ApiLocationResolution>

    /** Encuentra o auto-crea el Place geográfico para unas coords (flujo pioneer). */
    @POST("places/resolve")
    suspend fun resolvePlace(@Body body: ResolveRequest): ApiResolvedPlace

    @GET("places/{id}/context")
    suspend fun placeContext(
        @Path("id") id: String,
        @Query("source") source: String,   // "place" | "destination"
    ): ApiPlaceContext

    /** OJO: responde envuelto en { items: [...] }, no un array plano. */
    @GET("destinations")
    suspend fun destinations(@Query("limit") limit: Int = 5): DestinationsResponse

    @GET("feed/stories")
    suspend fun feedStories(
        @Query("limit") limit: Int = 10,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
    ): FeedPage

    @GET("travelers/me/journeys")
    suspend fun myJourneys(): List<ApiJourney>

    @GET("search/places")
    suspend fun searchPlaces(@Query("q") query: String): SearchResponse

    // ── Journeys (trips) — mismo contrato que APIClient.swift ────────────

    @retrofit2.http.POST("journeys")
    suspend fun createJourney(@Body body: CreateJourneyBody): ApiJourney

    @retrofit2.http.PATCH("journeys/{id}")
    suspend fun updateJourneyStatus(
        @retrofit2.http.Path("id") journeyId: String,
        @Body body: JourneyStatusBody,
    )

    /** Elimina UN lugar (journey) del viaje. */
    @retrofit2.http.DELETE("journeys/{id}")
    suspend fun cancelJourney(@retrofit2.http.Path("id") journeyId: String)

    /** Cancela el VIAJE completo (todos sus lugares + apoyos en curso). */
    @retrofit2.http.DELETE("trips/{id}")
    suspend fun cancelTrip(@retrofit2.http.Path("id") tripId: String)

    // ── Memoir: publicar trip (espejo de publishJourney en APIClient.swift) ──

    /** Sube las portadas (thumbnails JPEG) del journey vía buddy-core. */
    @retrofit2.http.Multipart
    @POST("journeys/{id}/pages/upload")
    suspend fun uploadJourneyPages(
        @Path("id") journeyId: String,
        @retrofit2.http.Part parts: List<okhttp3.MultipartBody.Part>,
    )

    @retrofit2.http.PATCH("journeys/{id}")
    suspend fun publishJourney(
        @Path("id") journeyId: String,
        @Body body: PublishBody,
    )

    @retrofit2.http.PATCH("trips/{id}")
    suspend fun publishTrip(
        @Path("id") tripId: String,
        @Body body: PublishBody,
    )

    // ── Comunidad viva (recent help + pulse) ────────────────────────────

    /**
     * GET /matching/recent-help/{destinationId} — actividad reciente en un destino.
     *
     * La ruta es la de matching, no /places/{id}/recent-help: esa no existe en
     * buddy-core y devolvía 404 en silencio (runCatching lo tragaba), así que
     * "Comunidad viva" salía siempre vacía en Android. iOS ya usaba esta.
     */
    @GET("matching/recent-help/{id}")
    suspend fun recentHelpByPlace(@Path("id") placeId: String): List<ApiRecentHelp>

    /** GET /community/pulse — pulso global cuando no hay actividad local. */
    @GET("community/pulse")
    suspend fun communityPulse(): ApiPulseResponse
}

@kotlinx.serialization.Serializable
@kotlinx.serialization.ExperimentalSerializationApi
data class CreateJourneyBody(
    @kotlinx.serialization.SerialName("destination_id") val destinationId: String? = null,
    @kotlinx.serialization.SerialName("place_id") val placeId: String? = null,
    @kotlinx.serialization.SerialName("osm_id") val osmId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val title: String? = null,
    @kotlinx.serialization.SerialName("arrival_at") val arrivalAt: String? = null,
    @kotlinx.serialization.SerialName("knows_how_to_get") val knowsHowToGet: Boolean? = null,
    @kotlinx.serialization.SerialName("has_lodging") val hasLodging: Boolean? = null,
    // Explícito en el body a propósito, aunque true ya sea el default del
    // backend cuando el campo falta: dentro de unos meses, alguien viendo un
    // POST /journeys sin este campo no sabría que existe un segundo
    // comportamiento (attach_to_trip=false — "Compartir un lugar"). Sin
    // @EncodeDefault, kotlinx.serialization omite los campos en su valor
    // default al serializar (mismo footgun que ya mordió a PublishBody) y
    // este viajaría ausente igual que antes — @EncodeDefault fuerza que
    // SIEMPRE vaya en el JSON, sea true o false.
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.ALWAYS)
    @kotlinx.serialization.SerialName("attach_to_trip") val attachToTrip: Boolean = true,
)

@kotlinx.serialization.Serializable
data class JourneyStatusBody(val status: String)

@kotlinx.serialization.Serializable
data class PublishBody(
    // SIN valores por defecto: kotlinx.serialization omite los campos default
    // al serializar (encodeDefaults=false) y el PATCH viajaba con body {} —
    // el backend respondía 200 sin publicar nada.
    val status: String,
    @kotlinx.serialization.SerialName("is_public") val isPublic: Boolean,
)

@Serializable
data class ResolveRequest(val lat: Double, val lng: Double)

@Serializable
data class ApiResolvedPlace(val id: String, val name: String, val city: String? = null)

@Serializable
data class SearchResponse(val items: List<ApiPlaceResult>)

@Serializable
data class DestinationsResponse(val items: List<com.buddy.app.core.data.model.ApiDestination>)
