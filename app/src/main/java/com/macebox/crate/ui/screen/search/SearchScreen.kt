package com.macebox.crate.ui.screen.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.macebox.crate.domain.model.Category
import com.macebox.crate.domain.model.MediaItem
import com.macebox.crate.ui.components.ArtworkImage
import com.macebox.crate.ui.components.CategoryBadge
import com.macebox.crate.ui.screen.addedit.ExternalSearchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onItemClick: (Long) -> Unit,
    onAddFromExternal: (Category, ExternalSearchResult) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.externalError) {
        state.externalError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissExternalError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Search") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            QueryBar(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onSubmit = {
                    if (state.tab == SearchTab.External) viewModel.runExternalSearch()
                },
            )
            TabRow(selected = state.tab, onSelect = viewModel::selectTab)
            when (state.tab) {
                SearchTab.Collection ->
                    CollectionResults(
                        query = state.query,
                        results = state.collectionResults,
                        onItemClick = onItemClick,
                    )
                SearchTab.External ->
                    ExternalResults(
                        state = state,
                        onCategorySelected = viewModel::selectCategory,
                        onSearch = viewModel::runExternalSearch,
                        onPick = { result -> onAddFromExternal(state.externalCategory, result) },
                    )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueryBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        label = { Text("Search") },
        trailingIcon = {
            IconButton(onClick = onSubmit) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
        },
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabRow(
    selected: SearchTab,
    onSelect: (SearchTab) -> Unit,
) {
    val options = remember { SearchTab.entries }
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(if (option == SearchTab.Collection) "My collection" else "External")
            }
        }
    }
}

@Composable
private fun CollectionResults(
    query: String,
    results: List<MediaItem>,
    onItemClick: (Long) -> Unit,
) {
    when {
        query.isBlank() ->
            Hint("Type to filter your local collection.")
        results.isEmpty() ->
            Hint("No matches in your collection.")
        else ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(results, key = { it.id }) { item ->
                    CollectionRow(item = item, onClick = { onItemClick(item.id) })
                    HorizontalDivider()
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExternalResults(
    state: SearchUiState,
    onCategorySelected: (Category) -> Unit,
    onSearch: () -> Unit,
    onPick: (ExternalSearchResult) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ProviderPicker(
            selected = state.externalCategory,
            onSelected = onCategorySelected,
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isExternalLoading ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                state.query.isBlank() ->
                    Hint("Type a query then tap search to look up ${providerName(state.externalCategory)}.")
                state.externalResults.isEmpty() && state.externalHasSearched ->
                    Hint("No results from ${providerName(state.externalCategory)}.")
                state.externalResults.isEmpty() ->
                    Hint("Tap search to look up ${providerName(state.externalCategory)}.", showSearch = true, onSearch = onSearch)
                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(state.externalResults, key = { it.identityKey() }) { result ->
                            ExternalRow(result = result, onClick = { onPick(result) })
                            HorizontalDivider()
                        }
                    }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderPicker(
    selected: Category,
    onSelected: (Category) -> Unit,
) {
    val options = remember { Category.entries }
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        options.forEachIndexed { index, category ->
            SegmentedButton(
                selected = category == selected,
                onClick = { onSelected(category) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(category.label)
            }
        }
    }
}

@Composable
private fun CollectionRow(
    item: MediaItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
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
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub =
                listOfNotNull(
                    item.artist?.takeIf { it.isNotBlank() },
                    item.format?.takeIf { it.isNotBlank() },
                    item.year?.toString(),
                ).joinToString(" · ").ifBlank { null }
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        CategoryBadge(category = item.category)
    }
}

@Composable
private fun ExternalRow(
    result: ExternalSearchResult,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = result.title.ifBlank { "(untitled)" },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val sub =
            result.subtitle ?: listOfNotNull(
                result.artist?.takeIf { it.isNotBlank() },
                result.year?.toString(),
                result.format?.takeIf { it.isNotBlank() },
                result.label?.takeIf { it.isNotBlank() },
            ).joinToString(" · ").ifBlank { null }
        if (!sub.isNullOrBlank()) {
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Hint(
    text: String,
    showSearch: Boolean = false,
    onSearch: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (showSearch && onSearch != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onSearch),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ExternalSearchResult.identityKey(): String =
    discogsId
        ?: barcode
        ?: "$title|${artist.orEmpty()}|${year ?: 0}"

private fun providerName(category: Category): String =
    when (category) {
        Category.Music -> "Discogs"
        Category.Films -> "TMDB"
        Category.Books -> "Open Library"
        Category.Games -> "RAWG"
        Category.Comics -> "ComicVine"
    }
