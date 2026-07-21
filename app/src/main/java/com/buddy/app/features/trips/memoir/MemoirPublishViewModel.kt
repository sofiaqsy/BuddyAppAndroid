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
                val parts = pages.mapIndexedNotNull { index, page ->
                    val filename = page.thumbnailFileName ?: return@mapIndexedNotNull null
                    val file = persistence.thumbnailFile(filename, journey.id)
                    if (!file.exists()) return@mapIndexedNotNull null
                    MultipartBody.Part.createFormData(
                        "page_$index", "page_$index.jpg",
                        file.asRequestBody("image/jpeg".toMediaType()),
                    )
                }
                if (parts.isNotEmpty()) {
                    api.uploadJourneyPages(journey.id, parts)
                    Log.d(TAG, "uploaded ${parts.size} page(s) for ${journey.id.take(8)}")
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
