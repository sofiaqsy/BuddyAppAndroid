package com.buddy.app.core.network

import com.buddy.app.core.data.SessionStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adjunta el traveler JWT como Bearer a cada request — el equivalente
 * centralizado de los setValue("Bearer …") repartidos por APIClient.swift.
 * /travelers/init y /refresh no lo necesitan pero lo toleran.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val store: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = store.tokenBlocking()
        val request = if (token.isNullOrEmpty()) chain.request()
        else chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}
