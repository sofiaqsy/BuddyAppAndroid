package com.buddy.app.features.authentication.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.POST

/**
 * Endpoints de sesión y auth de buddy-core — contrato idéntico al que consume
 * iOS (TravelerService/AuthService). Bodies en snake_case como espera el backend.
 */
interface AuthApi {

    @POST("travelers/init")
    suspend fun initTraveler(@Body body: InitRequest): InitResponse

    @POST("travelers/refresh")
    suspend fun refreshToken(@Body body: RefreshRequest): RefreshResponse

    @POST("auth/social")
    suspend fun socialLogin(@Body body: SocialRequest): SocialResponse

    @PATCH("travelers/profile")
    suspend fun completeProfile(@Body body: ProfileRequest)
}

@Serializable
data class InitRequest(@SerialName("device_id") val deviceId: String)

@Serializable
data class InitResponse(
    @SerialName("traveler_id") val travelerId: String,
    val token: String,
    val secret: String? = null,   // solo en la primera creación (idempotente después)
)

@Serializable
data class RefreshRequest(
    @SerialName("traveler_id") val travelerId: String,
    val secret: String,
    @SerialName("device_id") val deviceId: String,
)

@Serializable
data class RefreshResponse(val token: String, val status: String? = null)

@Serializable
data class SocialRequest(
    val provider: String,                              // "google" | "apple"
    @SerialName("identity_token") val identityToken: String,
    @SerialName("full_name") val fullName: String? = null,
)

@Serializable
data class SocialResponse(
    @SerialName("traveler_id") val travelerId: String,
    @SerialName("traveler_token") val travelerToken: String,
    val status: String,
)

@Serializable
data class ProfileRequest(@SerialName("full_name") val fullName: String)
