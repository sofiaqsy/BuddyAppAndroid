package com.buddy.app.features.business

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cuenta de negocio en Apartalo (iOS FincasAuth, 24 Nov 2026): la misma cuenta
 * de Google entra a Firebase de Apartalo por REST (signInWithIdp), sin el SDK.
 * El refresh token se guarda en preferencias privadas de la app; el ID token
 * (1 h) solo en memoria.
 */
object FincasAuth {
    const val BASE = "https://apartalo-core-9d633cdb9e1a.herokuapp.com"
    private const val API_KEY = "AIzaSyB56rWRNjX2OfMg9P6OFtAaynBazX8N3tc"
    private const val PREFS = "fincas_auth"
    private const val KEY_REFRESH = "refresh"

    class FincasError(message: String) : Exception(message)

    data class Business(val businessId: String?, val farmId: String?, val name: String, val role: String?)
    data class JoinAnswer(val status: String, val businessName: String?)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val JSON = "application/json".toMediaType()

    @Volatile private var cachedIdToken: String? = null
    @Volatile private var cachedExpiry = 0L

    fun hasSavedSession(ctx: Context) = prefs(ctx).getString(KEY_REFRESH, null) != null
    fun signOut(ctx: Context) { prefs(ctx).edit().remove(KEY_REFRESH).apply(); cachedIdToken = null }
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Entra a Apartalo con el id_token de Google (Credential Manager). */
    suspend fun signInWithGoogle(ctx: Context, googleIdToken: String) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("postBody", "id_token=$googleIdToken&providerId=google.com")
            .put("requestUri", "http://localhost")
            .put("returnSecureToken", true)
            .put("returnIdpCredential", true)
        val req = Request.Builder()
            .url("https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=$API_KEY")
            .post(body.toString().toRequestBody(JSON)).build()
        http.newCall(req).execute().use { r ->
            val j = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrDefault(JSONObject())
            val id = j.optString("idToken"); val refresh = j.optString("refreshToken")
            if (!r.isSuccessful || id.isEmpty() || refresh.isEmpty()) {
                val msg = j.optJSONObject("error")?.optString("message") ?: "sin detalle"
                android.util.Log.w("FincasAuth", "signInWithIdp falló: $msg")
                throw FincasError("No pudimos iniciar sesión ($msg).")
            }
            save(ctx, id, refresh)
        }
    }

    private fun save(ctx: Context, idToken: String, refresh: String) {
        cachedIdToken = idToken
        cachedExpiry = System.currentTimeMillis() + 50 * 60 * 1000
        prefs(ctx).edit().putString(KEY_REFRESH, refresh).apply()
    }

    suspend fun validIdToken(ctx: Context): String = withContext(Dispatchers.IO) {
        cachedIdToken?.takeIf { System.currentTimeMillis() < cachedExpiry }?.let { return@withContext it }
        val refresh = prefs(ctx).getString(KEY_REFRESH, null) ?: throw FincasError("Vuelve a entrar con tu cuenta.")
        val req = Request.Builder()
            .url("https://securetoken.googleapis.com/v1/token?key=$API_KEY")
            .post("grant_type=refresh_token&refresh_token=$refresh"
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            val j = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrDefault(JSONObject())
            val id = j.optString("id_token"); val newRefresh = j.optString("refresh_token")
            if (!r.isSuccessful || id.isEmpty()) throw FincasError("Tu sesión venció. Vuelve a entrar.")
            save(ctx, id, newRefresh.ifEmpty { refresh })
            id
        }
    }

    private suspend fun post(ctx: Context, path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val token = validIdToken(ctx)
        val req = Request.Builder().url(BASE + path)
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON)).build()
        http.newCall(req).execute().use { r ->
            val j = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrDefault(JSONObject())
            if (r.code == 401) throw FincasError("Tu sesión venció. Vuelve a entrar con tu cuenta.")
            if (!r.isSuccessful) throw FincasError(j.optString("error").ifEmpty { "No pudimos completar esto (${r.code}). Intenta de nuevo." })
            j
        }
    }

    /** Negocios de esta persona en Apartalo (no crea nada). */
    suspend fun businesses(ctx: Context): List<Business> {
        val j = post(ctx, "/api/auth/session", JSONObject())
        val arr = j.optJSONArray("businesses") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val b = arr.getJSONObject(i)
            Business(b.optString("businessId").ifEmpty { null }, b.optString("farmId").ifEmpty { null },
                b.optString("name"), b.optString("role").ifEmpty { null })
        }
    }

    suspend fun join(ctx: Context, code: String): JoinAnswer {
        val j = post(ctx, "/api/auth/join", JSONObject().put("code", code))
        return JoinAnswer(j.optString("status"), j.optString("businessName").ifEmpty { null })
    }

    /** Productos del negocio, la versión del dueño (incluye ocultos y stock). */
    suspend fun products(ctx: Context, farmId: String): List<com.buddy.app.core.data.model.FincasProduct> = withContext(Dispatchers.IO) {
        val token = validIdToken(ctx)
        val req = Request.Builder().url("$BASE/api/v1/farms/$farmId/products?offset=0&limit=100")
            .header("Authorization", "Bearer $token").get().build()
        http.newCall(req).execute().use { r ->
            when (r.code) {
                200 -> {}
                401 -> throw FincasError("Tu sesión venció. Vuelve a entrar.")
                403 -> throw FincasError("No eres miembro de este negocio.")
                else -> throw FincasError("El servidor respondió ${r.code}. Intenta de nuevo.")
            }
            productJson.decodeFromString(ProductPage.serializer(), r.body?.string().orEmpty()).items
        }
    }

    @kotlinx.serialization.Serializable
    private data class ProductPage(val items: List<com.buddy.app.core.data.model.FincasProduct> = emptyList())
    private val productJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // ── Escritura de productos (iOS FincasAuth.write): Idempotency-Key en todas,
    // un reintento con la MISMA llave si el servidor dormía (503) o falló la red.
    suspend fun write(ctx: Context, method: String, path: String, body: JSONObject,
                      key: String = java.util.UUID.randomUUID().toString()): JSONObject = withContext(Dispatchers.IO) {
        val token = validIdToken(ctx)
        val rb = if (method == "DELETE" && body.length() == 0) null else body.toString().toRequestBody(JSON)
        val req = Request.Builder().url(BASE + path)
            .header("Authorization", "Bearer $token").header("Idempotency-Key", key)
            .method(method, rb ?: if (method == "DELETE") null else "{}".toRequestBody(JSON)).build()
        var last: Exception? = null
        repeat(2) { intento ->
            try {
                http.newCall(req).execute().use { r ->
                    val j = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrDefault(JSONObject())
                    if (r.code == 503 && intento == 0) return@repeat
                    if (r.code !in 200..299) {
                        val msg = j.optString("error").ifEmpty { "El servidor respondió ${r.code}." }
                        throw FincasError(when (r.code) {
                            401 -> "Tu sesión venció. Vuelve a entrar."
                            403 -> "No eres miembro de este negocio."
                            429 -> "Demasiados cambios seguidos. Espera un minuto."
                            400 -> j.optString("field").takeIf { it.isNotEmpty() }?.let { "Revisa el campo $it: $msg" } ?: msg
                            else -> msg
                        })
                    }
                    return@withContext j
                }
            } catch (e: java.io.IOException) { last = e }
        }
        throw FincasError(last?.let { "No pudimos conectar. Intenta de nuevo." } ?: "No pudimos conectar. Intenta de nuevo.")
    }

    private fun products(farmId: String) = "/api/v1/farms/$farmId/products"

    suspend fun createProduct(ctx: Context, farmId: String, name: String, description: String, key: String): String =
        write(ctx, "POST", products(farmId), JSONObject().put("name", name).put("description", description), key)
            .optString("id").ifEmpty { throw FincasError("No recibimos el producto creado. Revisa la lista.") }

    suspend fun updateProduct(ctx: Context, farmId: String, productId: String, fields: JSONObject) {
        write(ctx, "PATCH", "${products(farmId)}/$productId", fields)
    }

    suspend fun createPresentation(ctx: Context, farmId: String, productId: String, fields: JSONObject, key: String): String? =
        write(ctx, "POST", "${products(farmId)}/$productId/presentations", fields, key).optString("id").ifEmpty { null }

    suspend fun updatePresentation(ctx: Context, farmId: String, productId: String, presId: String, fields: JSONObject) {
        write(ctx, "PATCH", "${products(farmId)}/$productId/presentations/$presId", fields)
    }

    /** Quita una foto de una presentación (fincas, como iOS). */
    suspend fun deletePresentationPhoto(ctx: Context, farmId: String, productId: String, presId: String, photoId: String) {
        write(ctx, "DELETE", "${products(farmId)}/$productId/presentations/$presId/photos/$photoId", JSONObject())
    }

    /** "Quitar producto": se archiva (nada se borra). */
    suspend fun archiveProduct(ctx: Context, farmId: String, productId: String) =
        updateProduct(ctx, farmId, productId, JSONObject().put("status", "archived"))

    /** Foto de una presentación: URL firmada, subir los bytes, registrar. */
    suspend fun uploadPresentationPhoto(ctx: Context, farmId: String, productId: String, presId: String, jpeg: ByteArray) {
        val base = "${products(farmId)}/$productId/presentations/$presId/photos"
        val signed = write(ctx, "POST", "$base/sign", JSONObject().put("mimeType", "image/jpeg"))
        val url = signed.optString("signedUrl"); val path = signed.optString("path")
        if (url.isEmpty() || path.isEmpty()) throw FincasError("No pudimos preparar la subida de la foto.")
        withContext(Dispatchers.IO) {
            val put = Request.Builder().url(url).put(jpeg.toRequestBody("image/jpeg".toMediaType())).build()
            http.newCall(put).execute().use { if (!it.isSuccessful) throw FincasError("No pudimos subir la foto (${it.code}).") }
        }
        write(ctx, "POST", base, JSONObject().put("path", path).put("mimeType", "image/jpeg"))
    }

    suspend fun createJoinCode(ctx: Context, businessId: String): String =
        post(ctx, "/api/auth/join-codes", JSONObject().put("businessId", businessId)).getString("code")
}

/** Código que llegó con el enlace buddyvr://unirse/<código>. */
object JoinLinks {
    val pending = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    fun handle(uri: android.net.Uri?): Boolean {
        if (uri?.scheme != "buddyvr" || uri.host != "unirse") return false
        val raw = (uri.lastPathSegment ?: "").uppercase().filter { it.isLetterOrDigit() || it == '-' }
        if (raw.count { it != '-' } != 8) return false
        pending.value = raw
        return true
    }
}
