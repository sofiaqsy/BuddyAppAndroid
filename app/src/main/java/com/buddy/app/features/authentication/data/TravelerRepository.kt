package com.buddy.app.features.authentication.data

import android.util.Base64
import android.util.Log
import com.buddy.app.core.data.SessionStore
import com.buddy.app.core.data.TravelerSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Espejo de TravelerService.swift — sesión guest → verified contra buddy-core.
 *
 * ensureSession(): si no hay sesión crea guest via /travelers/init;
 * si hay pero el JWT expira en <5 min, refresca en silencio.
 * Guests refrescan con secret; verified via el flujo social (Fase 3+).
 */
@Singleton
class TravelerRepository @Inject constructor(
    private val api: AuthApi,
    private val store: SessionStore,
) {
    private val mutex = Mutex()

    val session: Flow<TravelerSession?> = store.session

    suspend fun ensureSession(): String = mutex.withLock {
        val current = store.current()
        if (current != null) return refreshIfNeeded(current)

        Log.d(TAG, "no session found → POST /travelers/init")
        return createGuestSession()
    }

    private suspend fun createGuestSession(): String {
        val deviceId = store.deviceId()
        val res = api.initTraveler(InitRequest(deviceId))
        store.saveGuest(res.travelerId, res.token, res.secret)
        Log.d(TAG, "guest created → ${res.travelerId.take(8)}")
        return res.token
    }

    private suspend fun refreshIfNeeded(session: TravelerSession): String {
        val token = session.token
        if (!token.isNullOrEmpty() && !jwtExpiresSoon(token)) return token
        return forceRefresh(session)
    }

    private suspend fun forceRefresh(session: TravelerSession): String {
        val secret = store.secret()
        if (secret == null) {
            // Verified sin secret: si el refresh social falla, la sesión expiró.
            Log.w(TAG, "verified refresh no implementado sin secret — clearing")
            store.clear()
            throw SessionExpiredException()
        }
        try {
            val res = api.refreshToken(RefreshRequest(session.travelerId, secret, store.deviceId()))
            store.saveToken(res.token, res.status)
            Log.d(TAG, "token refreshed → ${session.travelerId.take(8)}")
            return res.token
        } catch (e: HttpException) {
            if (e.code() == 401) {
                Log.w(TAG, "guest refresh 401 — clearing stale session")
                store.clear()
                throw SessionExpiredException()
            }
            throw e
        }
    }

    /** true si el JWT falta, es inválido o expira en <5 minutos (igual que iOS). */
    private fun jwtExpiresSoon(token: String): Boolean {
        val parts = token.split(".")
        if (parts.size != 3) return true
        return try {
            val payload = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val exp = Json.parseToJsonElement(String(payload)).jsonObject["exp"]?.jsonPrimitive?.long
                ?: return true
            (exp * 1000 - System.currentTimeMillis()) < 5 * 60 * 1000
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Fuerza el refresh del JWT (tras un 401 en vuelo) — devuelve el token
     * nuevo o null si la sesión ya no es recuperable.
     *
     * [failedToken] es el token con el que la petición recibió el 401. El mutex
     * solo ponía los refreshes EN FILA: con 4 peticiones en 401 a la vez, la
     * primera renovaba y las otras tres esperaban el lock y renovaban otra vez
     * cada una. Dentro del lock, si el token guardado ya no es el que falló y
     * sigue vigente, otra petición ya lo renovó: se reutiliza sin ir a la red.
     * Mismo arreglo que el RefreshCoalescer de iOS.
     */
    suspend fun refreshNow(failedToken: String? = null): String? = mutex.withLock {
        val current = store.current() ?: return null
        val actual = current.token
        if (!actual.isNullOrEmpty() && actual != failedToken && !jwtExpiresSoon(actual)) {
            Log.d(TAG, "refresh omitido — otra petición ya renovó el token")
            return actual
        }
        runCatching { forceRefresh(current) }.getOrNull()
    }

    /**
     * Avisa al servidor que este dispositivo cerró sesión — hay que llamarlo
     * ANTES de limpiar el store, porque el interceptor necesita el JWT vigente.
     *
     * Sin esto el logout era solo local: la cuenta quedaba con
     * `is_available = true` y el waterfall la seguía eligiendo como buddy
     * aunque no hubiera nadie dentro de la app.
     *
     * Best-effort: si falla, el logout local sigue adelante igual.
     */
    suspend fun logoutRemote(pushToken: String?) {
        try {
            api.logout(LogoutRequest(deviceId = store.deviceId(), pushToken = pushToken))
            Log.d(TAG, "logout server-side ✓")
        } catch (e: Exception) {
            Log.w(TAG, "logout server-side falló: ${e.message}")
        }
    }

    suspend fun clearSession() = store.clear()

    companion object { private const val TAG = "TravelerRepo" }
}

class SessionExpiredException : Exception("Sesión expirada. Recupérala con tu cuenta.")
