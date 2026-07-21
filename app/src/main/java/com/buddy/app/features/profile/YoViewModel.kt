package com.buddy.app.features.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.app.core.data.SessionStore
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.features.profile.data.ApiBuddyMe
import com.buddy.app.features.profile.data.ApiUser
import com.buddy.app.features.profile.data.ApiUserSticker
import com.buddy.app.features.profile.data.BioBody
import com.buddy.app.features.profile.data.ProfileApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Espejo del estado de YoView (iOS): perfil, bio, stickers, trips, buddy. */
@HiltViewModel
class YoViewModel @Inject constructor(
    private val api: ProfileApi,
    private val sessionStore: SessionStore,
) : ViewModel() {

    data class State(
        val isLoading: Boolean = true,
        val user: ApiUser? = null,
        val journeys: List<ApiJourney> = emptyList(),
        val stickers: List<ApiUserSticker> = emptyList(),
        val buddyMe: ApiBuddyMe? = null,
        val isSavingBio: Boolean = false,
        val bioSaveFailed: Boolean = false,
        val isBecomingBuddy: Boolean = false,
        val isDeletingAccount: Boolean = false,
        val isUploadingAvatar: Boolean = false,
        val avatarUploadFailed: Boolean = false,
    ) {
        /** "N trips · N stickers" — igual que metaLine (iOS). */
        val metaLine: String
            get() {
                val trips = if (journeys.size == 1) "1 trip" else "${journeys.size} trips"
                val st = when {
                    stickers.isEmpty() -> null
                    stickers.size == 1 -> "1 sticker"
                    else -> "${stickers.size} stickers"
                }
                return listOfNotNull(trips, st).joinToString(" · ")
            }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                val myId = sessionStore.current()?.travelerId ?: run {
                    _state.update { it.copy(isLoading = false) }; return@launch
                }
                coroutineScope {
                    // onFailure con log: un fallo silencioso aquí se ve como
                    // "el perfil no carga" y es imposible de diagnosticar.
                    val user = async { runCatching { api.me() }.onFailure { Log.e(TAG, "users/me failed", it) }.getOrNull() }
                    val trips = async { runCatching { api.trips(myId).items }.onFailure { Log.e(TAG, "trips failed", it) }.getOrDefault(emptyList()) }
                    val stickers = async { runCatching { api.stickers(myId) }.onFailure { Log.e(TAG, "stickers failed", it) }.getOrDefault(emptyList()) }
                    // buddy/me responde 403 para no verificados — se trata como "no buddy"
                    val buddy = async { runCatching { api.buddyMe() }.getOrNull() }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            user = user.await(),
                            journeys = trips.await(),
                            stickers = stickers.await(),
                            buddyMe = buddy.await(),
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "load failed", e)
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Foto de perfil — espejo de uploadAvatar (iOS): reescala a 400px,
     * JPEG 0.85, multipart "avatar" a POST /users/me/avatar y actualiza
     * el avatar en el estado con la URL devuelta.
     */
    fun uploadAvatar(bytes: ByteArray) {
        viewModelScope.launch {
            _state.update { it.copy(isUploadingAvatar = true, avatarUploadFailed = false) }
            try {
                val jpeg = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: throw IllegalArgumentException("Formato de imagen no soportado")
                    val small = com.buddy.app.features.trips.memoir.BitmapEffects
                        .limitedToMaxDimension(bmp, 400)
                    java.io.ByteArrayOutputStream()
                        .also { small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
                        .toByteArray()
                }
                val part = okhttp3.MultipartBody.Part.createFormData(
                    "avatar", "avatar.jpg",
                    jpeg.toRequestBody("image/jpeg".toMediaType()),
                )
                val resp = api.uploadAvatar(part)
                _state.update {
                    it.copy(
                        isUploadingAvatar = false,
                        user = it.user?.copy(avatarUrl = resp.avatarUrl),
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "uploadAvatar failed", e)
                _state.update { it.copy(isUploadingAvatar = false, avatarUploadFailed = true) }
            }
        }
    }

    fun dismissAvatarError() = _state.update { it.copy(avatarUploadFailed = false) }

    /**
     * Portadas para el visor de historia — espejo del .task de StoryViewerSheet
     * (iOS): usa page_thumbs si vienen en el journey; si no, las pide al server.
     */
    suspend fun storyThumbs(journey: ApiJourney): List<String> {
        journey.pageThumbs?.takeIf { it.isNotEmpty() }?.let { return it }
        return runCatching { api.journeyPages(journey.id).sortedBy { it.pageIndex }.map { it.thumbnailUrl } }
            .onFailure { Log.e(TAG, "journeyPages failed", it) }
            .getOrDefault(emptyList())
    }

    /**
     * Elimina una publicación del perfil — optimista: sale del grid al
     * instante; el backend la despublica (cancelled + is_public=false) y
     * desaparece también del feed de la comunidad.
     */
    fun deletePublication(journey: ApiJourney) {
        _state.update { s -> s.copy(journeys = s.journeys.filter { it.id != journey.id }) }
        viewModelScope.launch {
            runCatching { api.deleteJourney(journey.id) }
                .onFailure { Log.e(TAG, "deletePublication failed", it) }
        }
    }

    fun saveBio(bio: String, onDone: () -> Unit) {
        val myId = _state.value.user?.id ?: return
        _state.update { it.copy(isSavingBio = true, bioSaveFailed = false) }
        viewModelScope.launch {
            runCatching { api.updateBio(myId, BioBody(bio)) }
                .onSuccess {
                    _state.update { s -> s.copy(isSavingBio = false, user = s.user?.copy(bio = bio)) }
                    onDone()
                }
                .onFailure {
                    Log.e(TAG, "saveBio failed", it)
                    _state.update { s -> s.copy(isSavingBio = false, bioSaveFailed = true) }
                }
        }
    }

    fun dismissBioError() = _state.update { it.copy(bioSaveFailed = false) }

    /** Crea el perfil de buddy y refresca la sección — como becomeBuddy (iOS). */
    fun becomeBuddy() {
        if (_state.value.isBecomingBuddy) return
        _state.update { it.copy(isBecomingBuddy = true) }
        viewModelScope.launch {
            runCatching { api.becomeBuddy() }
                .onSuccess { result -> _state.update { it.copy(isBecomingBuddy = false, buddyMe = result) } }
                .onFailure {
                    Log.e(TAG, "becomeBuddy failed", it)
                    _state.update { s -> s.copy(isBecomingBuddy = false) }
                }
        }
    }

    /** Refleja cambios hechos en BuddyProfileScreen (disponibilidad/zonas/especialidades). */
    fun applyBuddyProfileUpdate(profile: com.buddy.app.features.profile.data.ApiBuddyMe.BuddyProfile) {
        _state.update { it.copy(buddyMe = it.buddyMe?.copy(profile = profile)) }
    }

    fun deleteAccount(onDone: () -> Unit) {
        if (_state.value.isDeletingAccount) return
        _state.update { it.copy(isDeletingAccount = true) }
        viewModelScope.launch {
            runCatching { api.deleteAccount() }
                .onSuccess { onDone() }
                .onFailure { Log.e(TAG, "deleteAccount failed", it) }
            _state.update { it.copy(isDeletingAccount = false) }
        }
    }

    companion object { private const val TAG = "YoVM" }
}
