package com.buddy.app.features.authentication

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.buddy.app.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import javax.inject.Inject

/** Credencial unificada — espejo de IdentityCredential (iOS). */
data class IdentityCredential(
    val provider: String,        // "google" | "apple"
    val identityToken: String,
    val fullName: String?,
)

/**
 * Sign in with Google vía Credential Manager — la adaptación Android
 * del GoogleProvider de iOS. Mismo output: un id_token JWT que
 * POST /auth/social valida server-side (el backend no distingue plataforma).
 */
class GoogleIdentityProvider @Inject constructor() {

    suspend fun signIn(activityContext: Context): IdentityCredential {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val result = CredentialManager.create(activityContext)
            .getCredential(activityContext, request)

        val googleCred = GoogleIdTokenCredential.createFrom(result.credential.data)
        return IdentityCredential(
            provider = "google",
            identityToken = googleCred.idToken,
            fullName = googleCred.displayName,
        )
    }
}
