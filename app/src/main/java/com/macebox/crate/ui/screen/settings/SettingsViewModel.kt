package com.macebox.crate.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.data.auth.SessionManager
import com.macebox.crate.data.prefs.ThemeMode
import com.macebox.crate.data.prefs.UserPreferences
import com.macebox.crate.domain.model.MarketSettings
import com.macebox.crate.domain.model.UserProfile
import com.macebox.crate.domain.repository.EnrichmentRepository
import com.macebox.crate.domain.repository.MediaRepository
import com.macebox.crate.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TokenState(
    val isLoading: Boolean = true,
    val hasValue: Boolean = false,
)

data class RefreshAllProgress(
    val total: Int,
    val done: Int,
)

data class SettingsUiState(
    val profile: UserProfile? = null,
    val isProfileLoading: Boolean = true,
    val discogs: TokenState = TokenState(),
    val tmdb: TokenState = TokenState(),
    val rawg: TokenState = TokenState(),
    val comicVine: TokenState = TokenState(),
    val priceCharting: TokenState = TokenState(),
    val market: MarketSettings? = null,
    val isMarketLoading: Boolean = true,
    val currencies: List<String> = emptyList(),
    val refreshAllProgress: RefreshAllProgress? = null,
    val enrichAllProgress: RefreshAllProgress? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val errorMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val enrichmentRepository: EnrichmentRepository,
        private val mediaRepository: MediaRepository,
        private val userPreferences: UserPreferences,
        private val sessionManager: SessionManager,
    ) : ViewModel() {
        private val tokens = MutableStateFlow(TokensState())
        private val profile = MutableStateFlow<ProfileState>(ProfileState())
        private val market = MutableStateFlow(MarketState())
        private val refreshProgress = MutableStateFlow<RefreshAllProgress?>(null)
        private val enrichProgress = MutableStateFlow<RefreshAllProgress?>(null)
        private val errorMessage = MutableStateFlow<String?>(null)

        val uiState: StateFlow<SettingsUiState> =
            combine(
                combine(profile, market) { p, m -> p to m },
                tokens,
                userPreferences.flow.map { it.themeMode },
                combine(refreshProgress, enrichProgress, errorMessage) { r, e, err -> Triple(r, e, err) },
            ) { (p, m), t, theme, (progress, enrichProg, err) ->
                SettingsUiState(
                    profile = p.profile,
                    isProfileLoading = p.isLoading,
                    discogs = t.discogs,
                    tmdb = t.tmdb,
                    rawg = t.rawg,
                    comicVine = t.comicVine,
                    priceCharting = t.priceCharting,
                    market = m.settings,
                    isMarketLoading = m.isLoading,
                    currencies = m.currencies,
                    refreshAllProgress = progress,
                    enrichAllProgress = enrichProg,
                    themeMode = theme,
                    errorMessage = err,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState(),
            )

        init {
            loadProfile()
            loadTokens()
            loadMarket()
        }

        private fun loadProfile() {
            viewModelScope.launch {
                when (val result = settingsRepository.getMe()) {
                    is ApiResult.Success ->
                        profile.value = ProfileState(profile = result.value, isLoading = false)
                    ApiResult.NetworkError -> reportError("Couldn't reach the server.").also {
                        profile.update { it.copy(isLoading = false) }
                    }
                    is ApiResult.HttpError -> reportError(result.message ?: "Server error (${result.code}).").also {
                        profile.update { it.copy(isLoading = false) }
                    }
                    ApiResult.Unauthorised ->
                        profile.update { it.copy(isLoading = false) }
                }
            }
        }

        private fun loadTokens() {
            viewModelScope.launch {
                val discogs = async { settingsRepository.hasDiscogsToken() }
                val tmdb = async { settingsRepository.hasTmdbToken() }
                val rawg = async { settingsRepository.hasRawgKey() }
                val comicVine = async { settingsRepository.hasComicVineKey() }
                val priceCharting = async { settingsRepository.hasPriceChartingToken() }
                tokens.value =
                    TokensState(
                        discogs = boolToState(discogs.await()),
                        tmdb = boolToState(tmdb.await()),
                        rawg = boolToState(rawg.await()),
                        comicVine = boolToState(comicVine.await()),
                        priceCharting = boolToState(priceCharting.await()),
                    )
            }
        }

        private fun loadMarket() {
            viewModelScope.launch {
                val settingsResult = settingsRepository.getMarketSettings()
                val currenciesResult = settingsRepository.getCurrencies()
                val settings = (settingsResult as? ApiResult.Success)?.value
                val currencies = (currenciesResult as? ApiResult.Success)?.value.orEmpty()
                market.value = MarketState(settings = settings, currencies = currencies, isLoading = false)
                if (settingsResult is ApiResult.HttpError) {
                    reportError(settingsResult.message ?: "Server error (${settingsResult.code}).")
                }
            }
        }

        fun setDiscogsToken(value: String) =
            saveString({ settingsRepository.setDiscogsToken(value) }) {
                tokens.update {
                    it.copy(discogs = TokenState(isLoading = false, hasValue = value.isNotBlank()))
                }
            }

        fun setTmdbToken(value: String) =
            saveString({ settingsRepository.setTmdbToken(value) }) {
                tokens.update { it.copy(tmdb = presenceToState(value)) }
            }

        fun setRawgKey(value: String) =
            saveString({ settingsRepository.setRawgKey(value) }) {
                tokens.update { it.copy(rawg = presenceToState(value)) }
            }

        fun setComicVineKey(value: String) =
            saveString({ settingsRepository.setComicVineKey(value) }) {
                tokens.update { it.copy(comicVine = presenceToState(value)) }
            }

        fun setPriceChartingToken(value: String) =
            saveString({ settingsRepository.setPriceChartingToken(value) }) {
                tokens.update { it.copy(priceCharting = presenceToState(value)) }
            }

        fun setAutoFetchMarketRates(enabled: Boolean) {
            val current = market.value.settings ?: return
            updateMarket(current.copy(autoFetchMarketRates = enabled))
        }

        fun setCurrency(currency: String) {
            val current =
                market.value.settings
                    ?: MarketSettings(autoFetchMarketRates = false, marketCurrency = currency)
            updateMarket(current.copy(marketCurrency = currency))
        }

        private fun updateMarket(settings: MarketSettings) {
            viewModelScope.launch {
                market.update { it.copy(settings = settings) }
                when (val result = settingsRepository.setMarketSettings(settings)) {
                    is ApiResult.Success -> { /* persisted */ }
                    ApiResult.NetworkError -> reportError("Couldn't reach the server.")
                    is ApiResult.HttpError -> reportError(result.message ?: "Server error (${result.code}).")
                    ApiResult.Unauthorised -> { /* SessionManager handles */ }
                }
            }
        }

        fun refreshAllMarketRates() {
            viewModelScope.launch {
                refreshProgress.value = RefreshAllProgress(total = 0, done = 0)
                val refreshResult = enrichmentRepository.listRefreshableMarketValues()
                val refreshable =
                    when (refreshResult) {
                        is ApiResult.Success -> refreshResult.value
                        ApiResult.NetworkError -> {
                            reportError("Couldn't reach the server.")
                            refreshProgress.value = null
                            return@launch
                        }
                        is ApiResult.HttpError -> {
                            reportError(refreshResult.message ?: "Server error (${refreshResult.code}).")
                            refreshProgress.value = null
                            return@launch
                        }
                        ApiResult.Unauthorised -> {
                            refreshProgress.value = null
                            return@launch
                        }
                    }
                val ids = refreshable.itemIds
                refreshProgress.value = RefreshAllProgress(total = ids.size, done = 0)
                ids.forEachIndexed { index, id ->
                    ensureActive()
                    enrichmentRepository.fetchMarketValue(id)
                    refreshProgress.value = RefreshAllProgress(total = ids.size, done = index + 1)
                }
                refreshProgress.value = null
            }
        }

        fun setAutoEnrichOnClick(enabled: Boolean) {
            val current = market.value.settings ?: return
            updateMarket(current.copy(autoEnrichOnClick = enabled))
        }

        fun setAutoEnrichOnImport(enabled: Boolean) {
            val current = market.value.settings ?: return
            updateMarket(current.copy(autoEnrichOnImport = enabled))
        }

        fun enrichAll() {
            viewModelScope.launch {
                enrichProgress.value = RefreshAllProgress(total = 0, done = 0)
                val mediaResult = enrichmentRepository.listUnenrichedItems()
                val ids = when (mediaResult) {
                    is ApiResult.Success -> mediaResult.value
                    ApiResult.NetworkError -> {
                        reportError("Couldn't reach the server.")
                        enrichProgress.value = null
                        return@launch
                    }
                    is ApiResult.HttpError -> {
                        reportError(mediaResult.message ?: "Server error (${mediaResult.code}).")
                        enrichProgress.value = null
                        return@launch
                    }
                    ApiResult.Unauthorised -> {
                        enrichProgress.value = null
                        return@launch
                    }
                }
                enrichProgress.value = RefreshAllProgress(total = ids.size, done = 0)
                ids.forEachIndexed { index, id ->
                    ensureActive()
                    enrichmentRepository.enrich(id)
                    enrichProgress.value = RefreshAllProgress(total = ids.size, done = index + 1)
                }
                enrichProgress.value = null
            }
        }

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch { userPreferences.setThemeMode(mode) }
        }

        fun logout() {
            sessionManager.logout()
        }

        fun wipeCollection(scopes: List<String>) {
            viewModelScope.launch {
                when (val result = mediaRepository.wipeCollection(scopes)) {
                    is ApiResult.Success -> { /* local DB also cleared by repo */ }
                    ApiResult.NetworkError -> reportError("Couldn't reach the server.")
                    is ApiResult.HttpError -> reportError(result.message ?: "Server error (${result.code}).")
                    ApiResult.Unauthorised -> { /* SessionManager handles */ }
                }
            }
        }

        fun dismissError() {
            errorMessage.value = null
        }

        private fun saveString(
            call: suspend () -> ApiResult<Unit>,
            onSuccess: () -> Unit,
        ) {
            viewModelScope.launch {
                when (val result = call()) {
                    is ApiResult.Success -> onSuccess()
                    ApiResult.NetworkError -> reportError("Couldn't reach the server.")
                    is ApiResult.HttpError -> reportError(result.message ?: "Server error (${result.code}).")
                    ApiResult.Unauthorised -> { /* SessionManager handles */ }
                }
            }
        }

        private fun reportError(message: String) {
            errorMessage.value = message
        }

        private fun boolToState(result: ApiResult<Boolean>): TokenState =
            when (result) {
                is ApiResult.Success -> TokenState(isLoading = false, hasValue = result.value)
                else -> TokenState(isLoading = false)
            }

        private fun presenceToState(value: String): TokenState =
            TokenState(isLoading = false, hasValue = value.isNotBlank())

        private data class ProfileState(
            val profile: UserProfile? = null,
            val isLoading: Boolean = true,
        )

        private data class TokensState(
            val discogs: TokenState = TokenState(),
            val tmdb: TokenState = TokenState(),
            val rawg: TokenState = TokenState(),
            val comicVine: TokenState = TokenState(),
            val priceCharting: TokenState = TokenState(),
        )

        private data class MarketState(
            val settings: MarketSettings? = null,
            val currencies: List<String> = emptyList(),
            val isLoading: Boolean = true,
        )

    }
