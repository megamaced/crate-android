package com.megamaced.crate.ui.screen.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.dto.SuggestionDto
import com.megamaced.crate.data.auth.CurrentSession
import com.megamaced.crate.data.api.CrateApiService
import com.megamaced.crate.data.api.apiCall
import com.megamaced.crate.domain.LocalSimilarity
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.repository.EnrichmentRepository
import com.megamaced.crate.domain.repository.MediaRepository
import com.megamaced.crate.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemDetailUiState(
    val itemId: Long = 0,
    val item: MediaItem? = null,
    val isLoading: Boolean = true,
    val activeAction: DetailAction? = null,
    val errorMessage: String? = null,
    val deleted: Boolean = false,
    // True when the loaded item belongs to another user — i.e. visible to us
    // only via a share. Ownership is a superset of write permission.
    val isShared: Boolean = false,
    // True when the item is our own (ownership grants full control including
    // delete + re-share).
    val isOwner: Boolean = false,
    // True when we can add/edit this item — own item OR a read/write share.
    // Delete and re-share stay owner-only regardless (see [isOwner]).
    val canWrite: Boolean = false,
    val sharedByUser: String? = null,
)

/**
 * The two suggestion rows, kept separate from [ItemDetailUiState] because they
 * load on different schedules and neither should hold up the item itself.
 *
 * [local] is derived from the Room cache, so it is available offline. [online]
 * comes from the server (which in turn calls the enrichment provider), so with
 * no connection it stays empty and the row is simply not rendered.
 */
data class RecommendationsUiState(
    val local: List<MediaItem> = emptyList(),
    val online: List<SuggestionDto> = emptyList(),
    val onlineSource: String? = null,
)

enum class DetailAction {
    Enrich,
    Strip,
    FetchMarketValue,
    Delete,
}

@HiltViewModel
class ItemDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val mediaRepository: MediaRepository,
        private val enrichmentRepository: EnrichmentRepository,
        private val settingsRepository: SettingsRepository,
        private val api: CrateApiService,
        currentSession: CurrentSession,
    ) : ViewModel() {
        private val itemId: Long = checkNotNull(savedStateHandle["itemId"]) {
            "Detail route requires an itemId argument"
        }

        private val activeAction = MutableStateFlow<DetailAction?>(null)
        private val errorMessage = MutableStateFlow<String?>(null)
        private val deleted = MutableStateFlow(false)

        // Write permission for a shared item, learned from the network fetch
        // (the per-item permission isn't persisted through Room). Own items
        // don't rely on this — ownership already grants write.
        private val sharedCanWrite = MutableStateFlow(false)

        // Login name is read once at construction; it doesn't change without
        // a re-authentication that would tear this ViewModel down anyway.
        private val currentLoginName: String? = currentSession.loginName()

        val uiState: StateFlow<ItemDetailUiState> =
            combine(
                mediaRepository.observe(itemId),
                activeAction,
                errorMessage,
                deleted,
                sharedCanWrite,
            ) { item, action, err, isDeleted, canWriteShare ->
                val itemOwner = item?.userId
                // Fail closed: an item carries an explicit owner only when it
                // reached us via a share, so a known owner we can't positively
                // match to ourselves (e.g. login name momentarily unavailable)
                // is treated as shared/read-only rather than granting owner-only
                // Delete/Re-share. A null owner means the server attributed no
                // separate owner — that's our own item.
                val shared =
                    when {
                        item == null -> false
                        itemOwner == null -> false
                        currentLoginName == null -> true
                        else -> itemOwner != currentLoginName
                    }
                val owner = item != null && !shared
                ItemDetailUiState(
                    itemId = itemId,
                    item = item,
                    isLoading = item == null && !isDeleted,
                    activeAction = action,
                    errorMessage = err,
                    deleted = isDeleted,
                    isShared = shared,
                    isOwner = owner,
                    canWrite = owner || (shared && canWriteShare),
                    sharedByUser = if (shared) itemOwner else null,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ItemDetailUiState(itemId = itemId),
            )

        private val onlineSuggestions = MutableStateFlow(RecommendationsUiState())

        /**
         * "More from your crate", derived from the Room cache so it works with
         * no connection, plus the provider row when one was fetched.
         *
         * Ranking rules live in [LocalSimilarity] and mirror the server's
         * scorer, which computes the same row for the web client.
         */
        val recommendations: StateFlow<RecommendationsUiState> =
            combine(
                mediaRepository.observe(itemId).filterNotNull(),
                mediaRepository.observeAll(),
                onlineSuggestions,
            ) { item, all, online ->
                online.copy(local = LocalSimilarity.rank(item, all))
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = RecommendationsUiState(),
            )

        /**
         * Fetches the provider row, but only when the user has opted in.
         * Failure is silent by design — offline, or a provider being down,
         * should leave the row absent rather than show an error on a detail
         * screen that is otherwise complete.
         */
        private suspend fun loadOnlineSuggestions() {
            if (!settingsRepository.onlineRecommendationsFlow.first()) return
            val result = apiCall { api.getRecommendations(itemId) }
            if (result is ApiResult.Success) {
                onlineSuggestions.value =
                    RecommendationsUiState(
                        online = result.value.online,
                        onlineSource = result.value.onlineSource,
                    )
            }
        }

        init {
            viewModelScope.launch {
                loadOnlineSuggestions()
            }
            viewModelScope.launch {
                val refreshed = mediaRepository.refreshSingle(itemId)
                // Capture the per-item write permission from the network fetch;
                // it isn't carried through Room, so this is our only source.
                val networkCanWrite = (refreshed as? ApiResult.Success)?.value?.canWrite == true
                sharedCanWrite.value = networkCanWrite
                runAutoBackgroundFetches(networkCanWrite)
            }
        }

        private suspend fun runAutoBackgroundFetches(networkCanWrite: Boolean) {
            val me = (settingsRepository.getMe() as? ApiResult.Success)?.value ?: return
            var item = mediaRepository.observe(itemId).firstOrNull() ?: return

            // Auto enrich/market are writes. Skip them only for read-only
            // shared items; a read/write sharee (or the owner) may run them.
            val shared = item.userId != null && currentLoginName != null && item.userId != currentLoginName
            if (shared && !networkCanWrite) {
                return
            }

            if (me.autoEnrichOnClick && item.genres.isNullOrBlank() && item.artistBio.isNullOrBlank()) {
                val result = enrichmentRepository.enrich(itemId)
                if (result is ApiResult.Success) item = result.value
            }

            if (me.autoFetchMarketRates && !item.marketValue.isPresent && shouldAutoFetchMarket(item)) {
                enrichmentRepository.fetchMarketValue(itemId)
            }
        }

        // Mirrors the NC web app's shouldAutoFetchMarket() — music needs a Discogs ID
        // (the lookup keys on it); game/comic key on the item's title via PriceCharting;
        // book and film have no market-value source.
        private fun shouldAutoFetchMarket(item: MediaItem): Boolean =
            when (item.category) {
                Category.Music -> !item.discogsId.isNullOrBlank()
                Category.Games, Category.Comics -> !item.title.isNullOrBlank()
                else -> false
            }

        fun enrich() {
            run(DetailAction.Enrich) { enrichmentRepository.enrich(itemId) }
        }

        fun stripEnrichment() {
            run(DetailAction.Strip) { enrichmentRepository.stripEnrichment(itemId) }
        }

        fun fetchMarketValue() {
            run(DetailAction.FetchMarketValue) { enrichmentRepository.fetchMarketValue(itemId) }
        }

        fun delete() {
            viewModelScope.launch {
                activeAction.value = DetailAction.Delete
                errorMessage.value = null
                when (val result = mediaRepository.delete(itemId)) {
                    is ApiResult.Success -> deleted.value = true
                    ApiResult.NetworkError -> errorMessage.value = "Couldn't reach the server."
                    is ApiResult.HttpError -> errorMessage.value = result.message ?: "Server error (${result.code})."
                    ApiResult.Unauthorised -> { /* SessionManager already handled */ }
                }
                activeAction.value = null
            }
        }

        fun dismissError() {
            errorMessage.value = null
        }

        private fun run(
            action: DetailAction,
            block: suspend () -> ApiResult<MediaItem>,
        ) {
            viewModelScope.launch {
                activeAction.value = action
                errorMessage.value = null
                when (val result = block()) {
                    is ApiResult.Success -> { /* Repository writes through to Room */ }
                    ApiResult.NetworkError -> errorMessage.value = "Couldn't reach the server."
                    is ApiResult.HttpError -> errorMessage.value = result.message ?: "Server error (${result.code})."
                    ApiResult.Unauthorised -> { /* SessionManager already handled */ }
                }
                activeAction.value = null
            }
        }
    }
