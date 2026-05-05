package com.macebox.crate.ui.screen.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.macebox.crate.domain.model.Category
import com.macebox.crate.domain.model.MediaItem
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
                    SortMenuButton(
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
                    selected = uiState.selectedFormats,
                    onToggle = viewModel::toggleFormat,
                )
                CollectionGrid(
                    items = uiState.items,
                    onItemClick = onItemClick,
                    widthSizeClass = widthSizeClass,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
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
        EmptyState(
            title = "Nothing here yet",
            subtitle = "Pull to refresh, or add an item from the + button on the next screen.",
            modifier = modifier,
        )
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
