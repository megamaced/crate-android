package com.megamaced.crate.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.SubcomposeAsyncImage
import com.megamaced.crate.data.prefs.ThemeMode
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenSharedWithMe: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileCard(state = state, onOpenSharedWithMe = onOpenSharedWithMe)
            SectionHeader("Appearance")
            ThemeSection(
                themeMode = state.themeMode,
                onThemeChange = viewModel::setThemeMode,
            )

            SectionHeader("Categories")
            CategoriesSection(
                hidden = state.hiddenCategories,
                writesInFlight = state.categoryWritesInFlight,
                onSetVisible = viewModel::setCategoryVisible,
            )

            SectionHeader("Enrichment")
            EnrichmentSection(
                discogs = state.discogs,
                tmdb = state.tmdb,
                rawg = state.rawg,
                comicVine = state.comicVine,
                autoEnrichOnClick = state.market?.autoEnrichOnClick == true,
                enrichAllProgress = state.enrichAllProgress,
                onAutoEnrichChange = viewModel::setAutoEnrichOnClick,
                onEnrichAll = viewModel::enrichAll,
            )

            SectionHeader("Recommendations")
            RecommendationsSection(
                state = state,
                onlineEnabled = state.onlineRecommendations,
                onOnlineChange = viewModel::setOnlineRecommendations,
            )

            SectionHeader("Market")
            MarketSection(
                isLoading = state.isMarketLoading,
                discogs = state.discogs,
                priceCharting = state.priceCharting,
                currency = state.market?.marketCurrency,
                currencies = state.currencies,
                autoFetchMarketRates = state.market?.autoFetchMarketRates == true,
                refreshAllProgress = state.refreshAllProgress,
                onCurrencySelected = viewModel::setCurrency,
                onAutoFetchChange = viewModel::setAutoFetchMarketRates,
                onRefreshAll = viewModel::refreshAllMarketRates,
            )

            SectionHeader("Tokens")
            TokenEditor(
                label = "Discogs token",
                placeholder = "Personal access token",
                state = state.discogs,
                onSave = viewModel::setDiscogsToken,
            )
            TokenEditor(
                label = "TMDB v4 token",
                placeholder = "Bearer token",
                state = state.tmdb,
                onSave = viewModel::setTmdbToken,
            )
            TokenEditor(
                label = "RAWG token",
                placeholder = "API token",
                state = state.rawg,
                onSave = viewModel::setRawgKey,
            )
            TokenEditor(
                label = "ComicVine token",
                placeholder = "API token",
                state = state.comicVine,
                onSave = viewModel::setComicVineKey,
            )
            TokenEditor(
                label = "PriceCharting token",
                placeholder = "API token",
                state = state.priceCharting,
                onSave = viewModel::setPriceChartingToken,
            )

            SectionHeader("Account")
            Button(
                onClick = { confirmLogout = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Log out")
            }

            SectionHeader("Danger zone")
            DangerZoneSection(onWipe = viewModel::wipeCollection)

            SectionHeader("About")
            AboutSection(
                state = state,
                onCheckForUpdates = viewModel::checkForUpdates,
                onDismissUpdateCheck = viewModel::dismissUpdateCheck,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            text = { Text("You'll need to enter your Nextcloud host and re-authenticate.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    viewModel.logout()
                }) { Text("Log out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ProfileCard(
    state: SettingsUiState,
    onOpenSharedWithMe: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Avatar(profile = state.profile)
            Column(modifier = Modifier.weight(1f)) {
                if (state.isProfileLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        text = state.profile?.displayName ?: "Unknown",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = state.profile?.userId.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onOpenSharedWithMe) { Text("Shared with me") }
        }
    }
}

@Composable
private fun Avatar(profile: UserProfile?) {
    val url = profile?.avatarUrl
    if (url.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = profile.displayName,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null)
                }
            },
        )
    }
}

@Composable
private fun TokenEditor(
    label: String,
    placeholder: String,
    state: TokenState,
    onSave: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // Saved tokens are never sent back from the server, so the input always
    // starts empty. The user types to set or replace.
    var input by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text =
                            when {
                                state.isLoading -> "Loading…"
                                state.hasValue -> "Configured."
                                else -> "Not set."
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Close" else "Edit")
                }
            }
            if (expanded) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(placeholder) },
                    visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { revealed = !revealed }) {
                            Icon(
                                imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (revealed) "Hide" else "Show",
                            )
                        }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSave(input.trim())
                            expanded = false
                        },
                        enabled = input.isNotBlank(),
                    ) { Text("Save") }
                    if (state.hasValue) {
                        TextButton(onClick = {
                            input = ""
                            onSave("")
                            expanded = false
                        }) { Text("Remove") }
                    }
                }
            }
        }
    }
}

/**
 * Points at the Tokens section from the sections that depend on it.
 *
 * Tokens sit below these deliberately — they're configured once and never
 * touched again — which only works if the sections that need one say so.
 * Rendered only when the relevant token is genuinely absent, and never while
 * the token state is still loading, so it doesn't flash on every open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissingTokenHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MarketSection(
    isLoading: Boolean,
    discogs: TokenState,
    priceCharting: TokenState,
    currency: String?,
    currencies: List<String>,
    autoFetchMarketRates: Boolean,
    refreshAllProgress: RefreshAllProgress?,
    onCurrencySelected: (String) -> Unit,
    onAutoFetchChange: (Boolean) -> Unit,
    onRefreshAll: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Card
            }

            if (!discogs.hasValue &&
                !priceCharting.hasValue &&
                !discogs.isLoading &&
                !priceCharting.isLoading
            ) {
                MissingTokenHint(
                    "Add a Discogs or PriceCharting token below to enable market values.",
                )
            }

            CurrencyPicker(
                selected = currency,
                currencies = currencies,
                onSelected = onCurrencySelected,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-fetch market rates",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Fetch a fresh market value when an item is enriched.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = autoFetchMarketRates,
                    onCheckedChange = onAutoFetchChange,
                )
            }

            HorizontalDivider()

            val refreshing = refreshAllProgress != null
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Refresh all market rates",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val progress = refreshAllProgress
                    if (progress != null) {
                        Text(
                            text = "Refreshed ${progress.done} of ${progress.total}…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "Re-fetches every item the server can price.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    IconButton(onClick = onRefreshAll) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh all")
                    }
                }
            }
        }
    }
}

@Composable
private fun EnrichmentSection(
    discogs: TokenState,
    tmdb: TokenState,
    rawg: TokenState,
    comicVine: TokenState,
    autoEnrichOnClick: Boolean,
    enrichAllProgress: RefreshAllProgress?,
    onAutoEnrichChange: (Boolean) -> Unit,
    onEnrichAll: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val enrichTokens = listOf(discogs, tmdb, rawg, comicVine)
            if (enrichTokens.none { it.hasValue } && enrichTokens.none { it.isLoading }) {
                MissingTokenHint(
                    "Add tokens below to enrich music, films, games and comics. Books use " +
                        "Open Library, which needs no token.",
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-enrich when opened",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Automatically enrich an item when you view its details.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = autoEnrichOnClick,
                    onCheckedChange = onAutoEnrichChange,
                )
            }

            HorizontalDivider()

            val enriching = enrichAllProgress != null
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enrich all items",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val progress = enrichAllProgress
                    if (progress != null) {
                        Text(
                            text = "Enriched ${progress.done} of ${progress.total}…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "Enrich all un-enriched items via their provider.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (enriching) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    IconButton(onClick = onEnrichAll) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Enrich all")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyPicker(
    selected: String?,
    currencies: List<String>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Currency") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            currencies.forEach { code ->
                DropdownMenuItem(
                    text = { Text(code) },
                    onClick = {
                        expanded = false
                        if (code != selected) onSelected(code)
                    },
                )
            }
            if (currencies.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No currencies returned by server.") },
                    onClick = { expanded = false },
                    enabled = false,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSection(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
) {
    val options = remember { ThemeMode.entries }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Theme", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = mode == themeMode,
                        onClick = { onThemeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    ) {
                        Text(stringResource(mode.labelRes))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationsSection(
    state: SettingsUiState,
    onlineEnabled: Boolean,
    onOnlineChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text =
                    "Items show a \"More from your crate\" row, suggesting similar things you " +
                        "already own or want. That's worked out on this device from your own " +
                        "collection, so it works offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable online recommendations",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text =
                            "Also suggest things you don't own yet, from the service that " +
                                "enriched the item — music, films, books and games. Only " +
                                "enriched items can have these, and they need a connection to " +
                                "your server. Comics aren't covered: ComicVine publishes no " +
                                "similarity data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = onlineEnabled,
                    onCheckedChange = onOnlineChange,
                )
            }

            val recTokens = listOf(state.discogs, state.tmdb, state.rawg)
            if (onlineEnabled && recTokens.none { it.hasValue } && recTokens.none { it.isLoading }) {
                MissingTokenHint(
                    "No provider tokens are set, so online suggestions will only appear for " +
                        "books. Add tokens below for music, films and games.",
                )
            }
        }
    }
}

@Composable
private fun CategoriesSection(
    hidden: Set<Category>,
    writesInFlight: Set<Category>,
    onSetVisible: (Category, Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text =
                    "Hide categories you don't use. Hidden categories disappear from navigation and search. " +
                        "At least one must remain visible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Category.entries.forEach { cat ->
                val isVisible = cat !in hidden
                // Don't let the user hide the last visible category.
                val canToggleOff = hidden.size < Category.entries.size - 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = cat.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = isVisible,
                        // Disabled while this category's own write is in flight,
                        // and when it is the last visible one — you can always
                        // un-hide, but you can't hide everything.
                        enabled = cat !in writesInFlight && (!isVisible || canToggleOff),
                        onCheckedChange = { visible -> onSetVisible(cat, visible) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DangerZoneSection(onWipe: (List<String>) -> Unit) {
    var confirmWipe by remember { mutableStateOf(false) }
    // Derived from Category so the scopes and their labels can't drift from the
    // rest of the app — the api values are what the endpoint expects, and the
    // labels are the app's own plurals ("Films", not "Film").
    val scopes = remember { Category.entries.map { it.apiValue to it.label } + (PLAYLISTS_SCOPE to "Playlists") }
    // Nothing pre-selected: this deletes from the server irreversibly, so every
    // scope is an explicit opt-in and the confirm button starts disabled.
    var selected by remember { mutableStateOf(emptySet<String>()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Wipe collection",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Permanently delete selected data from your collection on the server. This cannot be undone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { confirmWipe = true },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Wipe data…")
            }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Wipe data") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Select categories to permanently delete:")
                    scopes.forEach { (scope, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(
                                checked = scope in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + scope else selected - scope
                                },
                            )
                            Text(
                                text = label,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmWipe = false
                        onWipe(selected.toList())
                    },
                    enabled = selected.isNotEmpty(),
                ) { Text("Wipe selected", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Cancel") }
            },
        )
    }
}

/** Scope key the wipe endpoint uses for playlists; the rest are category api values. */
private const val PLAYLISTS_SCOPE = "playlists"

@Composable
private fun AboutSection(
    state: SettingsUiState,
    onCheckForUpdates: () -> Unit,
    onDismissUpdateCheck: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("App version", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = com.megamaced.crate.BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Crate server version", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = state.profile?.crateVersion ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            UpdateCheckRow(
                state = state.updateCheck,
                onCheck = onCheckForUpdates,
                onDismiss = onDismissUpdateCheck,
                onOpenRelease = { uriHandler.openUri(it) },
            )
        }
    }
}

@Composable
private fun UpdateCheckRow(
    state: UpdateCheckState,
    onCheck: () -> Unit,
    onDismiss: () -> Unit,
    onOpenRelease: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Check for updates", style = MaterialTheme.typography.bodyMedium)
            when (state) {
                UpdateCheckState.Checking -> {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }

                is UpdateCheckState.Available -> {
                    TextButton(onClick = { onOpenRelease(state.htmlUrl) }) {
                        Text("Open ${state.tag}")
                    }
                }

                else -> {
                    TextButton(onClick = onCheck) { Text("Check") }
                }
            }
        }
        when (state) {
            UpdateCheckState.UpToDate -> {
                StatusLine(
                    text = "You're on the latest version.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    onDismiss = onDismiss,
                )
            }

            UpdateCheckState.Failed -> {
                StatusLine(
                    text = "Couldn't reach GitHub. Check your connection and try again.",
                    color = MaterialTheme.colorScheme.error,
                    onDismiss = onDismiss,
                )
            }

            is UpdateCheckState.Available -> {
                StatusLine(
                    text = "${state.tag} is available on GitHub.",
                    color = MaterialTheme.colorScheme.primary,
                    onDismiss = onDismiss,
                )
            }

            UpdateCheckState.Idle, UpdateCheckState.Checking -> {
                Unit
            }
        }
    }
}

@Composable
private fun StatusLine(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}
