package com.buddy.app.core.data

import android.content.Context
import android.util.Log
import com.buddy.app.BuildConfig
import com.buddy.app.features.authentication.data.TravelerRepository
import com.buddy.app.services.BuddyFirebaseMessagingService
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles registering / unregistering the FCM token with buddy-core.
 * Mirrors iOS PushService.swift.
 */
@Singleton
class PushService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationsApi: NotificationsApi,
    private val travelerRepository: TravelerRepository,
) {
    suspend fun registerToken() {
        // El JWT dura 15 min: al abrir la app suele estar vencido y el registro
        // daría 401. Garantizar sesión fresca antes de llamar al backend.
        try {
            travelerRepository.ensureSession()
        } catch (e: Exception) {
            Log.e(TAG, "No session available, skipping push registration", e)
            return
        }
        // Check if we have a stored token
        val storedToken = BuddyFirebaseMessagingService.getStoredToken(context)
        if (!storedToken.isNullOrEmpty()) {
            registerTokenWithBackend(storedToken)
            return
        }

        // Request fresh token from Firebase
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "Got FCM token: $token")
            registerTokenWithBackend(token)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get FCM token", e)
        }
    }

    private suspend fun registerTokenWithBackend(token: String) {
        try {
            val request = RegisterTokenRequest(
                token = token,
                platform = "android",
                environment = if (BuildConfig.DEBUG) "sandbox" else "production"
            )
            notificationsApi.registerToken(request)
            Log.d(TAG, "Token registered ✓")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register token: ${e.message}", e)
        }
    }

    suspend fun unregisterToken() {
        val storedToken = BuddyFirebaseMessagingService.getStoredToken(context) ?: return
        try {
            val request = UnregisterTokenRequest(token = storedToken)
            notificationsApi.unregisterToken(request)
            Log.d(TAG, "Token unregistered ✓")

            // Clear from storage
            val prefs = context.getSharedPreferences("buddy_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove("fcm_token").apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister token: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "[PushService]"
    }
}
