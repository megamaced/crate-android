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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.macebox.crate.ui.navigation.CategorySegmentedRow
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
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
            val result = runCatching {
                Json.decodeFromString<ExternalSearchResult>(json)
            }
            result.getOrNull()?.let(viewModel::applyExternalResult)
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

    val titleLabels = remember(state.category) { CategoryLabels.forCategory(state.category) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (if (state.isEditing) "Edit " else "Add ") + titleLabels.singularNoun,
                    )
                },
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
            CategorySegmentedRow(
                selected = state.category,
                onCategorySelected = viewModel::onCategoryChange,
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
                    Text(if (state.hasArtworkPreview) "Replace artwork" else "Pick artwork")
                }
                if (state.hasArtworkPreview) {
                    OutlinedButton(
                        onClick = viewModel::onRemoveArtwork,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Remove artwork")
                    }
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
            label = { Text(labels.title) },
            placeholder = { Text(labels.titlePlaceholder) },
            singleLine = true,
            isError = state.title.isBlank(),
        )
        OutlinedTextField(
            value = state.artist,
            onValueChange = viewModel::onArtistChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(labels.artist) },
            placeholder = { Text(labels.artistPlaceholder) },
            singleLine = true,
            isError = state.artist.isBlank(),
        )

        FormatField(
            label = labels.format,
            category = state.category,
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
                placeholder = { Text("e.g. 1973") },
                singleLine = true,
                isError = state.yearError,
                supportingText = if (state.yearError) {
                    { Text("Enter a year between 1800 and now.") }
                } else {
                    null
                },
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
                placeholder = { Text(labels.barcodePlaceholder) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
            )
        }

        if (state.category == Category.Books) {
            OutlinedButton(
                onClick = viewModel::lookupIsbn,
                enabled = state.barcode.isNotBlank() && !state.isLookingUpIsbn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isLookingUpIsbn) "Looking up…" else "Look up ISBN")
            }
        }

        OutlinedTextField(
            value = state.label,
            onValueChange = viewModel::onLabelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(labels.label) },
            placeholder = { Text(labels.labelPlaceholder) },
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

@Composable
private fun FormatField(
    label: String,
    category: Category,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = { Text("Select…") },
            singleLine = true,
            isError = value.isBlank(),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = if (value.isBlank()) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.outline
                },
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { sheetOpen = true },
        )
    }

    if (sheetOpen) {
        FormatPickerSheet(
            category = category,
            currentValue = value,
            onPick = {
                onValueChange(it)
                sheetOpen = false
            },
            onDismiss = { sheetOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatPickerSheet(
    category: Category,
    currentValue: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val groups = remember(category) { CategoryFormats.groupsFor(category) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            groups.forEach { group ->
                item(key = "header-${group.label}") {
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                items(group.formats, key = { "${group.label}-$it" }) { fmt ->
                    val selected = fmt.equals(currentValue, ignoreCase = true)
                    Text(
                        text = fmt + if (selected) "  ✓" else "",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(fmt) }
                            .padding(horizontal = 32.dp, vertical = 12.dp),
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
            !state.pendingArtworkUrl.isNullOrBlank() ->
                coil3.compose.AsyncImage(
                    model = state.pendingArtworkUrl,
                    contentDescription = "Selected artwork",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            state.isEditing && !state.removeArtwork && state.editingItemId != null ->
                ArtworkImage(
                    itemId = state.editingItemId,
                    contentDescription = state.title,
                    size = ArtworkSize.Thumb,
                    updatedAt = state.itemUpdatedAt,
                    category = state.category,
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
    val title: String,
    val format: String,
    val barcode: String,
    val label: String,
    val providerName: String,
    val singularNoun: String,
    val artistPlaceholder: String,
    val titlePlaceholder: String,
    val labelPlaceholder: String,
    val barcodePlaceholder: String,
) {
    companion object {
        // Mirror of crate/src/utils/categoryFormats.js FIELD_CONFIG plus the
        // per-category placeholder/heading text from AddEditModal.vue.
        fun forCategory(category: Category): CategoryLabels =
            when (category) {
                Category.Music -> CategoryLabels(
                    artist = "Artist",
                    title = "Album / Title",
                    format = "Format",
                    barcode = "Barcode",
                    label = "Label",
                    providerName = "Discogs",
                    singularNoun = "album",
                    artistPlaceholder = "e.g. Pink Floyd",
                    titlePlaceholder = "e.g. The Dark Side of the Moon",
                    labelPlaceholder = "e.g. EMI",
                    barcodePlaceholder = "e.g. 5099902987521",
                )
                Category.Films -> CategoryLabels(
                    artist = "Director",
                    title = "Film Title",
                    format = "Format",
                    barcode = "Barcode",
                    label = "Studio",
                    providerName = "TMDB",
                    singularNoun = "film",
                    artistPlaceholder = "e.g. Christopher Nolan",
                    titlePlaceholder = "e.g. Inception",
                    labelPlaceholder = "e.g. Warner Bros.",
                    barcodePlaceholder = "",
                )
                Category.Books -> CategoryLabels(
                    artist = "Author",
                    title = "Title",
                    format = "Format",
                    barcode = "ISBN",
                    label = "Publisher",
                    providerName = "Open Library",
                    singularNoun = "book",
                    artistPlaceholder = "e.g. George Orwell",
                    titlePlaceholder = "e.g. 1984",
                    labelPlaceholder = "e.g. Penguin",
                    barcodePlaceholder = "e.g. 978-0451524935",
                )
                Category.Games -> CategoryLabels(
                    artist = "Developer",
                    title = "Game Title",
                    format = "Platform",
                    barcode = "Barcode",
                    label = "Publisher",
                    providerName = "RAWG",
                    singularNoun = "game",
                    artistPlaceholder = "e.g. Nintendo",
                    titlePlaceholder = "e.g. Super Mario Bros.",
                    labelPlaceholder = "e.g. Nintendo",
                    barcodePlaceholder = "",
                )
                Category.Comics -> CategoryLabels(
                    artist = "Writer",
                    title = "Series / Volume Title",
                    format = "Format",
                    barcode = "Barcode",
                    label = "Publisher",
                    providerName = "ComicVine",
                    singularNoun = "comic",
                    artistPlaceholder = "e.g. Alan Moore",
                    titlePlaceholder = "e.g. Watchmen",
                    labelPlaceholder = "e.g. DC Comics",
                    barcodePlaceholder = "",
                )
            }
    }
}
