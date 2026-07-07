package com.buddy.app.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.buddy.app.core.data.TravelerSession
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.core.designsystem.components.BuddyAvatar
import com.buddy.app.core.designsystem.components.BuddyCard
import com.buddy.app.core.designsystem.components.BuddyPrimaryButton
import com.buddy.app.core.designsystem.components.BuddyStatusBadge
import com.buddy.app.core.designsystem.components.BuddyTextButton
import com.buddy.app.features.authentication.SessionViewModel

/**
 * Versión mínima de YoView (iOS) — estado de sesión + login Google.
 * La pantalla completa (perfil buddy, stickers, journeys) llega con Fase 4.
 */
@Composable
fun YoScreen(modifier: Modifier = Modifier, viewModel: SessionViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsState()
    val isSigningIn by viewModel.isSigningIn.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxSize().background(BuddyColor.Canvas).padding(Spacing.edge),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.lg))
        Text("Yo", style = BuddyType.DisplayHero, color = BuddyColor.Ink)

        SessionCard(
            session = session,
            isSigningIn = isSigningIn,
            error = error,
            onGoogleSignIn = { viewModel.signInWithGoogle(context) },
            onSignOut = viewModel::signOut,
        )
    }
}

@Composable
private fun SessionCard(
    session: TravelerSession?,
    isSigningIn: Boolean,
    error: String?,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    BuddyCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            BuddyAvatar(imageUrl = null, name = session?.fullName)
            Text(
                text = session?.fullName ?: "Viajero",
                style = BuddyType.Title2,
                color = BuddyColor.Ink,
            )
            BuddyStatusBadge(
                text = if (session?.isVerified == true) "Verificado" else "Invitado",
                available = session?.isVerified == true,
            )
            if (session?.isVerified != true) {
                Text(
                    "Inicia sesión para guardar tus trips y conectar con buddies.",
                    style = BuddyType.Subhead,
                    color = BuddyColor.InkMuted,
                )
                BuddyPrimaryButton(
                    text = "Continuar con Google",
                    onClick = onGoogleSignIn,
                    isLoading = isSigningIn,
                )
            } else {
                BuddyTextButton("Cerrar sesión", onClick = onSignOut)
            }
            if (error != null) {
                Text(error, style = BuddyType.Caption1, color = BuddyColor.ErrorRed)
            }
        }
    }
}
