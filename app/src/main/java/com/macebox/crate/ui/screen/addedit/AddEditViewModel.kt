package com.macebox.crate.ui.screen.addedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.data.api.CrateApiService
import com.macebox.crate.data.api.apiCall
import com.macebox.crate.domain.model.Category
import com.macebox.crate.domain.model.MediaItem
import com.macebox.crate.domain.model.MediaItemDraft
import com.macebox.crate.domain.model.Status
import com.macebox.crate.domain.model.UserProfile
import com.macebox.crate.domain.repository.EnrichmentRepository
import com.macebox.crate.domain.repository.MediaRepository
import com.macebox.crate.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.Calendar
import javax.inject.Inject

private val CURRENT_YEAR: Int = Calendar.getInstance().get(Calendar.YEAR)
private const val MIN_YEAR = 1800

data class AddEditUiState(
    val isEditing: Boolean = false,
    val editingItemId: Long? = null,
    val itemUpdatedAt: String? = null,
    val initialLoading: Boolean = false,
    val isSaving: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val format: String = "",
    val year: String = "",
    val barcode: String = "",
    val label: String = "",
    val country: String = "",
    val notes: String = "",
    val discogsId: String? = null,
    val artworkPath: String? = null,
    val status: Status = Status.Owned,
    val category: Category = Category.Music,
    val autoEnrich: Boolean = false,
    val pendingArtwork: PendingArtwork? = null,
    /**
     * Cover URL supplied by an enrichment source (Discogs/TMDB/etc.). Shown
     * in the artwork preview before save; on save the backend caches the
     * image and the local /apps/crate/artwork/{id} URL takes over. Mirrors
     * `enrichPreviewUrl` in AddEditModal.vue.
     */
    val pendingArtworkUrl: String? = null,
    /** True when the user clicked Remove on existing artwork. */
    val removeArtwork: Boolean = false,
    val isLookingUpIsbn: Boolean = false,
    val errorMessage: String? = null,
    val savedItemId: Long? = null,
) {
    val yearError: Boolean
        get() = year.isNotBlank() && (year.toIntOrNull()?.let { it !in MIN_YEAR..CURRENT_YEAR } ?: true)

    val canSave: Boolean
        get() = title.isNotBlank() &&
            artist.isNotBlank() &&
            format.isNotBlank() &&
            !yearError &&
            !isSaving

    val hasArtworkPreview: Boolean
        get() = pendingArtwork != null ||
            !pendingArtworkUrl.isNullOrBlank() ||
            (isEditing && !removeArtwork && editingItemId != null)
}

data class PendingArtwork(
    val bytes: ByteArray,
    val mimeType: String,
)

const val SCAN_RESULT_KEY = "scan_result"

@HiltViewModel
class AddEditViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val mediaRepository: MediaRepository,
        private val settingsRepository: SettingsRepository,
        private val enrichmentRepository: EnrichmentRepository,
        private val api: CrateApiService,
    ) : ViewModel() {
        private val itemId: Long? = savedStateHandle.get<Long>("itemId")?.takeIf { it > 0L }
        private val initialCategory: Category =
            savedStateHandle
                .get<String>("category")
                ?.let { Category.fromApi(it) }
                ?: Category.Music

        private val _uiState =
            MutableStateFlow(
                AddEditUiState(
                    isEditing = itemId != null,
                    editingItemId = itemId,
                    category = initialCategory,
                    initialLoading = itemId != null,
                ),
            )
        val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

        init {
            loadProfileDefaults()
            if (itemId != null) loadExisting(itemId)
            applyInitialPrefill(savedStateHandle.get<String>("prefillJson"))
            observeScanResults()
        }

        private fun applyInitialPrefill(prefillJson: String?) {
            if (prefillJson.isNullOrBlank()) return
            runCatching { Json.decodeFromString<ExternalSearchResult>(prefillJson) }
                .getOrNull()
                ?.let(::applyExternalResult)
        }

        private fun observeScanResults() {
            viewModelScope.launch {
                savedStateHandle
                    .getStateFlow<String?>(SCAN_RESULT_KEY, null)
                    .filterNotNull()
                    .collect { json ->
                        runCatching { Json.decodeFromString<ExternalSearchResult>(json) }
                            .getOrNull()
                            ?.let(::applyExternalResult)
                        savedStateHandle[SCAN_RESULT_KEY] = null
                    }
            }
        }

        private fun loadProfileDefaults() {
            viewModelScope.launch {
                when (val result = settingsRepository.getMe()) {
                    is ApiResult.Success -> applyProfile(result.value)
                    else -> { /* Ignore — toggle simply stays off. */ }
                }
            }
        }

        private fun applyProfile(profile: UserProfile) {
            _uiState.update {
                if (it.isEditing) it else it.copy(autoEnrich = profile.autoEnrichOnClick)
            }
        }

        private fun loadExisting(id: Long) {
            viewModelScope.launch {
                when (val result = mediaRepository.refreshSingle(id)) {
                    is ApiResult.Success -> populate(result.value)
                    ApiResult.NetworkError ->
                        _uiState.update {
                            it.copy(initialLoading = false, errorMessage = "Couldn't reach the server.")
                        }
                    is ApiResult.HttpError ->
                        _uiState.update {
                            it.copy(
                                initialLoading = false,
                                errorMessage = result.message ?: "Server error (${result.code}).",
                            )
                        }
                    ApiResult.Unauthorised ->
                        _uiState.update { it.copy(initialLoading = false) }
                }
            }
        }

        private fun populate(item: MediaItem) {
            _uiState.update {
                it.copy(
                    initialLoading = false,
                    title = item.title,
                    artist = item.artist.orEmpty(),
                    format = item.format.orEmpty(),
                    year = item.year?.toString().orEmpty(),
                    barcode = item.barcode.orEmpty(),
                    label = item.label.orEmpty(),
                    country = item.country.orEmpty(),
                    notes = item.notes.orEmpty(),
                    discogsId = item.discogsId,
                    artworkPath = item.artworkPath,
                    status = item.status,
                    category = item.category,
                    itemUpdatedAt = item.updatedAt,
                )
            }
        }

        fun onTitleChange(value: String) = update { copy(title = value) }

        fun onArtistChange(value: String) = update { copy(artist = value) }

        fun onFormatChange(value: String) = update { copy(format = value) }

        fun onYearChange(value: String) = update { copy(year = value.filter(Char::isDigit).take(4)) }

        fun onBarcodeChange(value: String) = update { copy(barcode = value) }

        fun onLabelChange(value: String) = update { copy(label = value) }

        fun onCountryChange(value: String) = update { copy(country = value) }

        fun onNotesChange(value: String) = update { copy(notes = value) }

        fun onCategoryChange(value: Category) =
            update {
                if (isEditing) this else copy(category = value)
            }

        fun onStatusChange(value: Status) = update { copy(status = value) }

        fun onAutoEnrichChange(value: Boolean) = update { copy(autoEnrich = value) }

        fun onArtworkPicked(
            bytes: ByteArray,
            mimeType: String,
        ) = update {
            copy(
                pendingArtwork = PendingArtwork(bytes, mimeType),
                pendingArtworkUrl = null,
                removeArtwork = false,
            )
        }

        fun onArtworkCleared() = update { copy(pendingArtwork = null) }

        fun onRemoveArtwork() =
            update {
                copy(
                    pendingArtwork = null,
                    pendingArtworkUrl = null,
                    artworkPath = null,
                    removeArtwork = true,
                )
            }

        fun applyExternalResult(result: ExternalSearchResult) {
            _uiState.update { current ->
                val cover = result.coverUrl?.takeIf { it.isNotBlank() }
                current.copy(
                    title = result.title.ifBlank { current.title },
                    artist = result.artist?.takeIf { it.isNotBlank() } ?: current.artist,
                    format = result.format?.takeIf { it.isNotBlank() } ?: current.format,
                    year = result.year?.toString() ?: current.year,
                    barcode = result.barcode?.takeIf { it.isNotBlank() } ?: current.barcode,
                    label = result.label?.takeIf { it.isNotBlank() } ?: current.label,
                    country = result.country?.takeIf { it.isNotBlank() } ?: current.country,
                    discogsId = result.discogsId ?: current.discogsId,
                    pendingArtworkUrl = cover ?: current.pendingArtworkUrl,
                    artworkPath = cover ?: current.artworkPath,
                    removeArtwork = if (cover != null) false else current.removeArtwork,
                )
            }
        }

        fun lookupIsbn() {
            val isbn = _uiState.value.barcode.trim()
            if (isbn.isBlank() || _uiState.value.isLookingUpIsbn) return
            viewModelScope.launch {
                _uiState.update { it.copy(isLookingUpIsbn = true, errorMessage = null) }
                val result = apiCall { api.openLibraryIsbn(isbn) }
                when (result) {
                    is ApiResult.Success -> {
                        val dto = result.value
                        applyExternalResult(
                            ExternalSearchResult(
                                title = dto.title,
                                artist = dto.artist,
                                year = dto.year,
                                barcode = dto.barcode ?: isbn,
                                label = dto.label,
                                coverUrl = dto.artworkUrl ?: dto.thumb,
                            ),
                        )
                        _uiState.update { it.copy(isLookingUpIsbn = false) }
                    }
                    ApiResult.NetworkError ->
                        _uiState.update {
                            it.copy(isLookingUpIsbn = false, errorMessage = "Couldn't reach the server.")
                        }
                    is ApiResult.HttpError ->
                        _uiState.update {
                            it.copy(
                                isLookingUpIsbn = false,
                                errorMessage = "ISBN not found in Open Library",
                            )
                        }
                    ApiResult.Unauthorised ->
                        _uiState.update { it.copy(isLookingUpIsbn = false) }
                }
            }
        }

        fun dismissError() = update { copy(errorMessage = null) }

        fun save() {
            val state = _uiState.value
            if (!state.canSave) return
            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, errorMessage = null) }
                val draft = state.toDraft()
                val saveResult =
                    if (state.isEditing) {
                        mediaRepository.update(itemId!!, draft)
                    } else {
                        mediaRepository.create(draft)
                    }
                when (saveResult) {
                    is ApiResult.Success -> handleSaved(saveResult.value, state)
                    ApiResult.NetworkError -> setError("Couldn't reach the server.")
                    is ApiResult.HttpError -> setError(saveResult.message ?: "Server error (${saveResult.code}).")
                    ApiResult.Unauthorised -> _uiState.update { it.copy(isSaving = false) }
                }
            }
        }

        private suspend fun handleSaved(
            saved: MediaItem,
            beforeState: AddEditUiState,
        ) {
            // A picked file always wins. Otherwise, if the user clicked Remove
            // and the row already existed on the server, wipe the cached image.
            // Enrichment-supplied URLs are sent through MediaItemDraft.artworkPath
            // so the backend caches them server-side — no client upload needed.
            when {
                beforeState.pendingArtwork != null -> {
                    val art = beforeState.pendingArtwork
                    mediaRepository.uploadArtwork(saved.id, art.bytes, art.mimeType)
                }
                beforeState.removeArtwork && beforeState.isEditing -> {
                    mediaRepository.deleteArtwork(saved.id)
                }
            }
            if (beforeState.autoEnrich) {
                enrichmentRepository.enrich(saved.id)
            }
            _uiState.update { it.copy(isSaving = false, savedItemId = saved.id) }
        }

        private fun setError(message: String) {
            _uiState.update { it.copy(isSaving = false, errorMessage = message) }
        }

        private inline fun update(crossinline block: AddEditUiState.() -> AddEditUiState) {
            _uiState.update(block)
        }
    }

private fun AddEditUiState.toDraft(): MediaItemDraft =
    MediaItemDraft(
        title = title.trim(),
        artist = artist.trim(),
        format = format.trim(),
        year = year.toIntOrNull(),
        barcode = barcode.trim().takeIf { it.isNotBlank() },
        notes = notes.trim().takeIf { it.isNotBlank() },
        status = status,
        category = category,
        discogsId = discogsId,
        artworkPath = artworkPath,
        label = label.trim().takeIf { it.isNotBlank() },
        country = country.trim().takeIf { it.isNotBlank() },
    )
