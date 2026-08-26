package com.megamaced.crate.ui.screen.share

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.megamaced.crate.R
import com.megamaced.crate.domain.model.Share
import com.megamaced.crate.domain.model.UserSearchResult
import com.megamaced.crate.util.resolve

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    target: ShareTarget,
    resourceId: Long = 0,
    category: String = "",
    onDismiss: () -> Unit,
    viewModel: ShareSheetViewModel = hiltViewModel(key = "share-${target.name}-$resourceId-$category"),
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(target, resourceId, category) {
        viewModel.bind(target, resourceId, category)
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
                        ShareTarget.Album -> stringResource(R.string.share_item_title)

                        ShareTarget.Playlist -> stringResource(R.string.share_playlist_title)

                        ShareTarget.Library -> stringResource(R.string.share_library_title)

                        ShareTarget.Category -> stringResource(
                            R.string.share_category_title,
                            category.replaceFirstChar { it.uppercase() },
                        )
                    },
                style = MaterialTheme.typography.titleLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.share_allow_editing),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(
                            if (state.grantCanWrite) {
                                R.string.share_permission_write
                            } else {
                                R.string.share_permission_read
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.grantCanWrite,
                    onCheckedChange = viewModel::onPermissionChange,
                    enabled = !state.isWorking,
                )
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.share_find_user_label)) },
                supportingText = { Text(stringResource(R.string.share_find_user_hint)) },
            )

            state.errorMessage?.let { msg ->
                Text(
                    text = msg.resolve(),
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
                        UserRow(
                            user = user,
                            enabled = !state.isWorking,
                            onClick = { viewModel.share(user.userId) },
                        )
                        HorizontalDivider()
                    }
                }
            }

            Text(
                text = stringResource(R.string.share_already_shared),
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
                    state.isLoadingShares -> {
                        CircularProgressIndicator()
                    }

                    state.existingShares.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.share_no_one_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {
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
}

@Composable
private fun UserRow(
    user: UserSearchResult,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
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
            Text(
                text = stringResource(
                    if (share.canWrite) R.string.share_can_edit else R.string.share_read_only,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRevoke, enabled = enabled) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.share_revoke))
        }
    }
}
