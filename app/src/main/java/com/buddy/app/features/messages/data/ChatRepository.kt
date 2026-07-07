package com.buddy.app.features.messages.data

import com.buddy.app.core.network.SseClient
import com.buddy.app.core.network.SseEvent
import com.buddy.app.features.matching.data.ApiMessage
import com.buddy.app.features.matching.data.MatchingApi
import com.buddy.app.features.matching.data.SendMessageBody
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Chat de un match — historial + envío idempotente + SSE de nuevos mensajes. */
@Singleton
class ChatRepository @Inject constructor(
    private val api: MatchingApi,
    private val sse: SseClient,
) {
    suspend fun messages(matchId: String, limit: Int = 30): List<ApiMessage> =
        api.messages(matchId, limit)

    suspend fun send(matchId: String, content: String): ApiMessage =
        api.sendMessage(matchId, SendMessageBody(content, idempotencyKey = UUID.randomUUID().toString()))

    suspend fun markRead(matchId: String) = runCatching { api.markRead(matchId) }.let { }

    fun messageStream(matchId: String): Flow<SseEvent> =
        sse.events("messages/$matchId/stream")
}
