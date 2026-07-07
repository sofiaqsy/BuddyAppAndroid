package com.buddy.app.features.authentication

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.app.core.data.TravelerSession
import com.buddy.app.features.authentication.data.AuthRepository
import com.buddy.app.features.authentication.data.TravelerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
) : ViewModel() {

    val session: StateFlow<TravelerSession?> = travelerRepo.session
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
            authRepo.signOut()
            travelerRepo.ensureSession()   // vuelve a guest silencioso, como iOS
        }
    }

    companion object { private const val TAG = "SessionVM" }
}
