package com.macebox.crate.ui.screen.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macebox.crate.domain.model.Category
import com.macebox.crate.domain.model.MediaItem
import com.macebox.crate.ui.components.ArtworkImage
import com.macebox.crate.ui.components.ArtworkSize
import com.macebox.crate.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onBack()
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(uiState.item?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    val item = uiState.item
                    if (item != null) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More",
                            )
                        }
                        DetailMenu(
                            item = item,
                            expanded = menuExpanded,
                            onDismiss = { menuExpanded = false },
                            onEnrich = {
                                menuExpanded = false
                                viewModel.enrich()
                            },
                            onStrip = {
                                menuExpanded = false
                                viewModel.stripEnrichment()
                            },
                            onFetchMarketValue = {
                                menuExpanded = false
                                viewModel.fetchMarketValue()
                            },
                            onDelete = {
                                menuExpanded = false
                                confirmDelete = true
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val item = uiState.item
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(innerPadding))
            item == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Item not found", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> ItemDetailContent(
                item = item,
                activeAction = uiState.activeAction,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("Delete this item?") },
            text = { Text("This removes it from your collection on the server. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DetailMenu(
    item: MediaItem,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEnrich: () -> Unit,
    onStrip: () -> Unit,
    onFetchMarketValue: () -> Unit,
    onDelete: () -> Unit,
) {
    val isEnriched = item.discogsId != null || item.tracklist.isNotEmpty() || item.artistBio != null
    val supportsMarketValue = item.category == Category.Music ||
        item.category == Category.Games ||
        item.category == Category.Comics

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (isEnriched) "Re-enrich" else "Enrich") },
            onClick = onEnrich,
        )
        if (isEnriched) {
            DropdownMenuItem(
                text = { Text("Remove enrichment") },
                onClick = onStrip,
            )
        }
        if (supportsMarketValue) {
            DropdownMenuItem(
                text = { Text("Fetch market rate") },
                onClick = onFetchMarketValue,
            )
        }
        DropdownMenuItem(
            text = { Text("Delete") },
            onClick = onDelete,
        )
    }
}

@Composable
private fun ItemDetailContent(
    item: MediaItem,
    activeAction: DetailAction?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        Box {
            ArtworkImage(
                itemId = item.id,
                contentDescription = item.title,
                size = ArtworkSize.Full,
                updatedAt = item.updatedAt,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            if (activeAction != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall,
            )
            item.artist?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ChipRow(item)

            CategorySpecificFacts(item)

            if (item.marketValue.isPresent) {
                MarketValueCard(item)
            }

            if (item.tracklist.isNotEmpty()) {
                SectionHeader("Tracklist")
                item.tracklist.forEach { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        track.position?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = track.title.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        track.duration?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            item.artistBio?.takeIf { it.isNotBlank() }?.let { bio ->
                SectionHeader(
                    when (item.category) {
                        Category.Films -> "About the director"
                        Category.Books -> "About the author"
                        Category.Games -> "About the developer"
                        Category.Comics -> "About the publisher"
                        Category.Music -> "About the artist"
                    },
                )
                Text(text = bio, style = MaterialTheme.typography.bodyMedium)
            }

            item.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                SectionHeader("Notes")
                Text(text = notes, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ChipRow(item: MediaItem) {
    val chips = buildList {
        item.format?.takeIf { it.isNotBlank() }?.let { add(it) }
        item.year?.let { add(it.toString()) }
        item.label?.takeIf { it.isNotBlank() }?.let { add(it) }
        item.country?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    if (chips.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        chips.forEach { value ->
            AssistChip(
                onClick = {},
                label = { Text(value) },
                shape = AssistChipDefaults.shape,
            )
        }
    }
}

@Composable
private fun CategorySpecificFacts(item: MediaItem) {
    val facts = buildList {
        item.barcode?.takeIf { it.isNotBlank() }?.let { add("Barcode" to it) }
        item.genres?.takeIf { it.isNotBlank() }?.let { add("Genres" to it) }
        item.pressingNotes?.takeIf { it.isNotBlank() }?.let { add("Pressing notes" to it) }
    }
    if (facts.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        facts.forEach { (label, value) ->
            FactRow(label = label, value = value)
        }
    }
}

@Composable
private fun FactRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MarketValueCard(item: MediaItem) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Market value",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            val main = item.marketValue.main
            if (main != null) {
                Text(
                    text = formatMoney(main, item.marketValue.currency),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            val subValues = listOfNotNull(
                item.marketValue.loose?.let { "Loose ${formatMoney(it, item.marketValue.currency)}" },
                item.marketValue.new?.let { "New ${formatMoney(it, item.marketValue.currency)}" },
            )
            if (subValues.isNotEmpty()) {
                Text(
                    text = subValues.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            item.marketValue.fetchedAt?.let {
                Text(
                    text = "Fetched $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(0.dp)),
    )
}

private fun formatMoney(
    value: Double,
    currency: String?,
): String {
    val symbol = when (currency?.uppercase()) {
        "USD" -> "$"
        "GBP" -> "£"
        "EUR" -> "€"
        "JPY" -> "¥"
        null, "" -> ""
        else -> "$currency "
    }
    return "$symbol%.2f".format(value)
}
