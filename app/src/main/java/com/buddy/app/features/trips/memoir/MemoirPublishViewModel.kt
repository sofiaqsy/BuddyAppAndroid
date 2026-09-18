package com.buddy.app.features.trips.memoir

import android.util.Log
import androidx.lifecycle.ViewModel
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.features.home.data.HomeApi
import com.buddy.app.features.home.data.PublishBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import javax.inject.Inject

/**
 * Publicación del Trip Book — espejo de publishJourney (APIClient.swift):
 * sube las portadas como multipart y marca journey (y trip padre) como
 * completed + is_public.
 */
@HiltViewModel
class MemoirPublishViewModel @Inject constructor(
    private val api: HomeApi,
) : ViewModel() {

    suspend fun publish(journey: ApiJourney, pages: List<CollagePage>, persistence: MemoirPersistence) {
        withContext(Dispatchers.IO) {
            try {
                val parts = pages.flatMapIndexed { index, page ->
                    val filename = page.thumbnailFileName ?: return@flatMapIndexed emptyList()
                    val file = persistence.thumbnailFile(filename, journey.id)
                    if (!file.exists()) return@flatMapIndexed emptyList()
                    listOf(
                        MultipartBody.Part.createFormData(
                            "page_$index", "page_$index.jpg",
                            file.asRequestBody("image/jpeg".toMediaType()),
                        ),
                        // El id de la página viaja junto a su archivo (espejo de
                        // iOS). buddy-core lo exige: con él actualiza esa fila en
                        // su sitio en vez de borrar y reinsertar el journey, que
                        // resucitaba fotos borradas. Sin él responde 400 y la
                        // foto nunca se publicaba desde Android.
                        MultipartBody.Part.createFormData("client_page_id_$index", page.id),
                    )
                }
                if (parts.isNotEmpty()) {
                    api.uploadJourneyPages(journey.id, parts)
                    Log.d(TAG, "uploaded ${parts.size / 2} page(s) for ${journey.id.take(8)}")
                }
                api.publishJourney(journey.id, PublishBody(status = "completed", isPublic = true))
                journey.tripId?.let {
                    runCatching { api.publishTrip(it, PublishBody(status = "completed", isPublic = true)) }
                }
                Log.d(TAG, "journey ${journey.id.take(8)} published")
            } catch (e: Exception) {
                Log.e(TAG, "publish failed", e)
            }
        }
    }

    companion object { private const val TAG = "MemoirPublish" }
}
