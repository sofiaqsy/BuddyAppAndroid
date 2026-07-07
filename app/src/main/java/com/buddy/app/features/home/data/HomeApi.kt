package com.buddy.app.features.home.data

import com.buddy.app.core.data.model.ApiDestination
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.core.data.model.ApiLocationResolution
import com.buddy.app.core.data.model.ApiPlaceContext
import com.buddy.app.core.data.model.ApiPlaceResult
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

    @GET("destinations")
    suspend fun destinations(@Query("limit") limit: Int = 5): List<ApiDestination>

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
}

@kotlinx.serialization.Serializable
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
)

@kotlinx.serialization.Serializable
data class JourneyStatusBody(val status: String)

@Serializable
data class ResolveRequest(val lat: Double, val lng: Double)

@Serializable
data class ApiResolvedPlace(val id: String, val name: String, val city: String? = null)

@Serializable
data class SearchResponse(val items: List<ApiPlaceResult>)
