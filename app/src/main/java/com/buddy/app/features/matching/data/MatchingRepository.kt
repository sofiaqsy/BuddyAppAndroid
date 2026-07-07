package com.buddy.app.features.matching.data

import com.buddy.app.core.network.SseClient
import com.buddy.app.core.network.SseEvent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Matching — el SSE es la fuente de verdad primaria (evento "matched")
 * con /matching/status como recuperación, igual que ContactarBuddyView (iOS).
 */
@Singleton
class MatchingRepository @Inject constructor(
    private val api: MatchingApi,
    private val sse: SseClient,
) {
    suspend fun createHelpRequest(destinationId: String, category: String, description: String? = null, journeyId: String? = null): ApiHelpRequest =
        api.createHelpRequest(HelpRequestBody(destinationId, category, description, journeyId))

    suspend fun cancelRequest(requestId: String) = api.cancelRequest(requestId)

    suspend fun status(requestId: String): ApiMatchingStatus = api.matchingStatus(requestId)

    suspend fun matches(): List<ApiMatch> = api.matches()

    suspend fun myOffers(): List<ApiBuddyOffer> = api.myOffers()

    suspend fun acceptOffer(requestId: String): ApiMatch = api.acceptRequest(AcceptBody(requestId))

    suspend fun declineOffer(requestId: String) = api.declineOffer(DeclineBody(requestId))

    /** Stream del estado de una solicitud — emite "matched" cuando hay buddy. */
    fun requestStream(requestId: String): Flow<SseEvent> =
        sse.events("matching/request/$requestId/stream")
}
