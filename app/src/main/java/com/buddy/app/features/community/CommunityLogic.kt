package com.buddy.app.features.community

import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.core.data.model.ApiUserRef

/**
 * Filas de Comunidad, copiadas de StoriesRowsView (iOS, Keyla 19 Nov 2026):
 * 1. Viajeros (avatares), 2. Recientes, 3. una fila por destino en orden de
 * cercanía (el del servidor), 4. "Más trips" con los destinos de una sola historia.
 */
object CommunityLogic {
    fun destName(j: ApiJourney): String = j.destination?.name ?: j.title ?: "Otros lugares"

    fun destId(items: List<ApiJourney>): String? = items.firstNotNullOfOrNull { it.destination?.id ?: it.destinationId }

    fun recent(stories: List<ApiJourney>): List<ApiJourney> =
        stories.sortedByDescending { it.createdAt ?: "" }.take(10)

    /** (destino, historias) en orden de primera aparición. */
    fun groups(stories: List<ApiJourney>): List<Pair<String, List<ApiJourney>>> {
        val map = LinkedHashMap<String, MutableList<ApiJourney>>()
        stories.forEach { map.getOrPut(destName(it)) { mutableListOf() }.add(it) }
        return map.map { it.key to it.value }
    }

    fun bigGroups(stories: List<ApiJourney>) = groups(stories).filter { it.second.size > 1 }
    fun singles(stories: List<ApiJourney>) = groups(stories).filter { it.second.size == 1 }.flatMap { it.second }

    fun travelers(stories: List<ApiJourney>): List<ApiUserRef> {
        val seen = mutableSetOf<String>()
        return stories.mapNotNull { it.users }.filter { u -> u.id != null && seen.add(u.id!!) }
    }

    /** "ana pérez" -> "Ana Pérez" (iOS .capitalized). */
    fun capitalized(s: String?): String =
        (s ?: "Viajero").split(" ").filter { it.isNotEmpty() }
            .joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.titlecase() } }

    fun firstName(s: String?): String = capitalized(s).split(" ").first()
}
