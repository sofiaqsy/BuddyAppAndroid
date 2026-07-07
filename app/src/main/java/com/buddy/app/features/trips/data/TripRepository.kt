package com.buddy.app.features.trips.data

import android.util.Log
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.features.home.data.CreateJourneyBody
import com.buddy.app.features.home.data.HomeApi
import com.buddy.app.features.home.data.JourneyStatusBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Espejo de ensureActiveTrip (APIClient.swift): garantiza que exista un
 * journey activo para el destino — reusa uno active/planning o lo crea.
 * El matching necesita el journey_id para que el trip aparezca en "Tu trip".
 */
@Singleton
class TripRepository @Inject constructor(
    private val api: HomeApi,
) {
    suspend fun myJourneys(): List<ApiJourney> = api.myJourneys()

    suspend fun ensureActiveTrip(destinationId: String): ApiJourney {
        val journeys = runCatching { api.myJourneys() }.getOrDefault(emptyList())

        val existing = journeys.firstOrNull {
            (it.destination?.id ?: it.destinationId) == destinationId &&
                it.status in listOf("active", "planning")
        }
        if (existing != null) {
            if (existing.status != "active") {
                runCatching { api.updateJourneyStatus(existing.id, JourneyStatusBody("active")) }
            }
            Log.d(TAG, "ensureActiveTrip → reusa journey ${existing.id.take(8)}")
            return existing
        }

        val created = api.createJourney(CreateJourneyBody(destinationId = destinationId))
        runCatching { api.updateJourneyStatus(created.id, JourneyStatusBody("active")) }
        Log.d(TAG, "ensureActiveTrip → creado journey ${created.id.take(8)}")
        return created
    }

    suspend fun createTrip(destinationId: String? = null, placeId: String? = null, lat: Double? = null, lng: Double? = null): ApiJourney =
        api.createJourney(CreateJourneyBody(destinationId = destinationId, placeId = placeId, lat = lat, lng = lng))

    companion object { private const val TAG = "TripRepo" }
}
