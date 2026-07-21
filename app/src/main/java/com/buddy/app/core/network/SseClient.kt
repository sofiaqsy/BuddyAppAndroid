package com.buddy.app.core.network

import android.util.Log
import com.buddy.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Evento SSE mínimo: nombre del evento (si viene) + data cruda. */
data class SseEvent(val event: String?, val data: String)

/**
 * Cliente SSE — espejo del patrón de iOS (URLSession.bytes + reconexión con
 * backoff exponencial). buddy-core emite text/event-stream en
 * /stream, /matching/request/:id/stream y /messages/:matchId/stream.
 */
@Singleton
class SseClient @Inject constructor(
    private val baseClient: OkHttpClient,
) {
    // Sin read timeout: el stream queda abierto indefinidamente (como timeoutInterval=300 en iOS).
    private val streamClient: OkHttpClient by lazy {
        baseClient.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()
    }

    /**
     * Conecta a `path` (relativo a la base) y emite eventos. Reconecta solo
     * con backoff 1s→2s→4s… máx 30s, reiniciando tras conexión exitosa.
     * El AuthInterceptor del cliente base adjunta el Bearer automáticamente.
     */
    fun events(path: String): Flow<SseEvent> = callbackFlow {
        val job = launch {
            var attempt = 0
            while (isActive) {
                try {
                    val request = Request.Builder()
                        .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/" + path.trimStart('/'))
                        .header("Accept", "text/event-stream")
                        .build()
                    streamClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw java.io.IOException("SSE HTTP ${response.code}")
                        attempt = 0
                        Log.d(TAG, "SSE $path connected (proto=${response.protocol})")
                        val source = response.body!!.source()
                        var eventName: String? = null
                        while (isActive) {
                            val line = source.readUtf8Line() ?: break
                            if (line.startsWith(":")) Log.d(TAG, "SSE $path heartbeat")
                            when {
                                line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                                line.startsWith("data:") -> {
                                    trySend(SseEvent(eventName, line.removePrefix("data:").trim()))
                                    eventName = null
                                }
                                line.isEmpty() -> eventName = null
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!isActive) break
                    Log.w(TAG, "SSE $path dropped: ${e.javaClass.simpleName}: ${e.message}")
                }
                if (!isActive) break
                attempt++
                delay(minOf(30_000L, 1000L shl minOf(attempt, 5)))
            }
        }
        awaitClose { job.cancel() }
    }.flowOn(Dispatchers.IO)
    // flowOn: execute() es bloqueante — en Main lanza NetworkOnMainThreadException
    // y el stream muere antes de conectar (los ViewModels colectan en Main).

    companion object { private const val TAG = "SseClient" }
}
