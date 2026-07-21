package com.buddy.app.services

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.buddy.app.MainActivity
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

class BuddyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        // Save token locally
        saveToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received from: ${message.from}")

        // Extract notification data
        val title = message.notification?.title ?: message.data["title"] ?: "Buddy"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val type = message.data["type"]
        val matchId = message.data["match_id"]

        Log.d(TAG, "Notification: title=$title, body=$body, type=$type")

        // Show notification
        showNotification(title, body, matchId, type)
    }

    private fun showNotification(title: String, body: String, matchId: String?, type: String?) {
        val notificationId = Random.nextInt(1000)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Set click intent to open MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!matchId.isNullOrEmpty()) {
                putExtra("match_id", matchId)
            }
            if (!type.isNullOrEmpty()) {
                putExtra("notif_type", type)
            }
        }
        Log.d(TAG, "showNotification: intent extras notif_type=${intent.getStringExtra("notif_type")} match_id=${intent.getStringExtra("match_id")}")
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(pendingIntent)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(notificationId, builder.build())
        }
    }

    private fun saveToken(token: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(FCM_TOKEN_KEY, token).apply()
        Log.d(TAG, "FCM token saved locally")
    }

    companion object {
        private const val TAG = "[FCM]"
        private const val CHANNEL_ID = "buddy_notifications"
        private const val PREFS_NAME = "buddy_prefs"
        private const val FCM_TOKEN_KEY = "fcm_token"

        fun getStoredToken(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(FCM_TOKEN_KEY, null)
        }
    }
}
