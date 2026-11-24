package com.buddy.app.features.community

import com.buddy.app.core.data.model.ApiDestinationRef
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.core.data.model.ApiUserRef
import org.junit.Assert.assertEquals
import org.junit.Test

/** Reglas de StoriesRowsView (iOS) para la pestaña Comunidad. */
class CommunityLogicTest {
    private fun j(id: String, dest: String?, user: String? = null, at: String? = null, destId: String? = null) = ApiJourney(
        id = id, status = "completed", createdAt = at,
        destination = dest?.let { ApiDestinationRef(id = destId, name = it, city = it) },
        users = user?.let { ApiUserRef(id = it, fullName = "ana pérez $it") },
    )

    private val stories = listOf(
        j("1", "Villa Rica", "u1", "2026-11-01", "d1"), j("2", "Lima", "u2", "2026-11-05"),
        j("3", "Villa Rica", "u1", "2026-11-03", "d1"), j("4", "Cusco", "u3", "2026-10-01"), j("5", null, null, null),
    )

    @Test fun groups_keepServerOrder() =
        assertEquals(listOf("Villa Rica", "Lima", "Cusco", "Otros lugares"), CommunityLogic.groups(stories).map { it.first })

    @Test fun bigGroups_onlyWithMoreThanOne() = assertEquals(listOf("Villa Rica"), CommunityLogic.bigGroups(stories).map { it.first })

    @Test fun singles_areTheRest() = assertEquals(listOf("2", "4", "5"), CommunityLogic.singles(stories).map { it.id })

    @Test fun recent_newestFirst_max10() = assertEquals(listOf("2", "3", "1", "4", "5"), CommunityLogic.recent(stories).map { it.id })

    @Test fun travelers_uniqueById() = assertEquals(listOf("u1", "u2", "u3"), CommunityLogic.travelers(stories).map { it.id })

    @Test fun destId_firstAvailable() = assertEquals("d1", CommunityLogic.destId(CommunityLogic.bigGroups(stories).first().second))

    @Test fun names() {
        assertEquals("Ana Pérez", CommunityLogic.capitalized("ana PÉREZ"))
        assertEquals("Ana", CommunityLogic.firstName("ana pérez"))
        assertEquals("Viajero", CommunityLogic.capitalized(null))
    }
}
