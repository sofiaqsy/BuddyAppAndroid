package com.buddy.app.core.data.store

import android.util.Log
import com.buddy.app.BuildConfig
import com.buddy.app.core.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Un dueño para un recurso del servidor que varias pantallas leen — la forma
 * común de JourneysStore y MatchingStore (espejo de los stores de iOS).
 *
 * Tres reglas, las mismas que en iOS:
 * - load() respeta una ventana de frescura: aparecer en pantalla no es motivo
 *   para ir al servidor.
 * - refresh() la salta a propósito: es el usuario (o quien espera un buddy)
 *   pidiendo datos nuevos.
 * - Una sola petición en vuelo: quien llega mientras otra corre se engancha a
 *   ella en vez de abrir una segunda.
 *
 * El snapshot va atado al traveler que lo pidió: si la sesión cambia (logout,
 * otra cuenta), el siguiente load() vuelve al servidor en vez de entregar los
 * datos de la cuenta anterior. Así no hace falta engancharse al logout.
 */
class SharedFetch<T>(
    private val name: String,
    private val freshnessMs: Long,
    private val sessionStore: SessionStore,
    private val fetcher: suspend () -> T,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private var value: T? = null
    private var fetchedAt = 0L
    private var fetchedFor: String? = null
    private var inFlight: Deferred<T>? = null

    suspend fun load(trigger: String): T {
        val owner = sessionStore.current()?.travelerId
        synchronized(lock) {
            val cached = value
            val age = System.currentTimeMillis() - fetchedAt
            if (cached != null && owner == fetchedFor && age < freshnessMs) {
                dlog("$trigger → reutilizo (${age}ms)")
                return cached
            }
        }
        return fetch(trigger, owner)
    }

    suspend fun refresh(trigger: String): T =
        fetch("$trigger/force", sessionStore.current()?.travelerId)

    private suspend fun fetch(trigger: String, owner: String?): T {
        val deferred = synchronized(lock) {
            inFlight?.takeIf { it.isActive }?.also {
                dlog("$trigger → ya hay una carga en vuelo, me engancho")
            } ?: scope.async { fetcher() }.also { nuevo ->
                inFlight = nuevo
                // Se suelta pase lo que pase —éxito, error o cancelación—: una
                // entrada viva tras un fallo dejaría a todos enganchados a una
                // carga muerta.
                nuevo.invokeOnCompletion { synchronized(lock) { if (inFlight === nuevo) inFlight = null } }
            }
        }
        val result = deferred.await()
        synchronized(lock) {
            value = result
            fetchedAt = System.currentTimeMillis()
            fetchedFor = owner
        }
        return result
    }

    private fun dlog(msg: String) {
        if (BuildConfig.DEBUG) Log.d("SharedFetch", "[$name] $msg")
    }
}
