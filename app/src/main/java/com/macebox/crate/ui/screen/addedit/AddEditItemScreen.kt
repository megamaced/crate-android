package com.macebox.crate.ui.screen.addedit

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.macebox.crate.domain.model.Category
import com.macebox.crate.domain.model.Status
import com.macebox.crate.ui.components.ArtworkImage
import com.macebox.crate.ui.components.ArtworkSize
import com.macebox.crate.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditItemScreen(
    onBack: () -> Unit,
    onScan: (String) -> Unit,
    modifier: Modifier = Modifier,
    scanResultJson: String? = null,
    onScanResultConsumed: () -> Unit = {},
    viewModel: AddEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var searchSheetOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(scanResultJson) {
        scanResultJson?.let { json ->
            runCatching { kotlinx.serialization.json.Json.decodeFromString<ExternalSearchResult>(json) }
                .getOrNull()
                ?.let(viewModel::applyExternalResult)
            onScanResultConsumed()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(state.savedItemId) {
        if (state.savedItemId != null) onBack()
    }

    val artworkPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { handlePickedUri(context, it, viewModel) }
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit item" else "Add item") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(if (state.isSaving) "Saving…" else "Save")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.initialLoading) {
            LoadingState(modifier = Modifier.padding(innerPadding))
        } else {
            FormContent(
                state = state,
                viewModel = viewModel,
                onPickArtwork = { artworkPicker.launch("image/*") },
                onOpenSearch = { searchSheetOpen = true },
                onScan = { onScan(state.category.apiValue) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            )
        }
    }

    if (searchSheetOpen) {
        ExternalSearchSheet(
            category = state.category,
            onDismiss = { searchSheetOpen = false },
            onPick = { result -> viewModel.applyExternalResult(result) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormContent(
    state: AddEditUiState,
    viewModel: AddEditViewModel,
    onPickArtwork: () -> Unit,
    onOpenSearch: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = remember(state.category) { CategoryLabels.forCategory(state.category) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!state.isEditing) {
            CategoryPicker(
                selected = state.category,
                onSelected = viewModel::onCategoryChange,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkPreview(
                state = state,
                onPick = onPickArtwork,
                modifier = Modifier.size(96.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenSearch, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Text(
                        text = "  Search ${labels.providerName}",
                    )
                }
                if (state.category == Category.Music || state.category == Category.Books) {
                    OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                        Text("Scan barcode")
                    }
                }
                OutlinedButton(onClick = onPickArtwork, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.pendingArtwork != null) "Replace artwork" else "Pick artwork")
                }
            }
        }

        StatusToggle(
            status = state.status,
            onStatusChange = viewModel::onStatusChange,
        )

        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Title") },
            singleLine = true,
            isError = state.title.isBlank(),
        )
        OutlinedTextField(
            value = state.artist,
            onValueChange = viewModel::onArtistChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(labels.artist) },
            singleLine = true,
            isError = state.artist.isBlank(),
        )

        FormatField(
            label = labels.format,
            suggestions = labels.formatSuggestions,
            value = state.format,
            onValueChange = viewModel::onFormatChange,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.year,
                onValueChange = viewModel::onYearChange,
                modifier = Modifier.weight(1f),
                label = { Text("Year") },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
            )
            OutlinedTextField(
                value = state.barcode,
                onValueChange = viewModel::onBarcodeChange,
                modifier = Modifier.weight(2f),
                label = { Text(labels.barcode) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
            )
        }

        OutlinedTextField(
            value = state.label,
            onValueChange = viewModel::onLabelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(labels.label) },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.country,
            onValueChange = viewModel::onCountryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Country") },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.notes,
            onValueChange = viewModel::onNotesChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Notes") },
            minLines = 3,
            maxLines = 8,
        )

        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-enrich on save",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Calls ${labels.providerName} after saving if a token is configured.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.autoEnrich,
                onCheckedChange = viewModel::onAutoEnrichChange,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPicker(
    selected: Category,
    onSelected: (Category) -> Unit,
) {
    val categories = remember { Category.entries }
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        categories.forEachIndexed { index, category ->
            SegmentedButton(
                selected = category == selected,
                onClick = { onSelected(category) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = categories.size),
            ) {
                Text(category.label)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusToggle(
    status: Status,
    onStatusChange: (Status) -> Unit,
) {
    val options = remember { listOf(Status.Owned, Status.Wanted) }
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == status,
                onClick = { onStatusChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(if (option == Status.Owned) "Owned" else "Wanted")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormatField(
    label: String,
    suggestions: List<String>,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            singleLine = true,
            isError = value.isBlank(),
        )
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { suggestion ->
                    FilterChip(
                        selected = suggestion.equals(value, ignoreCase = true),
                        onClick = { onValueChange(suggestion) },
                        label = { Text(suggestion) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtworkPreview(
    state: AddEditUiState,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.pendingArtwork != null ->
                coil3.compose.AsyncImage(
                    model = state.pendingArtwork.bytes,
                    contentDescription = "Selected artwork",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            state.isEditing && state.editingItemId != null ->
                ArtworkImage(
                    itemId = state.editingItemId,
                    contentDescription = state.title,
                    size = ArtworkSize.Thumb,
                    updatedAt = state.itemUpdatedAt,
                    modifier = Modifier.fillMaxSize(),
                )
            else ->
                Text(
                    text = "Tap to pick artwork",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}

private fun handlePickedUri(
    context: Context,
    uri: Uri,
    viewModel: AddEditViewModel,
) {
    val contentResolver = context.contentResolver
    val mime = contentResolver.getType(uri) ?: "image/*"
    contentResolver.openInputStream(uri)?.use { stream ->
        val bytes = stream.readBytes()
        if (bytes.isNotEmpty()) {
            viewModel.onArtworkPicked(bytes, mime)
        }
    }
}

private data class CategoryLabels(
    val artist: String,
    val format: String,
    val barcode: String,
    val label: String,
    val providerName: String,
    val formatSuggestions: List<String>,
) {
    companion object {
        fun forCategory(category: Category): CategoryLabels =
            when (category) {
                Category.Music ->
                    CategoryLabels(
                        artist = "Artist",
                        format = "Format",
                        barcode = "Barcode",
                        label = "Label",
                        providerName = "Discogs",
                        formatSuggestions = listOf("LP", "12\"", "7\"", "CD", "Cassette", "Digital"),
                    )
                Category.Films ->
                    CategoryLabels(
                        artist = "Director",
                        format = "Format",
                        barcode = "Barcode",
                        label = "Studio",
                        providerName = "TMDB",
                        formatSuggestions = listOf("Blu-ray", "4K UHD", "DVD", "VHS", "Digital"),
                    )
                Category.Books ->
                    CategoryLabels(
                        artist = "Author",
                        format = "Format",
                        barcode = "ISBN",
                        label = "Publisher",
                        providerName = "Open Library",
                        formatSuggestions = listOf("Hardback", "Paperback", "eBook", "Audiobook"),
                    )
                Category.Games ->
                    CategoryLabels(
                        artist = "Developer",
                        format = "Platform",
                        barcode = "Barcode",
                        label = "Publisher",
                        providerName = "RAWG",
                        formatSuggestions = listOf("PC", "PS5", "PS4", "Xbox", "Switch", "Cartridge"),
                    )
                Category.Comics ->
                    CategoryLabels(
                        artist = "Writer",
                        format = "Format",
                        barcode = "Barcode",
                        label = "Publisher",
                        providerName = "ComicVine",
                        formatSuggestions = listOf("Issue", "TPB", "Hardcover", "Omnibus", "Digital"),
                    )
            }
    }
}
