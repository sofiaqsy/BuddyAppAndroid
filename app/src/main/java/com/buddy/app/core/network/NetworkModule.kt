package com.buddy.app.core.network

import com.buddy.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

/**
 * Cliente HTTP hacia buddy-core (backend existente — mismos endpoints que iOS).
 * El backend responde camelCase (normalizado server-side), igual que consume
 * APIClient.swift; ignoreUnknownKeys permite evolución del contrato sin romper.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(authInterceptor: AuthInterceptor): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): com.buddy.app.features.authentication.data.AuthApi =
        retrofit.create(com.buddy.app.features.authentication.data.AuthApi::class.java)

    @Provides
    @Singleton
    fun provideHomeApi(retrofit: Retrofit): com.buddy.app.features.home.data.HomeApi =
        retrofit.create(com.buddy.app.features.home.data.HomeApi::class.java)

    @Provides
    @Singleton
    fun provideMatchingApi(retrofit: Retrofit): com.buddy.app.features.matching.data.MatchingApi =
        retrofit.create(com.buddy.app.features.matching.data.MatchingApi::class.java)

    @Provides
    @Singleton
    fun provideProfileApi(retrofit: Retrofit): com.buddy.app.features.profile.data.ProfileApi =
        retrofit.create(com.buddy.app.features.profile.data.ProfileApi::class.java)

    @Provides
    @Singleton
    fun provideMapApi(retrofit: Retrofit): com.buddy.app.features.trips.map.MapApi =
        retrofit.create(com.buddy.app.features.trips.map.MapApi::class.java)

    @Provides
    @Singleton
    fun provideNotificationsApi(retrofit: Retrofit): com.buddy.app.core.data.NotificationsApi =
        retrofit.create(com.buddy.app.core.data.NotificationsApi::class.java)
}
