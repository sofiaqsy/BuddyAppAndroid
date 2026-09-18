package com.buddy.app.features.matching.data

import com.buddy.app.core.network.SseClient
import com.buddy.app.core.network.SseEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * El backend impone UNA solicitud activa por traveler: si ya existe (p. ej.
 * quedó huérfana al minimizar/matar la app en plena búsqueda), POST
 * /matching/request responde 409 con el request_id existente — esta excepción
 * lo transporta para que la UI reanude ESA búsqueda en vez de fallar.
 */
class ActiveRequestExists(val requestId: String?) : Exception("active_request_exists")

/**
 * Matching — el SSE es la fuente de verdad primaria (evento "matched")
 * con /matching/status como recuperación, igual que ContactarBuddyView (iOS).
 */
@Singleton
class MatchingRepository @Inject constructor(
    private val api: MatchingApi,
    private val sse: SseClient,
    private val matchingStore: com.buddy.app.core.data.store.MatchingStore,
) {
    /**
     * Mi solicitud abierta, o null si no tengo ninguna.
     *
     * El endpoint responde 200 con el cuerpo literal `null`, que kotlinx no
     * puede decodificar contra un tipo no nulo (ver MatchingApi.myRequestRaw).
     * Se lee el texto y se decide aquí: "null" o vacío es una respuesta
     * legítima, no un fallo.
     */
    suspend fun myRequest(): ApiHelpRequest? {
        val resp = api.myRequestRaw()
        if (!resp.isSuccessful) throw retrofit2.HttpException(resp)
        val texto = resp.body()?.string()?.trim().orEmpty()
        if (texto.isEmpty() || texto == "null") return null
        return Json { ignoreUnknownKeys = true }.decodeFromString(texto)
    }

    suspend fun createHelpRequest(destinationId: String?, category: String, description: String? = null, journeyId: String? = null, lat: Double? = null, lng: Double? = null): ApiHelpRequest =
        try {
            api.createHelpRequest(HelpRequestBody(destinationId, category, description, journeyId, lat = lat, lng = lng))
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 409) {
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                val requestId = runCatching {
                    Json.parseToJsonElement(body ?: "")
                        .jsonObject["request_id"]?.jsonPrimitive?.content
                }.getOrNull()
                throw ActiveRequestExists(requestId)
            }
            throw e
        }

    suspend fun cancelRequest(requestId: String) = api.cancelRequest(requestId)

    suspend fun status(requestId: String): ApiMatchingStatus = api.matchingStatus(requestId)

    /** Para pantallas que MUESTRAN matches: pasa por el store (ventana de 3 s
     *  y una sola petición en vuelo). */
    suspend fun matches(trigger: String = "repo"): List<ApiMatch> = matchingStore.load(trigger)

    /** Para quien ESPERA un buddy: siempre al servidor. */
    suspend fun refreshMatches(trigger: String): List<ApiMatch> = matchingStore.refresh(trigger)

    suspend fun myOffers(): List<ApiBuddyOffer> = api.myOffers()

    /**
     * "Oportunidades para ayudar" — solicitudes dentro de la cobertura del
     * buddy, incluida su propia oferta oficial (isPriorityForMe = true) y las
     * que aún están en la ventana de exclusividad de otro buddy
     * (isCommunityUnlocked = false).
     */
    suspend fun availableHelp(): List<ApiHelpRequest> = api.requestsForBuddy()

    suspend fun acceptOffer(requestId: String): ApiMatch = api.acceptRequest(AcceptBody(requestId))

    suspend fun declineOffer(requestId: String) = api.declineOffer(DeclineBody(requestId))

    /** Stream del estado de una solicitud — emite "matched" cuando hay buddy. */
    fun requestStream(requestId: String): Flow<SseEvent> =
        sse.events("matching/request/$requestId/stream")
}
