package com.buddy.app.features.profile.data

import com.buddy.app.core.data.model.FeedPage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

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
}

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
        @SerialName("total_helps") val totalHelps: Int? = null,
        @SerialName("rating_avg") val ratingAvg: Double? = null,
        @SerialName("verification_status") val verificationStatus: String? = null,
    )
}
