package com.buddy.app.core.network

import com.buddy.app.core.data.SessionStore
import com.buddy.app.core.data.store.HomeBootstrapStore
import com.buddy.app.features.authentication.data.TravelerRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adjunta el traveler JWT como Bearer a cada request — el equivalente
 * centralizado de los setValue("Bearer …") repartidos por APIClient.swift.
 *
 * Además:
 * - Refresh PROACTIVO (espejo de refreshTokenIfExpiring en iOS): si el JWT
 *   ya vence pronto, se renueva ANTES de mandar la petición, evitando la
 *   ráfaga de 401 simultáneos tras un rato con la app abierta.
 * - Refresh REACTIVO: un 401 en pleno uso significa que igual venció entre
 *   medio → refresh silencioso y UN reintento (red de seguridad).
 * - Invalida el cache de /home/bootstrap tras una escritura real, para que
 *   la siguiente lectura no sirva datos que la escritura acaba de cambiar.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val store: SessionStore,
    // Lazy: rompe el ciclo AuthInterceptor → TravelerRepository → Retrofit → OkHttp
    private val travelerRepo: dagger.Lazy<TravelerRepository>,
    // Lazy por el mismo motivo: HomeBootstrapStore → HomeApi → Retrofit → OkHttp
    private val homeBootstrap: dagger.Lazy<HomeBootstrapStore>,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val path = chain.request().url.encodedPath
        val isAuthPath = path.contains("/travelers/init") ||
            path.contains("/travelers/refresh") || path.contains("/auth/")

        if (!isAuthPath) {
            runCatching { runBlocking { travelerRepo.get().refreshTokenIfExpiring() } }
        }

        val token = store.tokenBlocking()
        val request = if (token.isNullOrEmpty()) chain.request()
        else chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        var response = chain.proceed(request)

        if (response.code == 401 && !isAuthPath) {
            val fresh = runBlocking {
                // El token que falló: si otra petición ya lo renovó, no se repite.
                runCatching { travelerRepo.get().refreshNow(failedToken = token) }.getOrNull()
            }
            if (!fresh.isNullOrEmpty()) {
                response.close()
                response = chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $fresh")
                        .build(),
                )
            }
        }

        if (request.method != "GET" && response.isSuccessful) {
            runCatching { homeBootstrap.get().invalidar(path) }
        }
        return response
    }
}
