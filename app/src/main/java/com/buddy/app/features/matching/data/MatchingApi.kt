package com.buddy.app.features.matching.data

import com.buddy.app.core.data.model.ApiUserRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Matching + mensajes — mismos endpoints que APIClient.swift.
 * Ownership (traveler_id / buddy_id / sender_id) siempre lo deriva el
 * backend del JWT; nunca va en el body (igual que iOS).
 */
interface MatchingApi {

    @POST("matching/request")
    suspend fun createHelpRequest(@Body body: HelpRequestBody): ApiHelpRequest

    @DELETE("matching/request/{id}")
    suspend fun cancelRequest(@Path("id") requestId: String)

    @GET("matching/status/{id}")
    suspend fun matchingStatus(@Path("id") requestId: String): ApiMatchingStatus

    @GET("matching/matches")
    suspend fun matches(): List<ApiMatch>

    @GET("matching/my-offers")
    suspend fun myOffers(): List<ApiBuddyOffer>

    @POST("matching/match")
    suspend fun acceptRequest(@Body body: AcceptBody): ApiMatch

    @POST("matching/decline")
    suspend fun declineOffer(@Body body: DeclineBody)

    @PATCH("matching/match/{id}")
    suspend fun updateMatchStatus(@Path("id") matchId: String, @Body body: StatusBody): ApiMatch

    // ── Messages ──────────────────────────────────────────────────────────

    @GET("messages/{matchId}")
    suspend fun messages(
        @Path("matchId") matchId: String,
        @Query("limit") limit: Int = 30,
        @Query("before") before: String? = null,
    ): List<ApiMessage>

    @POST("messages/{matchId}")
    suspend fun sendMessage(@Path("matchId") matchId: String, @Body body: SendMessageBody): ApiMessage

    @PATCH("messages/{matchId}/read")
    suspend fun markRead(@Path("matchId") matchId: String)
}

@Serializable
data class HelpRequestBody(
    @SerialName("destination_id") val destinationId: String,
    val category: String,
    val description: String? = null,
    @SerialName("journey_id") val journeyId: String? = null,
    @SerialName("arrival_at") val arrivalAt: String? = null,
)

@Serializable data class AcceptBody(@SerialName("request_id") val requestId: String)
@Serializable data class DeclineBody(@SerialName("request_id") val requestId: String)
@Serializable data class StatusBody(val status: String)

@Serializable
data class SendMessageBody(
    val content: String,
    val type: String = "text",
    @SerialName("idempotency_key") val idempotencyKey: String? = null,
)

// ── Modelos (espejo de APIModels.swift) ───────────────────────────────────

@Serializable
data class ApiHelpRequest(
    val id: String,
    @SerialName("traveler_id") val travelerId: String,
    @SerialName("destination_id") val destinationId: String? = null,
    val category: String,
    val description: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class ApiMatchingStatus(
    val status: String,        // "searching" | "matched" | "failed" | "cancelled" | "none"
    val position: Int? = null,
    val total: Int? = null,
    val buddy: ApiUserRef? = null,
)

@Serializable
data class ApiMatch(
    val id: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("traveler_id") val travelerId: String,
    @SerialName("buddy_id") val buddyId: String,
    val status: String,
    val traveler: ApiUserRef? = null,
    val buddy: ApiUserRef? = null,
    @SerialName("feedback_submitted") val feedbackSubmitted: Boolean? = null,
)

@Serializable
data class ApiBuddyOffer(
    val id: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("help_request") val helpRequest: OfferRequest? = null,
) {
    @Serializable
    data class OfferRequest(
        val id: String,
        val category: String? = null,
        val description: String? = null,
        val users: ApiUserRef? = null,
    )
}

@Serializable
data class ApiMessage(
    val id: String,
    @SerialName("match_id") val matchId: String? = null,
    @SerialName("sender_id") val senderId: String? = null,
    val type: String? = null,
    val content: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val users: ApiUserRef? = null,
)
