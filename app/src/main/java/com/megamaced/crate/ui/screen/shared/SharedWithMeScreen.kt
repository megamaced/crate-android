package com.megamaced.crate.ui.screen.shared

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.megamaced.crate.domain.model.Playlist
import com.megamaced.crate.domain.model.SharedCategorySummary
import com.megamaced.crate.ui.components.CategoryBadge
import com.megamaced.crate.ui.components.EmptyState
import com.megamaced.crate.ui.components.LoadingState

/**
 * "Shared with me" landing — a home-style overview. Each category shared with
 * the viewer (whether via a whole-library, category, single-item or playlist
 * share) gets a tile that opens a full [SharedCategoryScreen] subpage; shared
 * playlists are listed below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedWithMeScreen(
    onBack: () -> Unit,
    onOpenCategory: (categoryApiValue: String) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SharedWithMeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Shared with me") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading -> LoadingState()
                state.isEmpty ->
                    EmptyState(
                        title = "Nothing shared with you",
                        subtitle = "Ask a Nextcloud user to share something from their collection.",
                    )
                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        if (state.categories.isNotEmpty()) {
                            item(key = "hdr-categories") { SectionHeader("Categories") }
                            items(state.categories, key = { "cat-${it.category.apiValue}" }) { summary ->
                                CategoryTile(
                                    summary = summary,
                                    onClick = { onOpenCategory(summary.category.apiValue) },
                                )
                                HorizontalDivider()
                            }
                        }
                        if (state.playlists.isNotEmpty()) {
                            item(key = "hdr-playlists") { SectionHeader("Playlists") }
                            items(state.playlists, key = { "playlist-${it.id}" }) { playlist ->
                                SharedPlaylistRow(
                                    playlist = playlist,
                                    onClick = { onPlaylistClick(playlist.id) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun CategoryTile(
    summary: SharedCategorySummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(category = summary.category)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = summary.label,
                style = MaterialTheme.typography.titleMedium,
            )
            val owners = summary.owners
            val ownerText =
                when (owners.size) {
                    0 -> null
                    1 -> "from ${owners.first()}"
                    else -> "from ${owners.size} people"
                }
            val countText = "${summary.count} item${if (summary.count == 1) "" else "s"}"
            Text(
                text = listOfNotNull(countText, ownerText).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SharedPlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub =
                listOfNotNull(
                    "${playlist.itemCount} item${if (playlist.itemCount == 1) "" else "s"}",
                    playlist.userId,
                ).joinToString(" · ")
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
