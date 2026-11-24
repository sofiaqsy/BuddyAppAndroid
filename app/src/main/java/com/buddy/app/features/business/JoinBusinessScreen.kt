package com.buddy.app.features.business

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.features.authentication.GoogleIdentityProvider
import kotlinx.coroutines.launch

/**
 * Se abre con el enlace de invitación (iOS JoinBusinessView, 24 Nov 2026):
 * entra con Google a la cuenta de negocio y se une al equipo.
 */
@Composable
fun JoinBusinessScreen(code: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf<JoinPhase>(JoinPhase.Checking) }
    BackHandler(onBack = onClose)

    suspend fun join() {
        phase = JoinPhase.Joining
        phase = runCatching {
            val a = FincasAuth.join(ctx, code)
            when (a.status) {
                "joined", "already_member" -> {
                    BusinessStore.sync(ctx)
                    a.businessName?.let { n -> BusinessStore.activateByName(ctx, n) }
                    if (a.status == "joined") JoinPhase.Joined(a.businessName ?: "el negocio") else JoinPhase.Already
                }
                else -> JoinPhase.Pending(a.businessName.orEmpty())
            }
        }.getOrElse { JoinPhase.Failed(it.message ?: "Intenta de nuevo.") }
    }

    LaunchedEffect(code) {
        if (FincasAuth.hasSavedSession(ctx)) join() else phase = JoinPhase.SignIn
    }

    Box(Modifier.fillMaxSize().background(BuddyColor.Canvas).systemBarsPadding()) {
        // Botón volver estándar de la app: círculo con flecha.
        Box(
            Modifier.padding(16.dp).size(44.dp).shadow(6.dp, CircleShape).clip(CircleShape)
                .background(BuddyColor.Surface).clickable(onClick = onClose)
                .semantics { contentDescription = "Atrás" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = BuddyColor.Brand, modifier = Modifier.size(20.dp)) }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(BuddyColor.PaleSage), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Group, null, tint = BuddyColor.Brand, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(20.dp))
            when (val p = phase) {
                JoinPhase.Checking, JoinPhase.Joining -> {
                    Title("Uniéndote al negocio"); Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(color = BuddyColor.Brand)
                }
                JoinPhase.SignIn -> {
                    Title("Te invitaron a un negocio")
                    Note("Entra con tu cuenta de Google para unirte al equipo.")
                    Spacer(Modifier.height(20.dp))
                    Primary("Continuar con Google", dark = false) {
                        scope.launch {
                            runCatching {
                                val cred = GoogleIdentityProvider().signIn(ctx)
                                FincasAuth.signInWithGoogle(ctx, cred.identityToken)
                            }.onSuccess { join() }
                             .onFailure { e ->
                                 // Cerrar la ventana de Google no es un error.
                                 phase = if (e is androidx.credentials.exceptions.GetCredentialCancellationException) JoinPhase.SignIn
                                         else JoinPhase.Failed(e.message ?: "No pudimos iniciar sesión.")
                             }
                        }
                    }
                }
                is JoinPhase.Joined -> {
                    Title("Ya eres parte de ${p.name}")
                    Note("Lo encuentras en la pestaña Negocio.")
                    Spacer(Modifier.height(20.dp)); Primary("Listo", onClick = onClose)
                }
                is JoinPhase.Pending -> {
                    Title("Solicitud enviada")
                    Note("La dueña o el dueño de ${p.name.ifEmpty { "el negocio" }} debe aprobarla. Te avisaremos cuando puedas entrar.")
                    Spacer(Modifier.height(20.dp)); Primary("Listo", onClick = onClose)
                }
                JoinPhase.Already -> {
                    Title("Ya eres parte de este negocio")
                    Spacer(Modifier.height(20.dp)); Primary("Listo", onClick = onClose)
                }
                is JoinPhase.Failed -> {
                    Title("No pudimos unirte"); Note(p.message)
                    Spacer(Modifier.height(20.dp)); Primary("Cerrar", onClick = onClose)
                }
            }
        }
    }
}

sealed interface JoinPhase {
    data object Checking : JoinPhase
    data object SignIn : JoinPhase
    data object Joining : JoinPhase
    data class Joined(val name: String) : JoinPhase
    data class Pending(val name: String) : JoinPhase
    data object Already : JoinPhase
    data class Failed(val message: String) : JoinPhase
}

@Composable private fun Title(t: String) =
    Text(t, style = BuddyType.Title2, color = BuddyColor.Brand, textAlign = TextAlign.Center)

@Composable private fun Note(t: String) {
    Spacer(Modifier.height(8.dp))
    Text(t, style = BuddyType.Subhead, color = BuddyColor.InkMuted, textAlign = TextAlign.Center)
}

@Composable private fun Primary(t: String, dark: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(CircleShape)
            .background(if (dark) BuddyColor.Brand else BuddyColor.Surface)
            .then(if (dark) Modifier else Modifier.border(1.dp, BuddyColor.Border, CircleShape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(t, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            color = if (dark) BuddyColor.InkInverse else BuddyColor.Ink)
    }
}
