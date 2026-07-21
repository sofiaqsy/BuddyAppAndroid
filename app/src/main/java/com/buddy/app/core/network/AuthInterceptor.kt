package com.buddy.app.core.network

import com.buddy.app.core.data.SessionStore
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
 * Además, espejo del retry de iOS: el JWT dura 15 min, así que un 401 en
 * pleno uso significa token vencido → refresh silencioso con el secret y
 * UN reintento. Sin esto, cualquier llamada tras 15 min de sesión falla en
 * silencio (p. ej. cancelar una solicitud que el servidor nunca cancelaba).
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val store: SessionStore,
    // Lazy: rompe el ciclo AuthInterceptor → TravelerRepository → Retrofit → OkHttp
    private val travelerRepo: dagger.Lazy<TravelerRepository>,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = store.tokenBlocking()
        val request = if (token.isNullOrEmpty()) chain.request()
        else chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        val response = chain.proceed(request)

        val path = chain.request().url.encodedPath
        val isAuthPath = path.contains("/travelers/init") ||
            path.contains("/travelers/refresh") || path.contains("/auth/")
        if (response.code == 401 && !isAuthPath) {
            val fresh = runBlocking {
                runCatching { travelerRepo.get().refreshNow() }.getOrNull()
            }
            if (!fresh.isNullOrEmpty()) {
                response.close()
                return chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $fresh")
                        .build(),
                )
            }
        }
        return response
    }
}
