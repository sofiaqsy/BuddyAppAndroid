package com.buddy.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pestaña Comunidad (como iOS): título, trips de viajeros, Mis trips en Yo. Solo lectura. */
@RunWith(AndroidJUnit4::class)
class CommunityJourneysTest {
    @get:Rule(order = 0) val permissions: androidx.test.rule.GrantPermissionRule =
        androidx.test.rule.GrantPermissionRule.grant(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.POST_NOTIFICATIONS,
        )
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private fun tapTab(label: String) {
        rule.waitUntil(25_000) { rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText(label).onFirst().performClick()
    }

    @Test fun comunidad_showsTitleAndTrips() {
        tapTab("Comunidad")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Trips de viajeros cerca de ti").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(25_000) { rule.onAllNodesWithTag("storyCard").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Recientes").onFirst().assertExists()
        assertTrue(rule.onAllNodesWithText("Trips", substring = false).fetchSemanticsNodes().isEmpty())
    }

    /** Sin sesión, Yo pide iniciar sesión ("Mis trips" aparece ya con sesión:
     *  eso no se prueba aquí porque las pruebas no inician sesión). */
    @Test fun yo_signedOut_asksToSignIn() {
        tapTab("Yo")
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Continuar con Google").fetchSemanticsNodes().isNotEmpty() }
    }
}

/** Conexiones (como iOS): encabezado "Tus conexiones"; sin sesión, invitación a iniciar sesión. */
@RunWith(AndroidJUnit4::class)
class ConexionesJourneysTest {
    @get:Rule(order = 0) val permissions: androidx.test.rule.GrantPermissionRule =
        androidx.test.rule.GrantPermissionRule.grant(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.POST_NOTIFICATIONS,
        )
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @Test fun conexiones_headerAndSignedOutInvite() {
        rule.waitUntil(25_000) { rule.onAllNodesWithText("Conexiones").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Conexiones").onFirst().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Tus conexiones").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Las personas que te acompañaron en tus trips").onFirst().assertExists()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Las personas son parte del viaje").fetchSemanticsNodes().isNotEmpty() }
    }
}
