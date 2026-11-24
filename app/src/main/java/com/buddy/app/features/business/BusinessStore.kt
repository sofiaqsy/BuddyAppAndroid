package com.buddy.app.features.business

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Negocios de Apartalo vinculados en este teléfono y cuál está activo
 * (iOS AccountStore). Activo = la pestaña "Yo" pasa a ser "Negocio".
 */
object BusinessStore {
    private const val PREFS = "business_store"
    private val _businesses = MutableStateFlow<List<FincasAuth.Business>>(emptyList())
    val businesses: StateFlow<List<FincasAuth.Business>> = _businesses
    private val _activeFarmId = MutableStateFlow<String?>(null)
    val activeFarmId: StateFlow<String?> = _activeFarmId
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _businesses.value = runCatching {
            val arr = JSONArray(p.getString("list", "[]"))
            (0 until arr.length()).map { i -> arr.getJSONObject(i).let {
                FincasAuth.Business(it.optString("businessId").ifEmpty { null }, it.optString("farmId").ifEmpty { null },
                    it.optString("name"), it.optString("role").ifEmpty { null })
            } }
        }.getOrDefault(emptyList())
        _activeFarmId.value = p.getString("active", null)
    }

    private fun save(ctx: Context) {
        val arr = JSONArray()
        _businesses.value.forEach { b ->
            arr.put(JSONObject().put("businessId", b.businessId ?: "").put("farmId", b.farmId ?: "")
                .put("name", b.name).put("role", b.role ?: ""))
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("list", arr.toString()).putString("active", _activeFarmId.value).apply()
    }

    /** Trae del servidor los negocios aprobados de esta persona. */
    suspend fun sync(ctx: Context) {
        load(ctx)
        val list = FincasAuth.businesses(ctx).filter { it.farmId != null }
        _businesses.value = list
        if (_activeFarmId.value != null && list.none { it.farmId == _activeFarmId.value }) _activeFarmId.value = null
        save(ctx)
    }

    fun activate(ctx: Context, farmId: String?) { load(ctx); _activeFarmId.value = farmId; save(ctx) }

    /** Descripción y contacto: como en iOS, por ahora se guardan en este teléfono. */
    fun about(ctx: Context, farmId: String) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("about_$farmId", "").orEmpty()
    fun contact(ctx: Context, farmId: String) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("contact_$farmId", "").orEmpty()
    fun saveProfile(ctx: Context, farmId: String, about: String, contact: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("about_$farmId", about).putString("contact_$farmId", contact).apply()
    }

    /** La sede principal va primero (iOS "Hacer principal"), guardado en el teléfono. */
    fun mainSpot(ctx: Context, farmId: String) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("main_$farmId", null)
    fun setMainSpot(ctx: Context, farmId: String, spotId: String) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("main_$farmId", spotId).apply()

    fun clear(ctx: Context) { _businesses.value = emptyList(); _activeFarmId.value = null; save(ctx) }

    fun activateByName(ctx: Context, name: String) {
        _businesses.value.firstOrNull { it.name.equals(name, ignoreCase = true) }?.farmId?.let { activate(ctx, it) }
    }
}
