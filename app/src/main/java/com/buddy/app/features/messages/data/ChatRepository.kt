package com.buddy.app.features.messages.data

import com.buddy.app.core.network.SseClient
import com.buddy.app.core.network.SseEvent
import com.buddy.app.features.matching.data.ApiHelpRequestInfo
import com.buddy.app.features.matching.data.ApiMatch
import com.buddy.app.features.matching.data.ApiMessage
import com.buddy.app.features.matching.data.ApiPlace
import com.buddy.app.features.matching.data.FeedbackBody
import com.buddy.app.features.matching.data.MatchingApi
import com.buddy.app.features.matching.data.ReportUserBody
import com.buddy.app.features.matching.data.SendMessageBody
import com.buddy.app.features.matching.data.StatusBody
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Chat de un match — espejo de la capa de red de BuddyChatView (iOS). */
@Singleton
class ChatRepository @Inject constructor(
    private val api: MatchingApi,
    private val sse: SseClient,
    private val matchingStore: com.buddy.app.core.data.store.MatchingStore,
) {
    suspend fun matches(): List<ApiMatch> = matchingStore.load("chat")

    suspend fun messages(matchId: String, limit: Int = 30, before: String? = null): List<ApiMessage> =
        api.messages(matchId, limit, before)

    suspend fun send(matchId: String, content: String, idempotencyKey: String? = null): ApiMessage =
        api.sendMessage(matchId, SendMessageBody(content, idempotencyKey = idempotencyKey ?: UUID.randomUUID().toString()))

    suspend fun markRead(matchId: String) = runCatching { api.markRead(matchId) }.let { }

    /** Cierre del match — mismo PATCH status=completed que iOS updateMatchStatus. */
    suspend fun completeMatch(matchId: String): ApiMatch =
        api.updateMatchStatus(matchId, StatusBody("completed"))

    suspend fun submitFeedback(matchId: String, feeling: String, pressure: String) =
        api.submitFeedback(FeedbackBody(matchId, feeling, pressure))

    suspend fun reportUser(reportedUserId: String, reason: String, details: String?, matchId: String) =
        api.reportUser(ReportUserBody(reportedUserId, reason, details, matchId))

    suspend fun requestInfo(requestId: String): ApiHelpRequestInfo = api.requestInfo(requestId)

    suspend fun places(destinationId: String): List<ApiPlace> = api.places(destinationId)

    suspend fun uploadImage(matchId: String, jpegBytes: ByteArray): ApiMessage {
        val clientId = UUID.randomUUID().toString()
        val part = MultipartBody.Part.createFormData(
            "image", "photo.jpg",
            jpegBytes.toRequestBody("image/jpeg".toMediaType()),
        )
        return api.uploadImage(matchId, clientId.toRequestBody("text/plain".toMediaType()), part)
    }

    suspend fun uploadAudio(matchId: String, file: File): ApiMessage {
        val clientId = UUID.randomUUID().toString()
        val part = MultipartBody.Part.createFormData(
            "audio", "audio.m4a",
            file.readBytes().toRequestBody("audio/mp4".toMediaType()),
        )
        return api.uploadAudio(matchId, clientId.toRequestBody("text/plain".toMediaType()), part)
    }

    fun messageStream(matchId: String): Flow<SseEvent> =
        sse.events("messages/$matchId/stream")
}
