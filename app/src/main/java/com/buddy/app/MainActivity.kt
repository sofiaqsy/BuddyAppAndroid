package com.buddy.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

        setContent {
            BuddyTheme {
                BuddyRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    /** Tap en una notificación push → deja el tab destino para que BuddyRoot lo aplique. */
    private fun handleNotificationIntent(intent: Intent?) {
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
