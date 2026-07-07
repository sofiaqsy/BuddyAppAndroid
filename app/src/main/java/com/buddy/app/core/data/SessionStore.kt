package com.buddy.app.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore(name = "buddy_session")

/**
 * Persistencia de sesión — equivalente Android de UserDefaults+Keychain (iOS).
 * En Android el storage de la app ya está sandboxed y un wipe borra todo,
 * así que DataStore cubre ambos roles (no hay split UserDefaults/Keychain).
 *
 * Claves espejo de TravelerService.swift:
 * traveler.id / traveler.token / traveler.status ("guest"|"verified") / secret / device_id.
 */
@Singleton
class SessionStore @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val TravelerId = stringPreferencesKey("traveler_id")
        val Token      = stringPreferencesKey("traveler_token")
        val Status     = stringPreferencesKey("traveler_status")
        val Secret     = stringPreferencesKey("traveler_secret")
        val DeviceId   = stringPreferencesKey("device_id")
        val FullName   = stringPreferencesKey("full_name")
    }

    val session: Flow<TravelerSession?> = context.sessionDataStore.data.map { p ->
        val id = p[Keys.TravelerId] ?: return@map null
        TravelerSession(
            travelerId = id,
            token = p[Keys.Token],
            status = p[Keys.Status] ?: "guest",
            fullName = p[Keys.FullName],
        )
    }

    suspend fun current(): TravelerSession? = session.first()

    suspend fun secret(): String? = context.sessionDataStore.data.first()[Keys.Secret]

    /** Device id estable — espejo de identifierForVendor (UUID persistido). */
    suspend fun deviceId(): String {
        val existing = context.sessionDataStore.data.first()[Keys.DeviceId]
        if (existing != null) return existing
        val fresh = UUID.randomUUID().toString()
        context.sessionDataStore.edit { it[Keys.DeviceId] = fresh }
        return fresh
    }

    suspend fun saveGuest(travelerId: String, token: String, secret: String?) {
        context.sessionDataStore.edit {
            it[Keys.TravelerId] = travelerId
            it[Keys.Token] = token
            it[Keys.Status] = "guest"
            if (secret != null) it[Keys.Secret] = secret
        }
    }

    suspend fun saveToken(token: String, status: String? = null) {
        context.sessionDataStore.edit {
            it[Keys.Token] = token
            if (status != null) it[Keys.Status] = status
        }
    }

    /** Hydrate tras auth social/OTP — espejo de TravelerService.hydrate(). */
    suspend fun hydrate(travelerId: String, token: String, status: String, fullName: String? = null) {
        context.sessionDataStore.edit {
            it[Keys.TravelerId] = travelerId
            it[Keys.Token] = token
            it[Keys.Status] = status
            it.remove(Keys.Secret)   // verified users no usan secret (como iOS)
            if (fullName != null) it[Keys.FullName] = fullName
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { p ->
            p.remove(Keys.TravelerId); p.remove(Keys.Token)
            p.remove(Keys.Status); p.remove(Keys.Secret); p.remove(Keys.FullName)
            // device_id se conserva — igual que iOS conserva identifierForVendor
        }
    }

    /** Lectura síncrona del token para el interceptor OkHttp (thread de red). */
    fun tokenBlocking(): String? = runBlocking { current()?.token }
}

data class TravelerSession(
    val travelerId: String,
    val token: String?,
    val status: String,
    val fullName: String?,
) {
    val isVerified: Boolean get() = status == "verified"
}
