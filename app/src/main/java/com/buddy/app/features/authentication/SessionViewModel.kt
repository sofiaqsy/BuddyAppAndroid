package com.buddy.app.features.authentication

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.app.core.data.TravelerSession
import com.buddy.app.features.authentication.data.AuthRepository
import com.buddy.app.features.authentication.data.TravelerRepository
import com.buddy.app.services.BuddyFirebaseMessagingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sesión a nivel app — espejo del arranque de BuddyAppApp (iOS):
 * ensureSession() al iniciar (guest silencioso), login Google opcional
 * para migrar guest → verified.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val travelerRepo: TravelerRepository,
    private val authRepo: AuthRepository,
    private val googleProvider: GoogleIdentityProvider,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val session: StateFlow<TravelerSession?> = travelerRepo.session
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Cuenta verificada cuya sesión expiró: hay que pedir login, no crear
     *  un guest. BuddyRoot muestra el aviso "Tu sesión expiró". */
    val needsReauth: StateFlow<Boolean> = travelerRepo.needsReauth
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { travelerRepo.ensureSession() }
                .onSuccess { Log.d(TAG, "session ready") }
                .onFailure { Log.e(TAG, "ensureSession failed", it) }
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            _isSigningIn.value = true
            _error.value = null
            try {
                val cred = googleProvider.signIn(activityContext)
                authRepo.socialLogin(cred.provider, cred.identityToken, cred.fullName)
            } catch (e: Exception) {
                Log.e(TAG, "google sign-in failed", e)
                _error.value = "No se pudo iniciar sesión. Inténtalo de nuevo."
            } finally {
                _isSigningIn.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            // Primero el servidor —mientras el JWT sigue vivo—: si no, la cuenta
            // queda con is_available = true y el waterfall la sigue eligiendo
            // como buddy sin nadie dentro de la app.
            val fcmToken = BuddyFirebaseMessagingService.getStoredToken(appContext)
            travelerRepo.logoutRemote(fcmToken)
            appContext.getSharedPreferences("buddy_prefs", Context.MODE_PRIVATE)
                .edit().remove("fcm_token").apply()

            authRepo.signOut()
            travelerRepo.ensureSession()   // vuelve a guest silencioso, como iOS
        }
    }

    companion object { private const val TAG = "SessionVM" }
}
