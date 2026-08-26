package com.megamaced.crate.ui.screen.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.megamaced.crate.R
import com.megamaced.crate.data.api.dto.SuggestionDto
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.Status
import com.megamaced.crate.domain.model.Track
import com.megamaced.crate.ui.components.ArtworkImage
import com.megamaced.crate.ui.components.ArtworkSize
import com.megamaced.crate.ui.components.LoadingState
import com.megamaced.crate.ui.components.PhotoImage
import com.megamaced.crate.ui.components.RecommendationRow
import com.megamaced.crate.ui.components.SuggestionTarget
import com.megamaced.crate.ui.screen.collection.decadeOf
import com.megamaced.crate.ui.screen.collection.genreTokens
import com.megamaced.crate.ui.screen.share.ShareSheet
import com.megamaced.crate.ui.screen.share.ShareTarget
import com.megamaced.crate.ui.theme.SectionSpacing
import com.megamaced.crate.ui.theme.WithinSectionSpacing
import com.megamaced.crate.ui.theme.crateColors
import com.megamaced.crate.util.resolve

/** The collection filter axis a tapped value in the detail view narrows to. */
enum class DetailFilterAxis {
    Genre,
    Format,
    Decade,
}

/**
 * The list a tapped value in the detail view jumps to, and the filter it
 * arrives with. One axis at a time, mirroring the web app, which clears every
 * other filter before applying the tapped one: a tap means "show me everything
 * like this", not "narrow what I was already looking at".
 *
 * [isShared] sends an item that came from a share to its shared-category page
 * rather than to the user's own collection.
 */
data class CollectionFilterTarget(
    val categoryApiValue: String,
    val isShared: Boolean,
    val axis: DetailFilterAxis,
    val value: String,
    // The item's own owned/wanted state, so the list opens on the tab that
    // holds it rather than on a tab the item isn't in.
    val status: Status,
)

/**
 * Height every badge in the detail view measures at least, whether or not it
 * is tappable. Material's own chip height, so the badges line up with the
 * chips used elsewhere in the app.
 */
private val BadgeMinHeight = AssistChipDefaults.Height

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long, String) -> Unit,
    onFilterCollection: (CollectionFilterTarget) -> Unit,
    onOpenItem: (Long) -> Unit,
    onAddSuggestion: (Category, SuggestionDto) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val recommendations by viewModel.recommendations.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val errorText = uiState.errorMessage?.resolve()
    LaunchedEffect(errorText) {
        errorText?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onBack()
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(uiState.item?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    val item = uiState.item
                    // Show the kebab whenever we can write (own item or a
                    // read/write share). Purely read-only shares get no menu.
                    if (item != null && uiState.canWrite) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.action_more),
                            )
                        }
                        DetailMenu(
                            item = item,
                            isOwner = uiState.isOwner,
                            expanded = menuExpanded,
                            onDismiss = { menuExpanded = false },
                            onEdit = {
                                menuExpanded = false
                                onEdit(item.id, item.category.apiValue)
                            },
                            onShare = {
                                menuExpanded = false
                                shareOpen = true
                            },
                            onEnrich = {
                                menuExpanded = false
                                viewModel.enrich()
                            },
                            onStrip = {
                                menuExpanded = false
                                viewModel.stripEnrichment()
                            },
                            onFetchMarketValue = {
                                menuExpanded = false
                                viewModel.fetchMarketValue()
                            },
                            onDelete = {
                                menuExpanded = false
                                confirmDelete = true
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val item = uiState.item
        when {
            uiState.isLoading -> {
                LoadingState(modifier = Modifier.padding(innerPadding))
            }

            item == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.detail_not_found), style = MaterialTheme.typography.bodyLarge)
                }
            }

            else -> {
                ItemDetailContent(
                    item = item,
                    activeAction = uiState.activeAction,
                    sharedByUser = uiState.sharedByUser,
                    canWrite = uiState.canWrite,
                    isShared = uiState.isShared,
                    onFilterCollection = onFilterCollection,
                    recommendations = recommendations,
                    onOpenItem = onOpenItem,
                    onAddSuggestion = { suggestion ->
                        onAddSuggestion(item.category, suggestion)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }
    }

    if (shareOpen && uiState.item != null) {
        ShareSheet(
            target = ShareTarget.Album,
            resourceId = uiState.item!!.id,
            onDismiss = { shareOpen = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun DetailMenu(
    item: MediaItem,
    isOwner: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onEnrich: () -> Unit,
    onStrip: () -> Unit,
    onFetchMarketValue: () -> Unit,
    onDelete: () -> Unit,
) {
    val isEnriched = item.discogsId != null || item.tracklist.isNotEmpty() || item.artistBio != null
    val supportsMarketValue = item.category == Category.Music ||
        item.category == Category.Games ||
        item.category == Category.Comics

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // Edit + enrichment + market are available to anyone who can write.
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_edit)) },
            onClick = onEdit,
        )
        // Re-sharing is owner-only.
        if (isOwner) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_share)) },
                onClick = onShare,
            )
        }
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (isEnriched) R.string.detail_menu_re_enrich else R.string.detail_menu_enrich,
                    ),
                )
            },
            onClick = onEnrich,
        )
        if (isEnriched) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.detail_menu_remove_enrichment)) },
                onClick = onStrip,
            )
        }
        if (supportsMarketValue) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.detail_menu_fetch_market_rate)) },
                onClick = onFetchMarketValue,
            )
        }
        // Deletion is owner-only — a sharee gets a 403 server-side anyway.
        if (isOwner) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun ItemDetailContent(
    item: MediaItem,
    activeAction: DetailAction?,
    sharedByUser: String?,
    canWrite: Boolean,
    isShared: Boolean,
    onFilterCollection: (CollectionFilterTarget) -> Unit,
    recommendations: RecommendationsUiState,
    onOpenItem: (Long) -> Unit,
    onAddSuggestion: (SuggestionDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Every tapped value lands on the same list — this item's category, or its
    // shared-category page when the item came from a share — so the sections
    // below only have to choose the axis.
    val onFilter: (DetailFilterAxis, String) -> Unit = { axis, value ->
        onFilterCollection(
            CollectionFilterTarget(
                categoryApiValue = item.category.apiValue,
                isShared = isShared,
                axis = axis,
                value = value,
                status = item.status,
            ),
        )
    }

    // LazyColumn rather than a scrolling Column: a box set's tracklist is
    // dozens of rows, and a Column composes and measures every one of them
    // before the first frame can be drawn.
    LazyColumn(modifier = modifier) {
        item(key = "hero") {
            Box {
                ArtworkImage(
                    itemId = item.id,
                    contentDescription = item.title,
                    size = ArtworkSize.Full,
                    updatedAt = item.updatedAt,
                    category = item.category,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
                if (activeAction != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            }
        }

        item(key = "metadata") {
            // Section order mirrors ItemDetailView.vue: the badges, then the
            // price blocks, then the metadata grid (label, genres, barcode),
            // then the photos.
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing),
            ) {
                // Provenance, title and artist read as one heading block, so
                // they stay tight instead of taking a section gap each.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (sharedByUser != null) {
                        Text(
                            text = stringResource(
                                R.string.detail_shared_by,
                                sharedByUser,
                                stringResource(
                                    if (canWrite) {
                                        R.string.detail_share_can_edit
                                    } else {
                                        R.string.detail_share_read_only
                                    },
                                ),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    item.artist?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                ChipRow(item = item, onFilter = onFilter)

                if (item.purchasePrice.isPresent) {
                    PurchasePriceRow(item)
                }

                if (item.marketValue.isPresent) {
                    MarketValueCard(item)
                }

                item.label?.takeIf { it.isNotBlank() }?.let { label ->
                    FactRow(label = stringResource(labelFieldNameRes(item.category)), value = label)
                }

                GenreChips(item = item, onFilter = onFilter)

                item.barcode?.takeIf { it.isNotBlank() }?.let { barcode ->
                    FactRow(label = stringResource(R.string.field_barcode), value = barcode)
                }

                if (item.hasPhoto1 || item.hasPhoto2) {
                    PhotoGallery(item)
                }
            }
        }

        if (item.tracklist.isNotEmpty()) {
            item(key = "tracklist-header") {
                SectionHeader(
                    text = stringResource(R.string.detail_tracklist),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = SectionSpacing),
                )
            }
            // Positional keys: the tracklist is a fixed, replaced-wholesale list
            // and its own fields aren't unique (two untitled tracks, repeated
            // positions across discs).
            itemsIndexed(
                items = item.tracklist,
                key = { index, _ -> "track-$index" },
            ) { _, track ->
                TrackRow(track = track, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        item(key = "prose") {
            Column(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = SectionSpacing,
                    bottom = SectionSpacing,
                ),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing),
            ) {
                item.pressingNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                    ProseSection(title = stringResource(R.string.detail_pressing_notes), body = notes)
                }

                item.artistBio?.takeIf { it.isNotBlank() }?.let { bio ->
                    ProseSection(
                        title = stringResource(
                            when (item.category) {
                                Category.Films -> R.string.detail_about_director
                                Category.Books -> R.string.detail_about_author
                                Category.Games -> R.string.detail_about_developer
                                Category.Comics -> R.string.detail_about_publisher
                                Category.Music -> R.string.detail_about_artist
                            },
                        ),
                        body = bio,
                    )
                }

                item.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    ProseSection(title = stringResource(R.string.detail_notes), body = notes)
                }
            }
        }

        // Outside the padded column: RecommendationRow manages its own
        // horizontal padding so its LazyRow can scroll edge to edge.
        item(key = "local-suggestions") {
            RecommendationRow(
                title = stringResource(R.string.detail_more_from_your_crate),
                entries = recommendations.local,
                onClick = { entry ->
                    (entry.target as? SuggestionTarget.Owned)?.let { onOpenItem(it.itemId) }
                },
            )
        }

        item(key = "online-suggestions") {
            RecommendationRow(
                title = stringResource(R.string.detail_if_you_like_this),
                source = recommendations.onlineSource,
                entries = recommendations.online,
                onClick = { entry ->
                    (entry.target as? SuggestionTarget.Provider)?.let { onAddSuggestion(it.suggestion) }
                },
            )
        }

        item(key = "bottom-spacer") {
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            track.position?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = track.title.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            track.duration?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * The badges under the artist, in the web app's order: format, year, status,
 * country. Format and year are tappable and take the user to that slice of the
 * collection, as they do there. The year filters by DECADE: that is the only
 * year axis the collection has, and "everything from the 1990s" is the cut
 * worth offering.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    item: MediaItem,
    onFilter: (DetailFilterAxis, String) -> Unit,
) {
    val format = item.format?.takeIf { it.isNotBlank() }
    val year = item.year?.takeIf { it != 0 }?.toString()
    // Read off the same year field as [year], so the two appear together.
    val decade = decadeOf(item)
    val country = item.country?.takeIf { it.isNotBlank() }
    // Anything not explicitly wanted reads as owned, matching the web app and
    // the mapper, which already lands an unknown API status on [Status.Owned].
    val wanted = item.status == Status.Wanted
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (format != null) {
            ValueBadge(
                value = format,
                a11y = stringResource(R.string.detail_format_filter_a11y, format),
                onClick = { onFilter(DetailFilterAxis.Format, format) },
            )
        }
        if (year != null && decade != null) {
            ValueBadge(
                value = year,
                a11y = stringResource(R.string.detail_year_filter_a11y, decade),
                onClick = { onFilter(DetailFilterAxis.Decade, decade) },
            )
        }
        // A state rather than an axis the collection can be sliced by, so it
        // is inert like the country. Wanted is the state worth noticing and
        // takes a filled container; owned keeps the muted default, as the two
        // do in the web app.
        ValueBadge(
            value = stringResource(if (wanted) R.string.status_wanted else R.string.status_owned),
            containerColor = if (wanted) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        )
        if (country != null) {
            ValueBadge(value = country)
        }
    }
}

/**
 * One pill in the detail view's badge rows. Format, year, status, country and
 * genre all render through here, so they share a shape, a text style and a
 * height and cannot drift apart from one another.
 *
 * [onClick] is the only thing that separates them: a badge with one narrows the
 * collection to that value and is announced as a button, and [a11y] replaces
 * the bare value with what the tap will actually do. A badge without one is
 * inert — deliberately not a clickable with an empty handler, which would take
 * focus and be announced as a button indistinguishable from the ones that
 * navigate.
 *
 * [containerColor] is the one visual axis a caller may vary, for a value whose
 * state deserves emphasis. Its text colour follows from it, and every other
 * axis stays shared so the badges keep lining up whatever colour they carry.
 */
@Composable
private fun ValueBadge(
    value: String,
    modifier: Modifier = Modifier,
    a11y: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
) {
    val label: @Composable () -> Unit = {
        Box(
            // A minimum height rather than a fixed one: the label still grows
            // with the system font scale instead of being clipped by it.
            modifier = Modifier
                .defaultMinSize(minHeight = BadgeMinHeight)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
    val shape = AssistChipDefaults.shape
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    if (onClick == null) {
        Surface(modifier = modifier, shape = shape, color = containerColor, border = border, content = label)
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.semantics {
                if (a11y != null) {
                    contentDescription = a11y
                }
                role = Role.Button
            },
            shape = shape,
            color = containerColor,
            border = border,
            content = label,
        )
    }
}

/**
 * Genres as tappable chips: each one filters the collection (or the shared
 * category, for a shared item) down to that genre. Providers store them as one
 * comma-separated string, so they are split before rendering.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreChips(
    item: MediaItem,
    onFilter: (DetailFilterAxis, String) -> Unit,
) {
    val genres = genreTokens(item)
    if (genres.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(WithinSectionSpacing)) {
        Text(
            text = stringResource(R.string.detail_genres),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            genres.forEach { genre ->
                ValueBadge(
                    value = genre,
                    a11y = stringResource(R.string.detail_genre_filter_a11y, genre),
                    onClick = { onFilter(DetailFilterAxis.Genre, genre) },
                )
            }
        }
    }
}

/**
 * What the record-label field is called for a category — the same names the add
 * form uses, and the same ones the web app derives in labelFieldLabel.
 */
@StringRes
private fun labelFieldNameRes(category: Category): Int =
    when (category) {
        Category.Films -> R.string.field_label_films
        Category.Books -> R.string.field_label_books
        Category.Games -> R.string.field_label_games
        Category.Comics -> R.string.field_label_comics
        Category.Music -> R.string.field_label_music
    }

/**
 * One row of the metadata grid: a name in a fixed-width column with its value
 * beside it. For short values only — prose belongs in a [ProseSection], where
 * the text starts under its heading instead of in the second column.
 */
@Composable
private fun FactRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MarketValueCard(item: MediaItem) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.detail_market_value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            val main = item.marketValue.main
            if (main != null) {
                Text(
                    text = formatMoney(main, item.marketValue.currency),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            val subValues = listOfNotNull(
                item.marketValue.loose?.let {
                    stringResource(R.string.detail_market_loose, formatMoney(it, item.marketValue.currency))
                },
                item.marketValue.new?.let {
                    stringResource(R.string.detail_market_new, formatMoney(it, item.marketValue.currency))
                },
            )
            if (subValues.isNotEmpty()) {
                Text(
                    text = subValues.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            item.marketValue.fetchedAt?.let {
                Text(
                    text = stringResource(R.string.detail_market_fetched, it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A heading with its body directly beneath it, both starting at the same left
 * edge. Every prose section goes through this so none of them can drift into a
 * label/value layout and end up indented away from its own heading.
 */
@Composable
private fun ProseSection(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(WithinSectionSpacing)) {
        SectionHeader(title)
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatMoney(
    value: Double,
    currency: String?,
): String {
    val symbol = when (currency?.uppercase()) {
        "USD" -> "$"
        "GBP" -> "£"
        "EUR" -> "€"
        "JPY" -> "¥"
        null, "" -> ""
        else -> "$currency "
    }
    return "$symbol%.2f".format(value)
}

/**
 * Renders the user's recorded purchase price with an optional gain/loss
 * delta against the current market value. The delta is only shown when
 * both prices share a currency — cross-currency comparisons would require
 * FX conversion, deliberately out of scope for Phase 13.
 */
@Composable
private fun PurchasePriceRow(item: MediaItem) {
    val price = item.purchasePrice.amount ?: return
    val priceCurrency = item.purchasePrice.currency
    val market = item.marketValue.main
    val marketCurrency = item.marketValue.currency

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.purchase_price_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = formatMoney(price, priceCurrency),
            style = MaterialTheme.typography.titleMedium,
        )
        when {
            market != null &&
                priceCurrency != null &&
                marketCurrency != null &&
                priceCurrency.equals(marketCurrency, ignoreCase = true) -> {
                val diff = market - price
                val percent =
                    if (price > 0.0) {
                        val pct = diff / price * 100
                        if (pct >= 0) pct.toLong() else -pct.toLong()
                    } else {
                        null
                    }
                val direction = if (diff >= 0) "+" else "−"
                val tint = if (diff >= 0) crateColors.gain else MaterialTheme.colorScheme.error
                Text(
                    text = "$direction${formatMoney(kotlin.math.abs(diff), priceCurrency)}" +
                        (percent?.let { " ($direction$it%)" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    color = tint,
                )
            }

            market != null -> {
                Text(
                    text = stringResource(R.string.detail_currencies_differ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Two-up photo strip below the metadata. Tapping a thumbnail opens a
 * full-size viewer dialog. Photos are rendered via [PhotoImage] so they
 * go through the same Coil cache + auth pipeline as artwork.
 */
@Composable
private fun PhotoGallery(item: MediaItem) {
    var fullSlot by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(WithinSectionSpacing)) {
        SectionHeader(stringResource(R.string.photos_section))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (item.hasPhoto1) {
                PhotoThumb(item = item, slot = 1, onClick = { fullSlot = 1 })
            }
            if (item.hasPhoto2) {
                PhotoThumb(item = item, slot = 2, onClick = { fullSlot = 2 })
            }
        }
    }
    val visibleSlot = fullSlot
    if (visibleSlot != null) {
        Dialog(onDismissRequest = { fullSlot = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { fullSlot = null },
                contentAlignment = Alignment.Center,
            ) {
                PhotoImage(
                    itemId = item.id,
                    slot = visibleSlot,
                    contentDescription = stringResource(R.string.detail_photo_a11y, visibleSlot),
                    size = ArtworkSize.Full,
                    updatedAt = item.updatedAt,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PhotoThumb(
    item: MediaItem,
    slot: Int,
    onClick: () -> Unit,
) {
    PhotoImage(
        itemId = item.id,
        slot = slot,
        contentDescription = stringResource(R.string.detail_photo_a11y, slot),
        size = ArtworkSize.Thumb,
        updatedAt = item.updatedAt,
        modifier = Modifier
            .size(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    )
}
