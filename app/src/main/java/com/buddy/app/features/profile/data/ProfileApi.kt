package com.buddy.app.features.profile.data

import com.buddy.app.core.data.model.ApiDestination
import com.buddy.app.core.data.model.ApiDestinationRef
import com.buddy.app.core.data.model.FeedPage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Endpoints del perfil — mismos que consume YoView (iOS). */
interface ProfileApi {

    @GET("users/me")
    suspend fun me(): ApiUser

    @GET("users/{id}/stickers")
    suspend fun stickers(@Path("id") travelerId: String): List<ApiUserSticker>

    @GET("users/{id}/trips")
    suspend fun trips(@Path("id") travelerId: String): FeedPage

    @PATCH("users/{id}")
    suspend fun updateBio(@Path("id") travelerId: String, @Body body: BioBody)

    @DELETE("users/me")
    suspend fun deleteAccount()

    @GET("buddy/me")
    suspend fun buddyMe(): ApiBuddyMe

    @POST("buddy/me")
    suspend fun becomeBuddy(): ApiBuddyMe

    /** Portadas publicadas de un journey — para el visor de historia. */
    @GET("journeys/{id}/pages")
    suspend fun journeyPages(@Path("id") journeyId: String): List<ApiJourneyPage>

    /** Despublica un journey del perfil (cancelled + is_public=false). */
    /** Cancela el VIAJE completo: sus lugares y los apoyos en curso.
     *
     *  Es lo que hay que llamar para borrar una publicación del perfil.
     *  /users/:id/trips agrupa los journeys por viaje —feed_trip_json_by_trip
     *  devuelve 'id', j_group.trip_id—, así que el id de una tarjeta del perfil
     *  es de un TRIP. */
    @DELETE("trips/{id}")
    suspend fun cancelTrip(@Path("id") tripId: String)

    @DELETE("journeys/{id}")
    suspend fun deleteJourney(@Path("id") journeyId: String)

    /** Foto de perfil — multipart "avatar", espejo de uploadAvatar (iOS). */
    @retrofit2.http.Multipart
    @POST("users/me/avatar")
    suspend fun uploadAvatar(
        @retrofit2.http.Part avatar: okhttp3.MultipartBody.Part,
    ): AvatarResponse

    // ── "Sé buddy en mi ciudad" — espejo de BuddyProfileView (iOS) ──────────

    /** allowed: is_available, specialties, max_active_matches, place_ids, coverage. */
    @PATCH("buddy/me")
    suspend fun updateBuddyMe(@Body body: UpdateBuddyMeBody): ApiBuddyMe

    @GET("places/{id}/guide")
    suspend fun placeGuide(@Path("id") id: String, @Query("source") source: String): ApiPlaceGuide

    @GET("places/geo/{id}")
    suspend fun geoPlace(@Path("id") id: String): ApiPlaceRef

    @GET("destinations/{id}")
    suspend fun destination(@Path("id") id: String): ApiDestination

    // ── Guía del lugar: editor de spots — espejo de BuddyGuideMapSheet (iOS) ─

    @GET("places/{id}/guide/spots")
    suspend fun guideSpots(
        @Path("id") id: String,
        @Query("source") source: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): ApiPlaceGuideSpotsPage

    @POST("places")
    suspend fun createSpot(@Body body: CreateSpotBody): ApiPlaceGuideSpot

    @PATCH("places/{id}")
    suspend fun updateSpot(@Path("id") id: String, @Body body: UpdateSpotBody): ApiPlaceGuideSpot

    @DELETE("places/{id}")
    suspend fun deleteSpot(@Path("id") id: String)
}

@Serializable
data class AvatarResponse(@SerialName("avatar_url") val avatarUrl: String)

@Serializable
data class ApiJourneyPage(
    val id: String,
    @SerialName("page_index") val pageIndex: Int = 0,
    @SerialName("thumbnail_url") val thumbnailUrl: String,
)

@Serializable data class BioBody(val bio: String)

@Serializable
data class ApiUser(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String? = null,
    val nationality: String? = null,
    @SerialName("member_since") val memberSince: String? = null,
)

@Serializable
data class ApiUserSticker(
    val id: String,
    @SerialName("unlocked_at") val unlockedAt: String? = null,
    @SerialName("sticker_catalog") val stickerCatalog: StickerCatalog? = null,
) {
    @Serializable
    data class StickerCatalog(
        val name: String? = null,
        @SerialName("image_url") val imageUrl: String? = null,
    )
}

@Serializable
data class ApiBuddyMe(
    @SerialName("is_buddy") val isBuddy: Boolean = false,
    val profile: BuddyProfile? = null,
) {
    @Serializable
    data class BuddyProfile(
        val id: String,
        @SerialName("is_available") val isAvailable: Boolean = false,
        val specialties: List<String>? = null,
        @SerialName("total_helps") val totalHelps: Int? = null,
        @SerialName("rating_avg") val ratingAvg: Double? = null,
        @SerialName("rating_count") val ratingCount: Int? = null,
        @SerialName("offers_accepted") val offersAccepted: Int? = null,
        @SerialName("verification_status") val verificationStatus: String? = null,
        @SerialName("destination_ids") val destinationIds: List<String>? = null,
        @SerialName("active_zone_ids") val activeZoneIds: List<String>? = null,
        @SerialName("place_ids") val placeIds: List<String>? = null,
        val destination: ApiDestinationRef? = null,
    )
}

/** Body de PATCH /buddy/me — solo se envían los campos que cambian. */
@Serializable
data class UpdateBuddyMeBody(
    val specialties: List<String>? = null,
    val coverage: BuddyCoverageInput? = null,
    @SerialName("place_ids") val placeIds: List<String>? = null,
    @SerialName("is_available") val isAvailable: Boolean? = null,
)

/** Espejo de BuddyCoverageInput (iOS) — ciudad de cobertura elegida en el picker. */
@Serializable
data class BuddyCoverageInput(
    val destinationId: String? = null,
    val city: String,
    val countryCode: String,
    val lat: Double? = null,
    val lng: Double? = null,
)

@Serializable
data class ApiPlaceGuide(
    val spotCount: Int = 0,
    val visitCount: Int = 0,
    val stickerCount: Int = 0,
    val lat: Double? = null,
    val lng: Double? = null,
    val spots: List<ApiPlaceGuideSpot>? = null,
    val destId: String? = null,
)

@Serializable
data class ApiPlaceGuideSpot(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    @SerialName("cover_url") val coverUrl: String? = null,
)

@Serializable
data class ApiPlaceRef(val id: String, val name: String, val city: String? = null)

@Serializable
data class ApiPlaceGuideSpotsPage(
    val spots: List<ApiPlaceGuideSpot> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
data class CreateSpotBody(
    val name: String,
    val lat: Double,
    val lng: Double,
    val destinationId: String,
    val placeType: String? = null,
)

/** PATCH parcial — solo se envían los campos que cambian. */
@Serializable
data class UpdateSpotBody(
    val name: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val placeType: String? = null,
)
