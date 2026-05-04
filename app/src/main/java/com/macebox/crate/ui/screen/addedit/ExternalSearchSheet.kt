package com.macebox.crate.ui.screen.addedit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macebox.crate.domain.model.Category

data class ExternalSearchResult(
    val title: String,
    val artist: String? = null,
    val format: String? = null,
    val year: Int? = null,
    val barcode: String? = null,
    val label: String? = null,
    val country: String? = null,
    val discogsId: String? = null,
    val subtitle: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalSearchSheet(
    category: Category,
    onDismiss: () -> Unit,
    onPick: (ExternalSearchResult) -> Unit,
    viewModel: ExternalSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(category) {
        viewModel.setCategory(category)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Search ${providerName(category)}",
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search query") },
                trailingIcon = {
                    IconButton(onClick = viewModel::search) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                },
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 480.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator()
                    state.errorMessage != null ->
                        Text(
                            text = state.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    state.results.isEmpty() && state.hasSearched ->
                        Text(
                            text = "No results.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    state.results.isNotEmpty() ->
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            items(state.results, key = { it.identityKey() }) { result ->
                                ResultRow(
                                    result = result,
                                    onClick = {
                                        onPick(result)
                                        onDismiss()
                                    },
                                )
                                HorizontalDivider()
                            }
                        }
                    else ->
                        Text(
                            text = "Type a query and tap search.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    result: ExternalSearchResult,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = result.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val sub = result.subtitle ?: buildSubtitle(result)
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

private fun ExternalSearchResult.identityKey(): String =
    discogsId
        ?: barcode
        ?: "$title|${artist.orEmpty()}|${year ?: 0}"

private fun buildSubtitle(result: ExternalSearchResult): String? =
    listOfNotNull(
        result.artist?.takeIf { it.isNotBlank() },
        result.year?.toString(),
        result.format?.takeIf { it.isNotBlank() },
        result.label?.takeIf { it.isNotBlank() },
    ).joinToString(" · ").ifBlank { null }

private fun providerName(category: Category): String =
    when (category) {
        Category.Music -> "Discogs"
        Category.Films -> "TMDB"
        Category.Books -> "Open Library"
        Category.Games -> "RAWG"
        Category.Comics -> "ComicVine"
    }
