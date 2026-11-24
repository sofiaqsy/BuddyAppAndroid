package com.buddy.app.features.business

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** buddy-core: vínculo Buddy ↔ negocio de Apartalo, sus lugares y categorías (iOS APIClient). */
interface BusinessApi {
    @POST("integrations/fincas/link")
    suspend fun link(@Body body: LinkBody): LinkAnswer

    @GET("integrations/fincas/farms/{farmId}/spots")
    suspend fun farmSpots(@Path("farmId") farmId: String): FarmSpots

    @GET("integrations/fincas/farms/{farmId}/categories")
    suspend fun farmCategories(@Path("farmId") farmId: String): FarmCategories

    @retrofit2.http.PUT("integrations/fincas/farms/{farmId}/categories")
    suspend fun setFarmCategories(@Path("farmId") farmId: String, @Body body: FarmCategoriesBody): FarmCategories

    /** El dueño reclama este lugar como sede de su negocio. */
    @POST("integrations/fincas/spots/{spotId}/claim")
    suspend fun claim(@Path("spotId") spotId: String, @Body body: ClaimBody): kotlinx.serialization.json.JsonObject

    @retrofit2.http.DELETE("integrations/fincas/spots/{spotId}/claim/{farmId}")
    suspend fun release(@Path("spotId") spotId: String, @Path("farmId") farmId: String): kotlinx.serialization.json.JsonObject

    /** Foto del dueño para su lugar (la primera es la portada en Inicio). */
    @retrofit2.http.Multipart
    @POST("integrations/fincas/spots/{spotId}/photos")
    suspend fun uploadOwnerPhoto(@Path("spotId") spotId: String,
                                 @retrofit2.http.Part photo: okhttp3.MultipartBody.Part,
                                 @retrofit2.http.Part("farmId") farmId: okhttp3.RequestBody): kotlinx.serialization.json.JsonObject

    @retrofit2.http.DELETE("integrations/fincas/spots/{spotId}/photos/{photoId}")
    suspend fun deleteOwnerPhoto(@Path("spotId") spotId: String, @Path("photoId") photoId: String,
                                 @retrofit2.http.Query("farmId") farmId: String): kotlinx.serialization.json.JsonObject

    @POST("integrations/fincas/spots/{spotId}/photos/{photoId}/cover")
    suspend fun setOwnerCover(@Path("spotId") spotId: String, @Path("photoId") photoId: String,
                              @Body body: ClaimBody): kotlinx.serialization.json.JsonObject

    @GET("integrations/fincas/spots/{spotId}/photos")
    suspend fun ownerPhotos(@Path("spotId") spotId: String): OwnerPhotos

    @GET("places/categories")
    suspend fun catalog(): CategoryCatalog
}

@Serializable data class FarmCategoriesBody(val main: String, val secondary: List<String>)
@Serializable data class CategoryCatalog(val categories: List<CatalogCategory> = emptyList())
@Serializable data class CatalogCategory(val id: String, val name: String, val icon: String? = null)

@Serializable data class LinkBody(val firebaseToken: String)
@Serializable data class LinkAnswer(val links: List<LinkItem>? = null)
@Serializable data class LinkItem(val farmId: String? = null, val name: String? = null)
@Serializable data class FarmSpots(val spots: List<FarmSpot> = emptyList())
@Serializable data class FarmSpot(
    val id: String,
    val name: String,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    @SerialName("no_place") val noPlace: Boolean? = null,
)
@Serializable data class FarmCategories(val main: String? = null, val secondary: List<String> = emptyList())

@Serializable data class OwnerPhotos(val photos: List<OwnerPhoto> = emptyList())
@Serializable data class OwnerPhoto(val id: String, val url: String, val position: Int = 0)
@Serializable data class ClaimBody(val farmId: String)

/** Lugares del negocio activo: spotId -> farmId. El mapa los usa para el modo dueño. */
object OwnerSpots {
    val map = kotlinx.coroutines.flow.MutableStateFlow<Map<String, String>>(emptyMap())
}

/** Lo que puede hacer el dueño en el panel de su lugar (iOS modo edición). */
data class OwnerActions(
    val onAddProduct: () -> Unit,
    val onAddPhoto: () -> Unit,
    /** url -> id de las fotos que subió el dueño (las únicas que puede quitar). */
    val ownerPhotoIds: Map<String, String> = emptyMap(),
    /** id de la portada actual (la primera). */
    val coverId: String? = null,
    val onRemovePhoto: (String) -> Unit = {},
    val onMakeCover: (String) -> Unit = {},
)

/** Para usar BusinessApi fuera de un ViewModel (el panel del mapa). */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface BusinessEntryPoint { fun businessApi(): BusinessApi }

/** JPEG de máx. 1600 px a 0.8, como iOS. */
object Jpeg {
    fun from(ctx: android.content.Context, uri: android.net.Uri): ByteArray {
        val src = ctx.contentResolver.openInputStream(uri).use { android.graphics.BitmapFactory.decodeStream(it) }
            ?: throw FincasAuth.FincasError("No pudimos leer la foto.")
        val scale = minOf(1f, 1600f / maxOf(src.width, src.height))
        val bmp = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
        return java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }
}
