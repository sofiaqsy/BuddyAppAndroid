package com.buddy.app.features.matching.data

import com.buddy.app.core.data.model.ApiDestinationRef
import com.buddy.app.core.data.model.ApiUserRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
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

    /**
     * GET /matching/my-request — mi solicitud abierta, si la hay.
     *
     * Sin destino en la ruta a propósito: la búsqueda puede haber empezado en
     * otra pantalla (el mapa, por ejemplo) y el Home tiene que enterarse igual.
     * Responde 200 con `null` cuando no hay ninguna — "no tienes solicitud" es
     * una respuesta, no un error. De ahí Response<> en vez del tipo desnudo.
     */
    @GET("matching/my-request")
    suspend fun myRequest(): retrofit2.Response<ApiHelpRequest>

    @GET("matching/my-offers")
    suspend fun myOffers(): List<ApiBuddyOffer>

    /** Solicitudes activas dentro de la cobertura del buddy (respaldo comunitario). */
    @GET("matching/requests/for-buddy")
    suspend fun requestsForBuddy(): List<ApiHelpRequest>

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

    // Subida multipart — el backend crea el registro y devuelve el ApiMessage
    // completo (mismos endpoints que uploadChatImage / AudioRecorderVM.upload en iOS).
    @Multipart
    @POST("messages/{matchId}/image")
    suspend fun uploadImage(
        @Path("matchId") matchId: String,
        @Part("client_message_id") clientMessageId: RequestBody,
        @Part image: MultipartBody.Part,
    ): ApiMessage

    @Multipart
    @POST("messages/{matchId}/audio")
    suspend fun uploadAudio(
        @Path("matchId") matchId: String,
        @Part("client_message_id") clientMessageId: RequestBody,
        @Part audio: MultipartBody.Part,
    ): ApiMessage

    // ── Cierre / reputación / reporte (mismos endpoints que iOS) ──────────

    @POST("matching/feedback")
    suspend fun submitFeedback(@Body body: FeedbackBody)

    @POST("users/report")
    suspend fun reportUser(@Body body: ReportUserBody)

    @GET("matching/request-info/{requestId}")
    suspend fun requestInfo(@Path("requestId") requestId: String): ApiHelpRequestInfo

    @GET("places")
    suspend fun places(@Query("destination_id") destinationId: String): List<ApiPlace>
}

@Serializable
data class HelpRequestBody(
    @SerialName("destination_id") val destinationId: String? = null,
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
    val users: ApiUserRef? = null,
    val destination: ApiDestinationRef? = null,
    // Solo presentes en GET /matching/requests/for-buddy — metadata de
    // "Oportunidades para ayudar" (respaldo comunitario con ventana de
    // exclusividad para el candidato oficial).
    @SerialName("candidate_count") val candidateCount: Int? = null,
    @SerialName("is_priority_for_me") val isPriorityForMe: Boolean? = null,
    @SerialName("is_community_unlocked") val isCommunityUnlocked: Boolean? = null,
    @SerialName("community_unlocks_in") val communityUnlocksIn: Int? = null,
    @SerialName("offer_seconds_remaining") val offerSecondsRemaining: Int? = null,
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
    @SerialName("matched_at") val matchedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val traveler: ApiUserRef? = null,
    val buddy: ApiUserRef? = null,
    @SerialName("feedback_submitted") val feedbackSubmitted: Boolean? = null,
    /**
     * El backend siempre lo manda en /matching/matches. Trae el destino, que
     * es lo que deja saber DESDE DÓNDE piden ayuda — un buddy puede estar
     * atendiendo varios lugares a la vez y sin esto las conversaciones son
     * indistinguibles.
     */
    @SerialName("help_request") val helpRequest: MatchHelpRequest? = null,
)

@Serializable
data class MatchHelpRequest(
    val category: String? = null,
    val description: String? = null,
    val destination: ApiDestinationRef? = null,
)

@Serializable
data class FeedbackBody(
    @SerialName("match_id") val matchId: String,
    val feeling: String,
    @SerialName("commercial_pressure") val commercialPressure: String,
)

@Serializable
data class ReportUserBody(
    @SerialName("reported_user_id") val reportedUserId: String,
    val reason: String,
    val details: String? = null,
    @SerialName("match_id") val matchId: String? = null,
)

@Serializable
data class ApiHelpRequestInfo(
    @SerialName("destination_id") val destinationId: String? = null,
    val category: String? = null,
)

@Serializable
data class ApiPlace(
    val id: String,
    val name: String,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    @SerialName("place_type") val placeType: String? = null,
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
        @SerialName("arrival_at") val arrivalAt: String? = null,
        val destination: com.buddy.app.core.data.model.ApiDestinationRef? = null,
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
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val users: ApiUserRef? = null,
)
