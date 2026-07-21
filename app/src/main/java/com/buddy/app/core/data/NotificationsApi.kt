package com.buddy.app.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST

interface NotificationsApi {

    @POST("notifications/token")
    suspend fun registerToken(@Body body: RegisterTokenRequest)

    @DELETE("notifications/token")
    suspend fun unregisterToken(@Body body: UnregisterTokenRequest)
}

@Serializable
data class RegisterTokenRequest(
    val token: String,
    val platform: String,
    val environment: String
)

@Serializable
data class UnregisterTokenRequest(
    val token: String
)
