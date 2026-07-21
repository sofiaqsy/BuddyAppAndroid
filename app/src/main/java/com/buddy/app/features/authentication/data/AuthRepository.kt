package com.buddy.app.features.authentication.data

import android.util.Log
import com.buddy.app.core.data.SessionStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Espejo del flujo social de AuthService.swift:
 * el identity token del proveedor va a POST /auth/social; el backend
 * resuelve/migra el traveler (guest → verified) y devuelve traveler_token.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthApi,
    private val store: SessionStore,
) {
    /**
     * @param provider "google" (Android usa Credential Manager; "apple" queda
     *        para continuación de cuenta vía web OAuth en una fase posterior).
     */
    suspend fun socialLogin(provider: String, identityToken: String, fullName: String?): String {
        // device_id → el backend crea la sesión de refresh device-bound y
        // devuelve el secret; con él la sesión verified persiste para siempre.
        val res = api.socialLogin(SocialRequest(provider, identityToken, fullName, store.deviceId()))
        Log.d(TAG, "social/$provider → traveler=${res.travelerId.take(8)} status=${res.status} secret=${res.secret != null}")
        store.hydrate(res.travelerId, res.travelerToken, res.status, fullName, secret = res.secret)
        return res.status
    }

    suspend fun completeProfile(fullName: String) {
        api.completeProfile(ProfileRequest(fullName))
        // Preservar el secret de refresh — hydrate sin él lo borraría y la
        // sesión moriría al expirar el JWT de 15 min.
        store.hydrate(
            travelerId = requireNotNull(store.current()).travelerId,
            token = requireNotNull(store.current()?.token),
            status = "verified",
            fullName = fullName,
            secret = store.secret(),
        )
    }

    suspend fun signOut() = store.clear()

    companion object { private const val TAG = "AuthRepo" }
}
