package com.macebox.crate.ui.screen.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.macebox.crate.domain.model.Share
import com.macebox.crate.domain.model.UserSearchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    target: ShareTarget,
    resourceId: Long,
    onDismiss: () -> Unit,
    viewModel: ShareSheetViewModel = hiltViewModel(key = "share-${target.name}-$resourceId"),
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(target, resourceId) {
        viewModel.bind(target, resourceId)
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
                text =
                    when (target) {
                        ShareTarget.Album -> "Share item"
                        ShareTarget.Playlist -> "Share playlist"
                    },
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Find a Nextcloud user") },
                supportingText = { Text("Type at least two characters.") },
            )

            state.errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (state.isSearching) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.results.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                ) {
                    items(state.results, key = { it.userId }) { user ->
                        UserRow(user = user, onClick = { viewModel.share(user.userId) })
                        HorizontalDivider()
                    }
                }
            }

            Text(
                text = "Already shared with",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp, max = 240.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.isLoadingShares -> CircularProgressIndicator()
                    state.existingShares.isEmpty() ->
                        Text(
                            text = "No one yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    else ->
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(state.existingShares, key = { it.id }) { share ->
                                ExistingShareRow(
                                    share = share,
                                    onRevoke = { viewModel.revoke(share.id) },
                                    enabled = !state.isWorking,
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
private fun UserRow(
    user: UserSearchResult,
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
            text = user.displayName ?: user.userId,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!user.displayName.isNullOrBlank() && user.displayName != user.userId) {
            Text(
                text = user.userId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExistingShareRow(
    share: Share,
    onRevoke: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = share.targetDisplayName ?: share.targetUserId,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!share.targetDisplayName.isNullOrBlank() && share.targetDisplayName != share.targetUserId) {
                Text(
                    text = share.targetUserId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onRevoke, enabled = enabled) {
            Icon(Icons.Filled.Close, contentDescription = "Revoke share")
        }
    }
}
