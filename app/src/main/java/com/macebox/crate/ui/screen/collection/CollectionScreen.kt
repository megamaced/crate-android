package com.macebox.crate.ui.screen.collection

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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.macebox.crate.data.prefs.CollectionViewMode
import com.macebox.crate.domain.model.Category
import com.macebox.crate.domain.model.MarketValue
import com.macebox.crate.domain.model.MediaItem
import com.macebox.crate.ui.components.ArtworkImage
import com.macebox.crate.ui.components.EmptyState
import com.macebox.crate.ui.components.FormatFilterChips
import com.macebox.crate.ui.components.MediaCard
import com.macebox.crate.ui.components.SortMenuButton
import com.macebox.crate.ui.navigation.CategorySegmentedRow
import com.macebox.crate.ui.network.LocalIsOnline

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

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(uiState.category.label) },
                actions = {
                    ViewModeToggle(
                        current = uiState.viewMode,
                        onSelected = viewModel::setViewMode,
                    )
                    SortMenuButton(
                        category = uiState.category,
                        selected = uiState.sort,
                        onSelected = viewModel::selectSort,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (isOnline) {
                FloatingActionButton(onClick = { onAddItem(uiState.category) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add item")
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
                CategorySegmentedRow(
                    selected = uiState.category,
                    onCategorySelected = viewModel::selectCategory,
                )
                FormatFilterChips(
                    formats = uiState.availableFormats,
                    totalCount = uiState.totalCount,
                    selected = uiState.selectedFormats,
                    onToggle = viewModel::toggleFormat,
                    onClear = viewModel::clearFormats,
                )
                when (uiState.viewMode) {
                    CollectionViewMode.Card -> CollectionGrid(
                        items = uiState.items,
                        onItemClick = onItemClick,
                        widthSizeClass = widthSizeClass,
                        modifier = Modifier.fillMaxSize(),
                    )
                    CollectionViewMode.List -> CollectionList(
                        items = uiState.items,
                        onItemClick = onItemClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewModeToggle(
    current: CollectionViewMode,
    onSelected: (CollectionViewMode) -> Unit,
) {
    val target = if (current == CollectionViewMode.Card) CollectionViewMode.List else CollectionViewMode.Card
    val (icon, label) = when (current) {
        CollectionViewMode.Card -> Icons.AutoMirrored.Outlined.ViewList to "Switch to list view"
        CollectionViewMode.List -> Icons.Outlined.GridView to "Switch to card view"
    }
    IconButton(onClick = { onSelected(target) }) {
        Icon(imageVector = icon, contentDescription = label)
    }
}

@Composable
private fun CollectionGrid(
    items: List<MediaItem>,
    onItemClick: (Long) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
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
        items(items, key = { it.id }) { item ->
            MediaCard(
                item = item,
                onClick = { onItemClick(item.id) },
            )
        }
    }
}

@Composable
private fun CollectionList(
    items: List<MediaItem>,
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        EmptyCollection(modifier)
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(items, key = { it.id }) { item ->
            CollectionListRow(item = item, onClick = { onItemClick(item.id) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun CollectionListRow(
    item: MediaItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val artist = item.artist?.takeIf { it.isNotBlank() }
            if (artist != null) {
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
        title = "Nothing here yet",
        subtitle = "Pull to refresh, or add an item from the + button on the next screen.",
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
