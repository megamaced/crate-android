package com.megamaced.crate.ui.screen.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.megamaced.crate.R
import com.megamaced.crate.data.prefs.CollectionViewMode
import com.megamaced.crate.domain.model.SortField
import com.megamaced.crate.ui.components.CollectionFilterBar
import com.megamaced.crate.ui.components.SortMenuButton
import com.megamaced.crate.ui.network.LocalIsOnline
import com.megamaced.crate.ui.screen.collection.CollectionGrid
import com.megamaced.crate.ui.screen.collection.CollectionList
import com.megamaced.crate.ui.screen.collection.ViewModeToggle
import com.megamaced.crate.util.resolve

/**
 * A single shared category, rendered as close as possible to a primary
 * [com.megamaced.crate.ui.screen.collection.CollectionScreen]: the same format
 * chips, sort menu (with headers), card/list toggle and — when the share is
 * read/write — an Add affordance into the owner's collection. Reuses the
 * collection renderers so the two views stay visually identical.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedCategoryScreen(
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    // owner uid + category apiValue — the target collection for a new item when
    // the category is shared read/write.
    onAddItem: (owner: String, category: String) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    viewModel: SharedCategoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isOnline = LocalIsOnline.current

    val errorText = uiState.error?.resolve()
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
                title = {
                    Column {
                        Text(stringResource(uiState.labelRes))
                        uiState.ownerCaption?.let { caption ->
                            Text(
                                text = caption.resolve(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
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
            val owner = uiState.writeOwner
            if (isOnline && owner != null) {
                FloatingActionButton(onClick = { onAddItem(owner, uiState.category.apiValue) }) {
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
