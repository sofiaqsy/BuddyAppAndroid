package com.buddy.app.core.data.store

import com.buddy.app.core.data.SessionStore
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.features.home.data.HomeApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dueño único de /travelers/me/journeys — Home y Trips lo pedían cada uno por
 * su cuenta. Ventana de 8 s, igual que iOS.
 */
@Singleton
class JourneysStore @Inject constructor(api: HomeApi, sessionStore: SessionStore) {
    private val shared = SharedFetch("journeys", 8_000, sessionStore) { api.myJourneys() }

    suspend fun load(trigger: String): List<ApiJourney> = shared.load(trigger)
    suspend fun refresh(trigger: String): List<ApiJourney> = shared.refresh(trigger)
}
