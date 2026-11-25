package com.buddy.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import com.buddy.app.core.data.PushService
import com.buddy.app.core.designsystem.BuddyTheme
import com.buddy.app.navigation.AppTab
import com.buddy.app.navigation.BuddyRoot
import com.buddy.app.navigation.PendingTabNavigation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var pushService: PushService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_CODE_NOTIFICATION)
        }

        // Register push token
        GlobalScope.launch(Dispatchers.Main) {
            pushService.registerToken()
        }

        handleNotificationIntent(intent)
        com.buddy.app.features.business.JoinLinks.handle(intent?.data)
        com.buddy.app.features.business.BusinessStore.load(this)

        setContent {
            BuddyTheme {
                // Tocar fuera de un campo de texto cierra el teclado, en toda la app
                // (Keyla, 24 Nov 2026). Se mira el toque al final: si un botón, una
                // lista o un campo ya lo usaron, no se hace nada.
                val focus = androidx.compose.ui.platform.LocalFocusManager.current
                val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.pointerInput(Unit) {
                        // Toque libre o deslizar hacia abajo (Keyla, 24 Nov 2026) cierran el teclado.
                        val swipe = 48.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                            var moved = false
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Final)
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                val dx = ch.position.x - down.position.x
                                val dy = ch.position.y - down.position.y
                                if (kotlin.math.abs(dx) > 10f || kotlin.math.abs(dy) > 10f) moved = true
                                if (dy > swipe && dy > kotlin.math.abs(dx)) { focus.clearFocus(); keyboard?.hide(); break }
                                if (!ch.pressed) {
                                    // Toque sin mover que nadie usó (no fue un botón ni un campo).
                                    if (!moved && !ch.isConsumed) { focus.clearFocus(); keyboard?.hide() }
                                    break
                                }
                            }
                        }
                    }
                ) {
                    BuddyRoot()
                    // El visor de fotos, encima de todo y en la misma ventana.
                    com.buddy.app.core.designsystem.components.PhotoOverlayHost()
                    // Enlace de invitación a un negocio: encima de todo.
                    val joinCode by com.buddy.app.features.business.JoinLinks.pending
                        .collectAsState()
                    joinCode?.let { code ->
                        com.buddy.app.features.business.JoinBusinessScreen(code) {
                            com.buddy.app.features.business.JoinLinks.pending.value = null
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
        com.buddy.app.features.business.JoinLinks.handle(intent.data)
    }

    /** Tap en una notificación push → deja el tab destino para que BuddyRoot lo aplique. */
    private fun handleNotificationIntent(intent: Intent?) {
        // Pregunta o respuesta de un negocio: abrir directo esa conversación. En
        // segundo plano el sistema pasa los datos tal cual ("type"); en primer
        // plano los pone el servicio ("notif_type").
        val bizType = intent?.getStringExtra("notif_type") ?: intent?.getStringExtra("type")
        val conversationId = intent?.getStringExtra("conversation_id")
        if ((bizType == "business_answer" || bizType == "business_question") && conversationId != null) {
            // Pregunta a uno de mis negocios: activarlo para responder como él (iOS).
            val farmId = intent?.getStringExtra("farm_id")
            if (bizType == "business_question" && farmId != null) {
                com.buddy.app.features.business.BusinessStore.load(this)
                if (com.buddy.app.features.business.BusinessStore.businesses.value.any { it.farmId == farmId })
                    com.buddy.app.features.business.BusinessStore.activate(this, farmId)
            }
            com.buddy.app.features.businesschat.BizChatLinks.pending.value = conversationId
            PendingTabNavigation.request(AppTab.Conexiones)
            return
        }
        val type = intent?.getStringExtra("notif_type")
        Log.d(TAG, "handleNotificationIntent: notif_type=$type")
        when (type) {
            "buddy_approved" -> {
                Log.d(TAG, "handleNotificationIntent: requesting tab Conexiones")
                PendingTabNavigation.request(AppTab.Conexiones)
            }
        }
    }

    companion object {
        private const val TAG = "[MainActivity]"
        private const val REQUEST_CODE_NOTIFICATION = 100
    }
}
