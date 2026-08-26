package com.megamaced.crate.ui.screen.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.megamaced.crate.R
import com.megamaced.crate.data.prefs.CollectionViewMode
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.MarketValue
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.SortField
import com.megamaced.crate.domain.model.Status
import com.megamaced.crate.ui.components.ArtworkImage
import com.megamaced.crate.ui.components.CollectionFilterBar
import com.megamaced.crate.ui.components.EmptyState
import com.megamaced.crate.ui.components.MediaCard
import com.megamaced.crate.ui.components.SortMenuButton
import com.megamaced.crate.ui.network.LocalIsOnline
import com.megamaced.crate.ui.screen.share.ShareSheet
import com.megamaced.crate.ui.screen.share.ShareTarget
import com.megamaced.crate.util.UiText
import com.megamaced.crate.util.resolve

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    onItemClick: (Long) -> Unit,
    onAddItem: (Category) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    viewModel: CollectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isOnline = LocalIsOnline.current
    // Share-sheet state: ShareTarget plus optional category key. Opened from the
    // toolbar Share button (category or whole-library sharing lives here now,
    // not in Settings). Null = closed.
    var shareSheet by remember { mutableStateOf<Pair<ShareTarget, String>?>(null) }

    val errorText = uiState.errorMessage?.resolve()
    LaunchedEffect(errorText) {
        errorText?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(uiState.category.labelRes)) },
                actions = {
                    CategoryMenu(
                        selected = uiState.category,
                        visible = uiState.visibleCategories,
                        onCategorySelected = viewModel::selectCategory,
                    )
                    StatusMenu(
                        selected = uiState.status,
                        onStatusSelected = viewModel::selectStatus,
                    )
                    ViewModeToggle(
                        current = uiState.viewMode,
                        onSelected = viewModel::setViewMode,
                    )
                    SortMenuButton(
                        category = uiState.category,
                        selected = uiState.sort,
                        onSelected = viewModel::selectSort,
                    )
                    if (isOnline) {
                        ShareCollectionMenu(
                            category = uiState.category,
                            onShareCategory = {
                                shareSheet = ShareTarget.Category to uiState.category.apiValue
                            },
                            onShareLibrary = { shareSheet = ShareTarget.Library to "" },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (isOnline) {
                FloatingActionButton(onClick = { onAddItem(uiState.category) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_item))
                }
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CollectionFilterBar(
                    formats = uiState.availableFormats,
                    totalCount = uiState.totalCount,
                    selectedFormats = uiState.selectedFormats,
                    onToggleFormat = viewModel::toggleFormat,
                    onClearFormats = viewModel::clearFormats,
                    decades = uiState.availableDecades,
                    genres = uiState.availableGenres,
                    selectedDecade = uiState.selectedDecade,
                    selectedGenre = uiState.selectedGenre,
                    onDecadeSelected = viewModel::selectDecade,
                    onGenreSelected = viewModel::selectGenre,
                )
                val artistFirst = uiState.sort.axis == SortField.Artist
                // The empty case is decided here rather than inside the grid and
                // the list: what to say depends on the status and on whether a
                // filter emptied it, and neither of those reaches them.
                if (uiState.groups.all { it.items.isEmpty() }) {
                    CollectionEmptyState(
                        status = uiState.status,
                        filterDescription = activeFilterDescription(uiState),
                        onClearFilters = viewModel::clearValueFilters,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    when (uiState.viewMode) {
                        CollectionViewMode.Card -> CollectionGrid(
                            groups = uiState.groups,
                            onItemClick = onItemClick,
                            widthSizeClass = widthSizeClass,
                            modifier = Modifier.fillMaxSize(),
                            artistFirst = artistFirst,
                        )

                        CollectionViewMode.List -> CollectionList(
                            groups = uiState.groups,
                            onItemClick = onItemClick,
                            modifier = Modifier.fillMaxSize(),
                            artistFirst = artistFirst,
                        )
                    }
                }
            }
        }
    }

    shareSheet?.let { (target, category) ->
        ShareSheet(
            target = target,
            category = category,
            onDismiss = { shareSheet = null },
        )
    }
}

@Composable
private fun ShareCollectionMenu(
    category: Category,
    onShareCategory: () -> Unit,
    onShareLibrary: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Outlined.Share,
            contentDescription = stringResource(R.string.action_share),
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.collection_share_category, stringResource(category.labelRes))) },
            onClick = {
                expanded = false
                onShareCategory()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.collection_share_library)) },
            onClick = {
                expanded = false
                onShareLibrary()
            },
        )
    }
}

/**
 * The category picker. It sits in the app bar rather than the body because a
 * phone has room for one row of controls above the grid, not several. The app
 * bar title names the current category, so the button is a control rather than
 * the sign of what is selected; only categories the user hasn't hidden in
 * settings are offered.
 */
@Composable
private fun CategoryMenu(
    selected: Category,
    visible: List<Category>,
    onCategorySelected: (Category) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    if (visible.isEmpty()) return

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Outlined.Category,
            contentDescription = stringResource(
                R.string.collection_category_button,
                stringResource(selected.labelRes),
            ),
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        visible.forEach { category ->
            val label = stringResource(category.labelRes)
            DropdownMenuItem(
                text = {
                    Text(
                        if (category == selected) {
                            stringResource(R.string.category_option_selected, label)
                        } else {
                            label
                        },
                    )
                },
                onClick = {
                    onCategorySelected(category)
                    expanded = false
                },
            )
        }
    }
}

/**
 * Owned / wanted, the mode the list is in. Unlike the category, no other piece
 * of chrome names it, so the button carries the state itself: a filled bookmark
 * in the accent colour for the wanted list against an outline for the
 * collection, and a content description that reads the current mode out.
 */
@Composable
private fun StatusMenu(
    selected: Status,
    onStatusSelected: (Status) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val wanted = selected == Status.Wanted

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = if (wanted) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = stringResource(
                R.string.collection_status_button,
                stringResource(selected.labelRes),
            ),
            tint = if (wanted) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        Status.entries.forEach { status ->
            val label = stringResource(status.labelRes)
            DropdownMenuItem(
                text = {
                    Text(
                        if (status == selected) {
                            stringResource(R.string.status_option_selected, label)
                        } else {
                            label
                        },
                    )
                },
                onClick = {
                    onStatusSelected(status)
                    expanded = false
                },
            )
        }
    }
}

/**
 * The empty collection, worded for the status it is empty on and for whether a
 * filter is what emptied it — a filtered-empty list gets the way out of it
 * rather than an invitation to add something.
 */
@Composable
private fun CollectionEmptyState(
    status: Status,
    filterDescription: String?,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wanted = status == Status.Wanted
    if (filterDescription != null) {
        EmptyState(
            title = stringResource(
                if (wanted) {
                    R.string.collection_empty_filtered_wanted
                } else {
                    R.string.collection_empty_filtered_collection
                },
                filterDescription,
            ),
            modifier = modifier,
            action = {
                TextButton(onClick = onClearFilters) {
                    Text(stringResource(R.string.collection_clear_filters))
                }
            },
        )
    } else {
        EmptyState(
            title = stringResource(
                if (wanted) R.string.collection_empty_wanted_title else R.string.collection_empty_title,
            ),
            subtitle = stringResource(
                if (wanted) R.string.collection_empty_wanted_subtitle else R.string.collection_empty_subtitle,
            ),
            modifier = modifier,
        )
    }
}

/**
 * The active filters as one phrase — "1990s Rock LP" — for the empty state to
 * read back. Null when nothing is filtered. Decade, genre then format, the
 * order the web app reads them in.
 */
private fun activeFilterDescription(state: CollectionUiState): String? =
    (listOfNotNull(state.selectedDecade, state.selectedGenre) + state.selectedFormats.sorted())
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ")

@Composable
internal fun ViewModeToggle(
    current: CollectionViewMode,
    onSelected: (CollectionViewMode) -> Unit,
) {
    val target = if (current == CollectionViewMode.Card) CollectionViewMode.List else CollectionViewMode.Card
    val (icon, labelRes) = when (current) {
        CollectionViewMode.Card -> Icons.AutoMirrored.Outlined.ViewList to R.string.collection_view_switch_to_list
        CollectionViewMode.List -> Icons.Outlined.GridView to R.string.collection_view_switch_to_card
    }
    IconButton(onClick = { onSelected(target) }) {
        Icon(imageVector = icon, contentDescription = stringResource(labelRes))
    }
}

/**
 * [artistFirst] leads each card with the artist instead of the title, so a grid
 * sorted by artist / director / author reads in the order it is sorted.
 */
@Composable
internal fun CollectionGrid(
    groups: List<ItemGroup>,
    onItemClick: (Long) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    artistFirst: Boolean = false,
) {
    if (groups.all { it.items.isEmpty() }) {
        EmptyCollection(modifier)
        return
    }

    val columns = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 2
        WindowWidthSizeClass.Medium -> 3
        else -> 5
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (group in groups) {
            if (group.header != null) {
                item(
                    key = "header:${group.header}",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    GroupHeader(header = group.header)
                }
            }
            items(group.items, key = { it.id }) { item ->
                MediaCard(
                    item = item,
                    onClick = { onItemClick(item.id) },
                    artistFirst = artistFirst,
                )
            }
        }
    }
}

/**
 * [artistFirst] leads each row with the artist instead of the title. Runs of
 * consecutive items by the same artist print the name once, so a list sorted by
 * artist / director / author reads as one cluster per name.
 */
@Composable
internal fun CollectionList(
    groups: List<ItemGroup>,
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    artistFirst: Boolean = false,
) {
    if (groups.all { it.items.isEmpty() }) {
        EmptyCollection(modifier)
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        for (group in groups) {
            if (group.header != null) {
                item(key = "header:${group.header}") {
                    GroupHeader(header = group.header)
                }
            }
            itemsIndexed(group.items, key = { _, item -> item.id }) { index, item ->
                CollectionListRow(
                    item = item,
                    onClick = { onItemClick(item.id) },
                    artistFirst = artistFirst,
                    // Repeats are only suppressed within a group; the first row
                    // after a header always names its artist.
                    hideArtist = artistFirst && isArtistRepeat(item, group.items.getOrNull(index - 1)),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun GroupHeader(header: UiText) {
    Text(
        text = header.resolve(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun CollectionListRow(
    item: MediaItem,
    onClick: () -> Unit,
    artistFirst: Boolean = false,
    hideArtist: Boolean = false,
) {
    val rowLabel = stringResource(
        R.string.collection_row_a11y,
        item.title,
        item.artist?.takeIf { it.isNotBlank() } ?: "",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = rowLabel
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtworkImage(
            itemId = item.id,
            contentDescription = item.title,
            updatedAt = item.updatedAt,
            category = item.category,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val artist = item.artist?.takeIf { it.isNotBlank() }
            // Artist leads only when there is one to lead with; an item without
            // an artist keeps the title as its headline either way.
            val artistLeads = artistFirst && artist != null
            if (artistLeads && !hideArtist) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = item.title,
                style = if (artistLeads) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                // Unspecified keeps the inherited content colour for the
                // title-led layout, exactly as before.
                color = if (artistLeads) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!artistLeads && artist != null) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val meta = listOfNotNull(
                item.format?.takeIf { it.isNotBlank() },
                item.year?.toString(),
                item.label?.takeIf { it.isNotBlank() },
            ).joinToString(" · ").takeIf { it.isNotBlank() }
            if (meta != null) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        formatMarketValue(item.marketValue)?.let { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EmptyCollection(modifier: Modifier) {
    EmptyState(
        title = stringResource(R.string.collection_empty_title),
        subtitle = stringResource(R.string.collection_empty_subtitle),
        modifier = modifier,
    )
}

private fun formatMarketValue(value: MarketValue): String? {
    val main = value.main ?: value.new ?: value.loose ?: return null
    val symbol = when (value.currency?.uppercase()) {
        "GBP" -> "£"
        "USD" -> "$"
        "EUR" -> "€"
        null, "" -> ""
        else -> "${value.currency} "
    }
    return "$symbol${"%.0f".format(main)}"
}
