package com.macebox.crate.ui.screen.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macebox.crate.domain.model.Playlist
import com.macebox.crate.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistListScreen(
    onPlaylistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var createOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Playlists") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { createOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New playlist")
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.playlists.isEmpty() && !state.isRefreshing) {
                EmptyState(
                    title = "No playlists yet",
                    subtitle = "Tap + to create your first playlist.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(state.playlists, key = { it.id }) { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist.id) },
                            onRename = { renameTarget = playlist },
                            onDelete = { deleteTarget = playlist },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (createOpen) {
        NameDialog(
            title = "New playlist",
            initialValue = "",
            confirmLabel = "Create",
            onDismiss = { createOpen = false },
            onConfirm = { name ->
                createOpen = false
                viewModel.create(name)
            },
        )
    }

    renameTarget?.let { target ->
        NameDialog(
            title = "Rename playlist",
            initialValue = target.name,
            confirmLabel = "Save",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                viewModel.rename(target.id, name)
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("Delete \"${target.name}\"?") },
            text = { Text("This removes the playlist from the server. Tracks themselves are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = target.id
                    deleteTarget = null
                    viewModel.delete(id)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.items.size} item${if (playlist.items.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRename) {
            Icon(Icons.Filled.Edit, contentDescription = "Rename")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete")
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.trim().isNotEmpty(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
