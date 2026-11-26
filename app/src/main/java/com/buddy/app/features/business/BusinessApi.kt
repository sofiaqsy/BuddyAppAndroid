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

    // Perfil del negocio y horario por sede (servidor, 26 Nov 2026, igual que iOS).
    @GET("integrations/fincas/farms/{farmId}/profile")
    suspend fun farmProfile(@Path("farmId") farmId: String): FarmProfile

    @retrofit2.http.PUT("integrations/fincas/farms/{farmId}/profile")
    suspend fun saveFarmProfile(@Path("farmId") farmId: String, @Body body: FarmProfileBody): kotlinx.serialization.json.JsonObject

    @retrofit2.http.PUT("integrations/fincas/spots/{spotId}/hours")
    suspend fun saveSpotHours(@Path("spotId") spotId: String, @Body body: SpotHoursBody): kotlinx.serialization.json.JsonObject

    /** Público: descripción y horario. Nunca trae el WhatsApp. */
    @GET("integrations/fincas/spots/{spotId}/info")
    suspend fun spotInfo(@Path("spotId") spotId: String): SpotInfo
}

/** 0 = lunes … 6 = domingo; minutos desde medianoche (igual que iOS DayHours). */
@Serializable data class DayHours(val day: Int, val open: Boolean, val from: Int, val to: Int) {
    companion object {
        val names = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        fun defaults() = (0..6).map { DayHours(it, it < 6, 8 * 60, 18 * 60) }
    }
}
@Serializable data class FarmProfile(val about: String? = null, val contact: String? = null, val hours: Map<String, List<DayHours>>? = null)
@Serializable data class FarmProfileBody(val about: String?, val contact: String?)
@Serializable data class SpotHoursBody(val farmId: String, val hours: List<DayHours>)
@Serializable data class SpotInfo(val about: String? = null, val hours: List<DayHours>? = null)

/** Textos del horario, iguales a iOS HoursSummary. */
object HoursSummary {
    private val short = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    fun time(minutes: Int): String {
        val h = minutes / 60; val m = minutes % 60
        val suffix = if (h < 12) "a. m." else "p. m."
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(h12, m, suffix)
    }
    fun text(days: List<DayHours>): String {
        val open = days.sortedBy { it.day }.filter { it.open }
        if (open.isEmpty()) return "Cerrado todos los días"
        val groups = mutableListOf<Triple<Int, Int, DayHours>>()
        for (d in open) {
            val last = groups.lastOrNull()
            if (last != null && last.second == d.day - 1 && last.third.from == d.from && last.third.to == d.to)
                groups[groups.size - 1] = Triple(last.first, d.day, last.third)
            else groups.add(Triple(d.day, d.day, d))
        }
        if (open.size == 7 && groups.size == 1) return "Todos los días · ${time(groups[0].third.from)} – ${time(groups[0].third.to)}"
        return groups.joinToString(" · ") { g ->
            val ds = if (g.first == g.second) short[g.first] else "${short[g.first]}–${short[g.second]}"
            "$ds · ${time(g.third.from)} – ${time(g.third.to)}"
        }
    }
    private val lima = java.util.TimeZone.getTimeZone("America/Lima")
    /** Hoy, 0 = lunes, hora de Perú. */
    fun today(): Int = (java.util.Calendar.getInstance(lima).get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
    private fun nowMinutes(): Int = java.util.Calendar.getInstance(lima).let { it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE) }
    fun isOpenNow(h: List<DayHours>): Boolean {
        val d = h.firstOrNull { it.day == today() } ?: return false
        return d.open && nowMinutes() in d.from until d.to
    }
    fun nowText(h: List<DayHours>): String {
        val t = today(); val now = nowMinutes()
        h.firstOrNull { it.day == t }?.takeIf { it.open }?.let { d ->
            if (now < d.from) return "Cerrado ahora · abre ${time(d.from)}"
            if (now < d.to) return "Abierto ahora · cierra ${time(d.to)}"
        }
        for (i in 1..7) {
            val day = (t + i) % 7
            h.firstOrNull { it.day == day && it.open }?.let { d ->
                return "Cerrado ahora · abre ${if (i == 1) "mañana" else DayHours.names[day].lowercase()} ${time(d.from)}"
            }
        }
        return "Cerrado"
    }
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
