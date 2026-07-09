package com.buddy.app.core.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType

/**
 * "Cómo llegar" — espejo de la función de TripDetailView (iOS):
 * abre el lugar en Google Maps o Waze para que el usuario pueda llegar.
 * Intenta la app nativa primero y cae al navegador si no está instalada.
 */
object ExternalNavigation {

    fun openGoogleMaps(context: Context, lat: Double, lng: Double, name: String) {
        Log.d(TAG, "googleMaps → place=$name lat=$lat lng=$lng")
        // Deep link de navegación nativa; fallback web universal
        val native = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng"))
            .setPackage("com.google.android.apps.maps")
        if (tryStart(context, native)) return
        openUrl(context, "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=driving")
    }

    fun openWaze(context: Context, lat: Double, lng: Double, name: String) {
        Log.d(TAG, "waze → place=$name lat=$lat lng=$lng")
        openUrl(context, "https://waze.com/ul?ll=$lat,$lng&navigate=yes")
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent)
        Log.d(TAG, "✅ abierto app nativa")
        true
    }.getOrDefault(false)

    private fun openUrl(context: Context, url: String) {
        Log.d(TAG, "URL: $url")
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onSuccess { Log.d(TAG, "✅ abierto") }
            .onFailure { Log.e(TAG, "❌ open falló", it) }
    }

    private const val TAG = "ComoLlegar"
}

/** Diálogo "Cómo llegar a X" — mismas opciones que iOS (sin Apple Maps). */
@Composable
fun ComoLlegarDialog(
    placeName: String,
    lat: Double,
    lng: Double,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BuddyColor.Surface,
        title = { Text("Cómo llegar a $placeName", style = BuddyType.Headline, color = BuddyColor.Ink) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                TextButton(onClick = { ExternalNavigation.openGoogleMaps(context, lat, lng, placeName); onDismiss() }) {
                    Text("Google Maps", style = BuddyType.FootnoteBold, color = BuddyColor.Brand)
                }
                TextButton(onClick = { ExternalNavigation.openWaze(context, lat, lng, placeName); onDismiss() }) {
                    Text("Waze", style = BuddyType.FootnoteBold, color = BuddyColor.Brand)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", style = BuddyType.FootnoteBold, color = BuddyColor.InkMuted)
            }
        },
    )
}
