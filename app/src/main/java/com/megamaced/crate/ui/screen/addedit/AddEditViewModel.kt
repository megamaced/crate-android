package com.megamaced.crate.ui.screen.addedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamaced.crate.R
import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.CrateApiService
import com.megamaced.crate.data.api.apiCall
import com.megamaced.crate.data.api.toUiText
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.MediaItemDraft
import com.megamaced.crate.domain.model.Status
import com.megamaced.crate.domain.model.UserProfile
import com.megamaced.crate.domain.repository.EnrichmentRepository
import com.megamaced.crate.domain.repository.MediaRepository
import com.megamaced.crate.domain.repository.SettingsRepository
import com.megamaced.crate.util.MAX_PICKED_IMAGE_BYTES
import com.megamaced.crate.util.PickedImageReader
import com.megamaced.crate.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.util.Calendar
import javax.inject.Inject

private val CURRENT_YEAR: Int = Calendar.getInstance().get(Calendar.YEAR)
private const val MIN_YEAR = 1800

/** Currency the price selector defaults to until the user's profile loads. */
const val DEFAULT_CURRENCY = "GBP"

/**
 * Static fallback for the currency picker; mirrors MarketValueService::
 * SUPPORTED_CURRENCIES on the server. Used until `/settings/currencies`
 * responds, and as a safety net if that request fails.
 */
val DEFAULT_CURRENCIES = listOf(
    "GBP",
    "USD",
    "EUR",
    "CAD",
    "AUD",
    "JPY",
    "CHF",
    "MXN",
    "BRL",
    "NZD",
    "SEK",
    "ZAR",
)

@Serializable
data class AddEditUiState(
    val isEditing: Boolean = false,
    val editingItemId: Long? = null,
    // Uid of the collection owner when adding into a shared library/category
    // we can write to; null for a normal add into our own collection.
    val owner: String? = null,
    // True when the category is fixed (adding into a shared *category*), so the
    // category picker is hidden and locked to the shared scope's category.
    val categoryLocked: Boolean = false,
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
    val pendingArtwork: PendingImage? = null,
    /**
     * Cover URL supplied by an enrichment source (Discogs/TMDB/etc.). Shown
     * in the artwork preview before save; on save the backend caches the
     * image and the local /apps/crate/artwork/{id} URL takes over. Mirrors
     * `enrichPreviewUrl` in AddEditModal.vue.
     */
    val pendingArtworkUrl: String? = null,
    /** True when the user clicked Remove on existing artwork. */
    val removeArtwork: Boolean = false,
    /**
     * Extra user photo slots. Each tracks a pending file (uploaded post-save)
     * and a Remove flag that flips when the user clears an existing photo.
     * The presence flags come from the loaded item.
     */
    val pendingPhoto1: PendingImage? = null,
    val pendingPhoto2: PendingImage? = null,
    val removePhoto1: Boolean = false,
    val removePhoto2: Boolean = false,
    val hasPhoto1: Boolean = false,
    val hasPhoto2: Boolean = false,
    val isLookingUpIsbn: Boolean = false,
    /** Held as a raw string so the user can clear it back to "" without coercion. */
    val purchasePrice: String = "",
    val purchasePriceCurrency: String = DEFAULT_CURRENCY,
    /** Currency allowlist served from /settings/currencies; falls back to [DEFAULT_CURRENCIES]. */
    val availableCurrencies: List<String> = DEFAULT_CURRENCIES,
    // Transient because it is in-flight state, not form input: the coroutine
    // that raised it died with the process, and [readSavedForm] drops it anyway.
    @Transient val errorMessage: UiText? = null,
    val savedItemId: Long? = null,
    /**
     * True when the item saved but at least one photo slot didn't. Reported
     * before the screen pops, so the user isn't left believing a photo
     * uploaded when it didn't.
     */
    val photoUploadFailed: Boolean = false,
    /**
     * True when a picked image turned out to be over the size cap only once its
     * bytes were read. Providers often declare no length, so the check at pick
     * time can't catch every case, and a silently dropped upload would leave
     * the user believing the image was saved.
     */
    val imageTooLarge: Boolean = false,
) {
    val yearError: Boolean
        get() = year.isNotBlank() && (year.toIntOrNull()?.let { it !in MIN_YEAR..CURRENT_YEAR } ?: true)

    /**
     * True when a format is set that isn't one of the category's known values —
     * a provider can hand back a platform or format string Crate doesn't file
     * under. Advisory only: the server accepts free text, so this warns rather
     * than blocking the save.
     */
    val formatUnrecognised: Boolean
        get() = format.isNotBlank() && !CategoryFormats.isValid(category, format)

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

/**
 * An image the user picked but hasn't uploaded yet, held as the content Uri
 * rather than its bytes: reading eagerly would block the main thread on a
 * cloud-only photo, three decoded images risk an OOM, and the bytes could
 * never survive [SavedStateHandle]'s 1 MB transaction limit.
 */
@Serializable
data class PendingImage(
    val uri: String,
    val mimeType: String,
)

const val SCAN_RESULT_KEY = "scan_result"

/** Key the in-progress form is parked under across process death. */
private const val FORM_STATE_KEY = "add_edit_form"

private val formJson = Json { ignoreUnknownKeys = true }

@HiltViewModel
class AddEditViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val mediaRepository: MediaRepository,
        private val settingsRepository: SettingsRepository,
        private val enrichmentRepository: EnrichmentRepository,
        private val imageReader: PickedImageReader,
        private val api: CrateApiService,
    ) : ViewModel() {
        private val itemId: Long? = savedStateHandle.get<Long>("itemId")?.takeIf { it > 0L }
        private val initialCategory: Category =
            savedStateHandle
                .get<String>("category")
                ?.let { Category.fromApi(it) }
                ?: Category.Music

        // Owner uid to create the item under (shared-collection add); null for a
        // normal add. A category-share add also pins the category (owner + an
        // explicit category nav arg), so lock the picker in that case.
        private val owner: String? = savedStateHandle.get<String>("owner")?.takeIf { it.isNotBlank() }
        private val categoryLocked: Boolean =
            owner != null && !savedStateHandle.get<String>("category").isNullOrBlank()

        // A form recovered from process death already holds everything the nav
        // args and the item fetch would supply, so those one-off initialisers
        // must not run again and overwrite what the user typed.
        private val restored: AddEditUiState? = readSavedForm()

        private val _uiState =
            MutableStateFlow(
                restored
                    ?: AddEditUiState(
                        isEditing = itemId != null,
                        editingItemId = itemId,
                        owner = owner,
                        categoryLocked = categoryLocked,
                        category = initialCategory,
                        initialLoading = itemId != null,
                    ),
            )
        val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

        init {
            // The currency allowlist is server state, not user input, so it is
            // always re-fetched.
            loadCurrencyOptions()
            if (restored == null) {
                loadProfileDefaults()
                if (itemId != null) loadExisting(itemId)
                // Before the prefill, so an existing item's own status still wins.
                if (itemId == null && savedStateHandle.get<Boolean>("defaultWanted") == true) {
                    update { copy(status = Status.Wanted) }
                }
                applyInitialPrefill(savedStateHandle.get<String>("prefillJson"))
            }
            // NB: barcode-scan results (SCAN_RESULT_KEY) are consumed solely by
            // AddEditItemScreen's LaunchedEffect + onScanResultConsumed. Do not
            // also observe them here — two owners resetting the same key races.
        }

        /**
         * The parked form, or null when there is nothing usable to restore.
         *
         * A snapshot taken while the item fetch was still in flight is
         * discarded: it holds no user input yet, and keeping it would skip the
         * fetch and leave an edit form blank.
         */
        private fun readSavedForm(): AddEditUiState? {
            val json = savedStateHandle.get<String>(FORM_STATE_KEY) ?: return null
            val saved = runCatching { formJson.decodeFromString<AddEditUiState>(json) }.getOrNull()
            if (saved == null || saved.initialLoading) return null
            // Drop the in-flight/one-shot flags: the coroutines that owned them
            // died with the process.
            return saved.copy(
                isSaving = false,
                isLookingUpIsbn = false,
                errorMessage = null,
                savedItemId = null,
                photoUploadFailed = false,
                imageTooLarge = false,
            )
        }

        /**
         * Pull the supported-currency allowlist from the server so the
         * picker stays in sync with MarketValueService::SUPPORTED_CURRENCIES.
         * Failure leaves the static [DEFAULT_CURRENCIES] in place; the form
         * stays usable.
         */
        private fun loadCurrencyOptions() {
            viewModelScope.launch {
                val result = apiCall { api.getCurrencies() }
                if (result is ApiResult.Success && result.value.isNotEmpty()) {
                    update { copy(availableCurrencies = result.value) }
                }
            }
        }

        private fun applyInitialPrefill(prefillJson: String?) {
            if (prefillJson.isNullOrBlank()) return
            runCatching { Json.decodeFromString<ExternalSearchResult>(prefillJson) }
                .getOrNull()
                ?.let(::applyExternalResult)
        }

        private fun loadProfileDefaults() {
            viewModelScope.launch {
                when (val result = settingsRepository.getMe()) {
                    is ApiResult.Success -> {
                        applyProfile(result.value)
                    }

                    else -> { /* Ignore — toggle simply stays off. */ }
                }
            }
        }

        private fun applyProfile(profile: UserProfile) {
            val currency = profile.marketCurrency?.takeIf { it.isNotBlank() } ?: DEFAULT_CURRENCY
            update {
                if (isEditing) {
                    this
                } else {
                    copy(
                        autoEnrich = profile.autoEnrichOnClick,
                        // Pre-fill the currency on new items, but only if the
                        // user hasn't already typed one.
                        purchasePriceCurrency = if (purchasePriceCurrency == DEFAULT_CURRENCY) currency else purchasePriceCurrency,
                    )
                }
            }
        }

        private fun loadExisting(id: Long) {
            viewModelScope.launch {
                when (val result = mediaRepository.refreshSingle(id)) {
                    is ApiResult.Success -> {
                        populate(result.value)
                    }

                    ApiResult.NetworkError -> {
                        update {
                            copy(initialLoading = false, errorMessage = UiText.Res(R.string.error_network))
                        }
                    }

                    is ApiResult.HttpError -> {
                        update {
                            copy(
                                initialLoading = false,
                                errorMessage = result.toUiText(),
                            )
                        }
                    }

                    ApiResult.Unauthorised -> {
                        update { copy(initialLoading = false) }
                    }
                }
            }
        }

        private fun populate(item: MediaItem) {
            update {
                copy(
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
                    purchasePrice =
                        item.purchasePrice.amount
                            ?.let(::formatPriceForInput)
                            .orEmpty(),
                    purchasePriceCurrency =
                        item.purchasePrice.currency
                            ?.takeIf { c -> c.isNotBlank() }
                            ?: purchasePriceCurrency,
                    hasPhoto1 = item.hasPhoto1,
                    hasPhoto2 = item.hasPhoto2,
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

        /**
         * Accepts any of: "", "12", "12.50", "12,50". Strips currency symbols
         * and thousand separators that some keyboards inject. Coerces the
         * decimal separator to a dot so [String.toDoubleOrNull] can parse it.
         */
        fun onPurchasePriceChange(value: String) =
            update {
                // Keep digits and a SINGLE decimal separator. Without the
                // dedupe, "12.3.4" survives the filter but fails
                // String.toDoubleOrNull(), so the price is silently dropped on
                // save even though the field still shows a value.
                val cleaned =
                    buildString {
                        var dotSeen = false
                        for (ch in value.replace(',', '.')) {
                            when {
                                ch.isDigit() -> {
                                    append(ch)
                                }

                                ch == '.' && !dotSeen -> {
                                    append(ch)
                                    dotSeen = true
                                }
                            }
                        }
                    }
                copy(purchasePrice = cleaned)
            }

        fun onPurchasePriceCurrencyChange(value: String) = update { copy(purchasePriceCurrency = value) }

        fun onArtworkPicked(uri: String) {
            acceptPicked(uri) { pending ->
                update {
                    copy(
                        pendingArtwork = pending,
                        pendingArtworkUrl = null,
                        removeArtwork = false,
                    )
                }
            }
        }

        fun onRemoveArtwork() =
            update {
                copy(
                    pendingArtwork = null,
                    pendingArtworkUrl = null,
                    artworkPath = null,
                    removeArtwork = true,
                )
            }

        fun onPhotoPicked(
            slot: Int,
            uri: String,
        ) {
            acceptPicked(uri) { pending ->
                update {
                    when (slot) {
                        1 -> copy(pendingPhoto1 = pending, removePhoto1 = false)
                        2 -> copy(pendingPhoto2 = pending, removePhoto2 = false)
                        else -> this
                    }
                }
            }
        }

        /**
         * Vets a picked image off the main thread before it enters the form.
         *
         * The size check is up front rather than at upload time so an
         * oversized pick is refused while the user is still looking at the
         * picker's outcome, not after they press Save.
         */
        private fun acceptPicked(
            uri: String,
            onAccepted: (PendingImage) -> Unit,
        ) {
            viewModelScope.launch {
                val length = imageReader.length(uri)
                if (length != null && length > MAX_PICKED_IMAGE_BYTES) {
                    update { copy(errorMessage = UiText.Res(R.string.add_edit_image_too_large)) }
                    return@launch
                }
                val mime = imageReader.mimeType(uri) ?: "image/*"
                onAccepted(PendingImage(uri = uri, mimeType = mime))
            }
        }

        fun onRemovePhoto(slot: Int) =
            update {
                when (slot) {
                    1 -> copy(pendingPhoto1 = null, removePhoto1 = true)
                    2 -> copy(pendingPhoto2 = null, removePhoto2 = true)
                    else -> this
                }
            }

        fun applyExternalResult(result: ExternalSearchResult) {
            update {
                val cover = result.coverUrl?.takeIf { it.isNotBlank() }
                copy(
                    title = result.title.ifBlank { title },
                    artist = result.artist?.takeIf { it.isNotBlank() } ?: artist,
                    format = result.format?.takeIf { it.isNotBlank() } ?: format,
                    year = result.year?.toString() ?: year,
                    barcode = result.barcode?.takeIf { it.isNotBlank() } ?: barcode,
                    label = result.label?.takeIf { it.isNotBlank() } ?: label,
                    country = result.country?.takeIf { it.isNotBlank() } ?: country,
                    discogsId = result.discogsId ?: discogsId,
                    pendingArtworkUrl = cover ?: pendingArtworkUrl,
                    artworkPath = cover ?: artworkPath,
                    removeArtwork = if (cover != null) false else removeArtwork,
                )
            }
        }

        fun lookupIsbn() {
            val isbn = _uiState.value.barcode.trim()
            if (isbn.isBlank() || _uiState.value.isLookingUpIsbn) return
            viewModelScope.launch {
                update { copy(isLookingUpIsbn = true, errorMessage = null) }
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
                        update { copy(isLookingUpIsbn = false) }
                    }

                    ApiResult.NetworkError -> {
                        update {
                            copy(isLookingUpIsbn = false, errorMessage = UiText.Res(R.string.error_network))
                        }
                    }

                    is ApiResult.HttpError -> {
                        update {
                            copy(
                                isLookingUpIsbn = false,
                                errorMessage = UiText.Res(R.string.add_edit_isbn_not_found),
                            )
                        }
                    }

                    ApiResult.Unauthorised -> {
                        update { copy(isLookingUpIsbn = false) }
                    }
                }
            }
        }

        fun dismissError() = update { copy(errorMessage = null) }

        fun save() {
            val state = _uiState.value
            if (!state.canSave) return
            viewModelScope.launch {
                update { copy(isSaving = true, errorMessage = null, photoUploadFailed = false, imageTooLarge = false) }
                val draft = state.toDraft()
                val saveResult =
                    if (state.isEditing) {
                        mediaRepository.update(itemId!!, draft)
                    } else {
                        mediaRepository.create(draft)
                    }
                when (saveResult) {
                    is ApiResult.Success -> handleSaved(saveResult.value, state)
                    ApiResult.NetworkError -> setError(UiText.Res(R.string.error_network))
                    is ApiResult.HttpError -> setError(saveResult.toUiText())
                    ApiResult.Unauthorised -> update { copy(isSaving = false) }
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
            var tooLarge = false
            when {
                beforeState.pendingArtwork != null -> {
                    when (val read = imageReader.read(beforeState.pendingArtwork.uri)) {
                        is PickedImageReader.ReadResult.Success -> {
                            mediaRepository.uploadArtwork(saved.id, read.bytes, beforeState.pendingArtwork.mimeType)
                        }

                        // Over the cap once the bytes were counted. The item
                        // itself saved, so this is reported rather than fatal.
                        PickedImageReader.ReadResult.TooLarge -> {
                            tooLarge = true
                        }

                        // Unreadable — a picker grant that didn't survive
                        // process death, or a file since deleted.
                        PickedImageReader.ReadResult.Unavailable -> {
                            Unit
                        }
                    }
                }

                beforeState.removeArtwork && beforeState.isEditing -> {
                    mediaRepository.deleteArtwork(saved.id)
                }
            }
            // Same logic applied to each photo slot. Independent of artwork
            // (different endpoint, different appdata folder server-side).
            val photo1 = applyPhotoSlot(saved.id, slot = 1, beforeState.pendingPhoto1, beforeState.removePhoto1, beforeState.isEditing)
            val photo2 = applyPhotoSlot(saved.id, slot = 2, beforeState.pendingPhoto2, beforeState.removePhoto2, beforeState.isEditing)
            if (photo1 == SlotOutcome.TooLarge || photo2 == SlotOutcome.TooLarge) tooLarge = true
            if (beforeState.autoEnrich) {
                enrichmentRepository.enrich(saved.id)
            }
            // savedItemId is what pops the screen, so it is set last and in the
            // same emission as the photo outcome — the screen can then report a
            // failure before it navigates away.
            update {
                copy(
                    isSaving = false,
                    photoUploadFailed = photo1 == SlotOutcome.Failed || photo2 == SlotOutcome.Failed,
                    imageTooLarge = tooLarge,
                    savedItemId = saved.id,
                )
            }
        }

        /** What a photo slot's pending work came to. [Ok] also covers "nothing to do". */
        private enum class SlotOutcome { Ok, Failed, TooLarge }

        private suspend fun applyPhotoSlot(
            itemId: Long,
            slot: Int,
            pending: PendingImage?,
            remove: Boolean,
            isEditing: Boolean,
        ): SlotOutcome {
            val result = when {
                pending != null -> {
                    when (val read = imageReader.read(pending.uri)) {
                        is PickedImageReader.ReadResult.Success -> {
                            mediaRepository.uploadPhoto(itemId, slot, read.bytes, pending.mimeType)
                        }

                        // Reported apart from a failed upload: one is worth
                        // retrying, the other needs a smaller image.
                        PickedImageReader.ReadResult.TooLarge -> {
                            return SlotOutcome.TooLarge
                        }

                        PickedImageReader.ReadResult.Unavailable -> {
                            return SlotOutcome.Failed
                        }
                    }
                }

                remove && isEditing -> {
                    mediaRepository.deletePhoto(itemId, slot)
                }

                else -> {
                    return SlotOutcome.Ok
                }
            }
            return if (result is ApiResult.Success) SlotOutcome.Ok else SlotOutcome.Failed
        }

        private fun setError(message: UiText) {
            update { copy(isSaving = false, errorMessage = message) }
        }

        /**
         * Single funnel for every state write, so the parked copy in
         * [SavedStateHandle] can never drift from what the form is showing.
         */
        private fun update(block: AddEditUiState.() -> AddEditUiState) {
            _uiState.update(block)
            savedStateHandle[FORM_STATE_KEY] = formJson.encodeToString(_uiState.value)
        }
    }

private fun AddEditUiState.toDraft(): MediaItemDraft {
    val price = purchasePrice.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    return MediaItemDraft(
        title = title.trim(),
        artist = artist.trim(),
        format = format.trim(),
        year = year.toIntOrNull(),
        barcode = barcode.trim().takeIf { it.isNotBlank() },
        notes = notes.trim().takeIf { it.isNotBlank() },
        status = status,
        category = category,
        discogsId = discogsId,
        // The server reads null as "leave unchanged" and "" as "clear", and the
        // Json config omits null keys entirely — so an emptied field has to go
        // out as "" or the old value survives the next fetch.
        artworkPath = artworkPath.orEmpty(),
        label = label.trim(),
        country = country.trim(),
        purchasePrice = price,
        purchasePriceCurrency = if (price != null) purchasePriceCurrency else null,
        // Non-null only for a shared-collection add — routes the create to the
        // owner's collection (server verifies the read/write share).
        owner = owner,
    )
}

/**
 * Render a stored purchase price for the input box without losing
 * precision: integers as "12", fractions as "12.5". Matches the way
 * [String.toDoubleOrNull] parses what the user typed in the first place.
 */
private fun formatPriceForInput(value: Double): String {
    if (value == value.toLong().toDouble()) return value.toLong().toString()
    return value.toString()
}
