package com.megamaced.crate.ui.screen.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.megamaced.crate.R
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.ui.components.ArtworkImage
import com.megamaced.crate.ui.components.CategoryBadge
import com.megamaced.crate.ui.components.EmptyState
import com.megamaced.crate.ui.components.LoadingState
import com.megamaced.crate.ui.screen.share.ShareSheet
import com.megamaced.crate.ui.screen.share.ShareTarget
import com.megamaced.crate.util.resolve

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }

    val errorText = state.errorMessage?.resolve()
    LaunchedEffect(errorText) {
        errorText?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(state.playlist?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    // Re-sharing is owner-only.
                    if (state.isOwner) {
                        IconButton(onClick = { shareOpen = true }, enabled = state.playlist != null) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                        }
                    }
                    // Rename is available to anyone who can write.
                    if (state.canWrite) {
                        IconButton(onClick = { renameOpen = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_rename))
                        }
                    }
                    // Deleting the playlist is owner-only.
                    if (state.isOwner) {
                        IconButton(onClick = { deleteOpen = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Adding tracks requires write access (own playlist or a
            // read/write share).
            if (state.playlist != null && state.canWrite) {
                FloatingActionButton(onClick = { addOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.playlist_add_items))
                }
            }
        },
    ) { innerPadding ->
        when {
            state.isLoading -> {
                LoadingState(modifier = Modifier.padding(innerPadding))
            }

            state.playlist == null -> {
                EmptyState(
                    title = stringResource(R.string.playlist_not_found),
                    modifier = Modifier.padding(innerPadding),
                )
            }

            state.playlist!!.items.isEmpty() -> {
                EmptyState(
                    title = stringResource(R.string.playlist_empty_items_title),
                    subtitle = stringResource(R.string.playlist_empty_items_subtitle),
                    modifier = Modifier.padding(innerPadding),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(state.playlist!!.items, key = { it.id }) { item ->
                        PlaylistItemRow(
                            item = item,
                            canWrite = state.canWrite,
                            onClick = { onItemClick(item.id) },
                            onRemove = { viewModel.removeItem(item.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (renameOpen) {
        RenameDialog(
            initialName = state.playlist?.name.orEmpty(),
            onDismiss = { renameOpen = false },
            onConfirm = { name ->
                renameOpen = false
                viewModel.rename(name)
            },
        )
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = {
                Text(stringResource(R.string.playlist_delete_title, state.playlist?.name.orEmpty()))
            },
            text = { Text(stringResource(R.string.playlist_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteOpen = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (addOpen) {
        AddItemSheet(
            candidates = state.candidates,
            onDismiss = { addOpen = false },
            onPick = { mediaItemId ->
                viewModel.addItem(mediaItemId)
            },
        )
    }

    if (shareOpen && state.playlist != null) {
        ShareSheet(
            target = ShareTarget.Playlist,
            resourceId = state.playlist!!.id,
            onDismiss = { shareOpen = false },
        )
    }
}

@Composable
private fun PlaylistItemRow(
    item: MediaItem,
    canWrite: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
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
        CategoryBadge(category = item.category, modifier = Modifier.padding(end = 4.dp))
        // Removing tracks requires write access.
        if (canWrite) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.playlist_remove_item))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemSheet(
    candidates: List<MediaItem>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered =
        remember(query, candidates) {
            if (query.isBlank()) {
                candidates
            } else {
                val q = query.trim().lowercase()
                candidates.filter {
                    it.title.lowercase().contains(q) ||
                        it.artist
                            .orEmpty()
                            .lowercase()
                            .contains(q)
                }
            }
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
                text = stringResource(R.string.playlist_add_to),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.playlist_filter_collection)) },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 480.dp),
            ) {
                if (filtered.isEmpty()) {
                    Text(
                        text = stringResource(
                            if (candidates.isEmpty()) {
                                R.string.playlist_collection_empty
                            } else {
                                R.string.playlist_no_matches
                            },
                        ),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(filtered, key = { it.id }) { item ->
                            CandidateRow(
                                item = item,
                                onClick = {
                                    onPick(item.id)
                                },
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
private fun CandidateRow(
    item: MediaItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
private fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.field_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.trim().isNotEmpty() && name != initialName,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
