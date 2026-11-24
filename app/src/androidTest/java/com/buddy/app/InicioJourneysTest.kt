package com.buddy.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Recorridos del Inicio en pantalla (Keyla, 20 Nov 2026), como
 * BuddyAppUITests/MainJourneysUITests en iOS. Datos reales, solo lectura.
 * Independiente de la ubicación: usa el primer lugar que muestra Inicio.
 */
@RunWith(AndroidJUnit4::class)
class InicioJourneysTest {
    // Instalación limpia: sin esto el aviso de permiso de ubicación tapa la app.
    @get:Rule(order = 0) val permissions: androidx.test.rule.GrantPermissionRule =
        androidx.test.rule.GrantPermissionRule.grant(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.POST_NOTIFICATIONS,   // MainActivity lo pide al abrir
        )
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(tag: String, ms: Long = 25_000) =
        rule.waitUntil(ms) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test fun inicio_showsSearchSectionsAndPlaces() {
        waitFor("placeCard")
        rule.onAllNodesWithText("Explora por categoría").onFirst().assertExists()
        rule.onAllNodesWithText("Cerca de ti").onFirst().assertExists()
    }

    @Test fun search_findsFirstPlaceByName_thenClears() {
        waitFor("placeCard")
        val name = rule.onAllNodesWithTag("placeCard").fetchSemanticsNodes().first()
            .config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
            .map { it.text }.firstOrNull { !it.endsWith(" m") && !it.endsWith(" km") }   // la distancia va primero
            ?: error("La tarjeta no tiene nombre")
        rule.onNodeWithTag("searchField").performTextInput(name.uppercase())
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("placeCard").and(hasText(name, substring = true))).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Explora por categoría").fetchSemanticsNodes().let { assertTrue("Las secciones se ocultan al buscar", it.isEmpty()) }
        rule.onNodeWithTag("searchField").performTextClearance()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Explora por categoría").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun search_noResults_showsHelp() {
        waitFor("placeCard")
        rule.onNodeWithTag("searchField").performTextInput("zzqxw")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Sin resultados", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun inicio_neverNamesPartners() {
        waitFor("placeCard")
        listOf("Apartalo", "Apártalo", "fincas").forEach { w ->
            assertTrue("Inicio muestra $w", rule.onAllNodesWithText(w, substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        }
    }
}
