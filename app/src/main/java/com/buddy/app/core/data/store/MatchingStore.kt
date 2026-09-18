package com.buddy.app.core.data.store

import com.buddy.app.core.data.SessionStore
import com.buddy.app.features.matching.data.ApiMatch
import com.buddy.app.features.matching.data.MatchingApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dueño único de /matching/matches.
 *
 * Tres segundos y no ocho como los journeys: esto es el emparejamiento con un
 * buddy, y un match SÍ cambia mientras el usuario mira. Quien espera un buddy
 * usa refresh(), nunca load(): un sondeo que devuelve el snapshot no es un
 * sondeo.
 *
 * El store trae datos y nada más. Qué match vale, cuándo se pasa al chat,
 * cuándo se cancela la espera — eso sigue en quien lleva el flujo.
 */
@Singleton
class MatchingStore @Inject constructor(api: MatchingApi, sessionStore: SessionStore) {
    private val shared = SharedFetch("matches", 3_000, sessionStore) { api.matches() }

    suspend fun load(trigger: String): List<ApiMatch> = shared.load(trigger)
    suspend fun refresh(trigger: String): List<ApiMatch> = shared.refresh(trigger)
}
