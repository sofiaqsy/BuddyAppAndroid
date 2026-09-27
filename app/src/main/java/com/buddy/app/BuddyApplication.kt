package com.buddy.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

@HiltAndroidApp
class BuddyApplication : Application() {

    // Hilt inyecta los campos de Application antes de que corra onCreate() —
    // es el MISMO OkHttpClient que usa Retrofit, así que la conexión que
    // abre precalentar() se reutiliza en la primera petición real.
    @Inject lateinit var httpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        precalentar()
    }

    /**
     * Abre DNS + TCP + TLS hacia buddy-core antes de que haga falta. Espejo
     * de APIClient.precalentar() (iOS): desde España cada conexión nueva
     * cuesta ~1-2 s de handshakes, y eso se paga completo en la primera
     * petición del Home si nadie lo adelanta.
     */
    private fun precalentar() {
        val raiz = BuildConfig.API_BASE_URL.toHttpUrlOrNull() ?: return
        val health = raiz.newBuilder().encodedPath("/health").build()
        val request = Request.Builder().url(health).head().build()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { httpClient.newCall(request).execute().close() }
                .onFailure { Log.d(TAG, "precalentar: sin respuesta útil (${it.message}) — no importa, era solo el handshake") }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "buddy_notifications",
                "Buddy Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications from Buddy (help requests, messages, etc)"
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object { private const val TAG = "BuddyApplication" }
}
