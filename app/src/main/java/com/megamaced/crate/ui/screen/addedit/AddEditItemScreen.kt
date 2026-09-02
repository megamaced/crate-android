package com.megamaced.crate.ui.screen.addedit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.megamaced.crate.R
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.Status
import com.megamaced.crate.ui.components.ArtworkImage
import com.megamaced.crate.ui.components.ArtworkSize
import com.megamaced.crate.ui.components.LoadingState
import com.megamaced.crate.ui.components.PhotoImage
import com.megamaced.crate.ui.navigation.CategorySegmentedRow
import com.megamaced.crate.util.resolve
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

    LaunchedEffect(scanResultJson) {
        scanResultJson?.let { json ->
            val result = runCatching {
                Json.decodeFromString<ExternalSearchResult>(json)
            }
            result.getOrNull()?.let(viewModel::applyExternalResult)
            onScanResultConsumed()
        }
    }

    val errorText = state.errorMessage?.resolve()
    LaunchedEffect(errorText) {
        errorText?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    // The save outcome and the photo outcome arrive in one emission, so the
    // warning can be shown to completion before the screen pops — two separate
    // effects would race and navigation would win, hiding the warning.
    val photoFailedMessage = stringResource(R.string.photo_upload_failed)
    val imageTooLargeMessage = stringResource(R.string.add_edit_image_too_large)
    LaunchedEffect(state.savedItemId) {
        if (state.savedItemId != null) {
            if (state.imageTooLarge) snackbarHostState.showSnackbar(imageTooLargeMessage)
            if (state.photoUploadFailed) snackbarHostState.showSnackbar(photoFailedMessage)
            onBack()
        }
    }

    // PickVisualMedia rather than GetContent: it can only return images, and it
    // hands back a Uri the ViewModel reads off the main thread at upload time —
    // reading here would block the UI on a cloud-only photo's download.
    val imageRequest = remember { PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly) }
    val artworkPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { viewModel.onArtworkPicked(it.toString()) }
        }

    // Photo pickers — one per slot so the launcher knows which slot the pick
    // belongs to. Registering two launchers is cheaper than juggling a
    // "pendingSlot" state variable across recomposition.
    val photo1Picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { viewModel.onPhotoPicked(slot = 1, uri = it.toString()) }
        }
    val photo2Picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { viewModel.onPhotoPicked(slot = 2, uri = it.toString()) }
        }

    val titleLabels = remember(state.category) { CategoryLabels.forCategory(state.category) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (state.isEditing) R.string.add_edit_title_edit else R.string.add_edit_title_add,
                            stringResource(titleLabels.singularNounRes),
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(
                            stringResource(
                                if (state.isSaving) R.string.add_edit_saving else R.string.action_save,
                            ),
                        )
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
                onPickArtwork = { artworkPicker.launch(imageRequest) },
                onPickPhoto = { slot ->
                    when (slot) {
                        1 -> photo1Picker.launch(imageRequest)
                        2 -> photo2Picker.launch(imageRequest)
                    }
                },
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
    onPickPhoto: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = remember(state.category) { CategoryLabels.forCategory(state.category) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // When adding into someone else's shared collection, make it explicit
        // where the item is going.
        state.owner?.let { owner ->
            Text(
                text = stringResource(R.string.add_edit_adding_to_collection, owner),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Hide the category picker while editing, and when the category is
        // locked to a shared-category scope.
        if (!state.isEditing && !state.categoryLocked) {
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
                        text = stringResource(
                            R.string.add_edit_search_provider,
                            stringResource(labels.providerNameRes),
                        ),
                    )
                }
                if (state.category == Category.Music || state.category == Category.Books) {
                    OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_scan_barcode))
                    }
                }
                OutlinedButton(onClick = onPickArtwork, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (state.hasArtworkPreview) {
                                R.string.add_edit_replace_artwork
                            } else {
                                R.string.add_edit_pick_artwork
                            },
                        ),
                    )
                }
                if (state.hasArtworkPreview) {
                    OutlinedButton(
                        onClick = viewModel::onRemoveArtwork,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.add_edit_remove_artwork))
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
            label = { Text(stringResource(labels.titleRes)) },
            placeholder = { Text(stringResource(labels.titlePlaceholderRes)) },
            singleLine = true,
            isError = state.title.isBlank(),
        )
        OutlinedTextField(
            value = state.artist,
            onValueChange = viewModel::onArtistChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(labels.artistRes)) },
            placeholder = { Text(stringResource(labels.artistPlaceholderRes)) },
            singleLine = true,
            isError = state.artist.isBlank(),
        )

        FormatField(
            label = stringResource(labels.formatRes),
            category = state.category,
            value = state.format,
            unrecognised = state.formatUnrecognised,
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
                label = { Text(stringResource(R.string.field_year)) },
                placeholder = { Text(stringResource(R.string.hint_year)) },
                singleLine = true,
                isError = state.yearError,
                supportingText = if (state.yearError) {
                    { Text(stringResource(R.string.add_edit_year_error)) }
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
                label = { Text(stringResource(labels.barcodeRes)) },
                // Only two categories carry a useful example; the rest render an
                // empty placeholder rather than an empty resource.
                placeholder = { Text(labels.barcodePlaceholderRes?.let { stringResource(it) }.orEmpty()) },
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
                Text(
                    stringResource(
                        if (state.isLookingUpIsbn) {
                            R.string.add_edit_looking_up_isbn
                        } else {
                            R.string.add_edit_look_up_isbn
                        },
                    ),
                )
            }
        }

        OutlinedTextField(
            value = state.label,
            onValueChange = viewModel::onLabelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(labels.labelRes)) },
            placeholder = { Text(stringResource(labels.labelPlaceholderRes)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.country,
            onValueChange = viewModel::onCountryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_country)) },
            singleLine = true,
        )
        // Original price paid (any category)
        PurchasePriceRow(
            price = state.purchasePrice,
            currency = state.purchasePriceCurrency,
            currencyOptions = state.availableCurrencies,
            onPriceChange = viewModel::onPurchasePriceChange,
            onCurrencyChange = viewModel::onPurchasePriceCurrencyChange,
        )

        // Extra photo slots (disc shots, receipts, sleevenotes etc.). Each
        // slot tracks its own pending file + remove flag; uploads happen
        // after the main save resolves so the new item ID is known.
        PhotoSlotsRow(
            state = state,
            onPickPhoto = onPickPhoto,
            onRemovePhoto = viewModel::onRemovePhoto,
        )

        OutlinedTextField(
            value = state.notes,
            onValueChange = viewModel::onNotesChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_notes)) },
            minLines = 3,
            maxLines = 8,
        )
    }
}

@Composable
private fun PurchasePriceRow(
    price: String,
    currency: String,
    currencyOptions: List<String>,
    onPriceChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = price,
            onValueChange = onPriceChange,
            modifier = Modifier.weight(2f),
            label = { Text(stringResource(R.string.purchase_price_label)) },
            placeholder = { Text(stringResource(R.string.hint_purchase_price)) },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
        )
        CurrencyDropdown(
            current = currency,
            enabled = price.isNotBlank(),
            options = currencyOptions,
            onPick = onCurrencyChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CurrencyDropdown(
    current: String,
    enabled: Boolean,
    options: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.purchase_currency_label)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor =
                    if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(enabled = enabled) { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { code ->
                DropdownMenuItem(
                    text = { Text(code) },
                    onClick = {
                        onPick(code)
                        expanded = false
                    },
                )
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
                Text(stringResource(option.labelRes))
            }
        }
    }
}

@Composable
private fun FormatField(
    label: String,
    category: Category,
    value: String,
    // A provider can hand back a format Crate doesn't file under; flag it
    // rather than block the save, since the server accepts free text.
    unrecognised: Boolean,
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
            placeholder = { Text(stringResource(R.string.add_edit_format_select)) },
            singleLine = true,
            isError = value.isBlank(),
            supportingText = if (unrecognised) {
                { Text(stringResource(R.string.format_unrecognised, label)) }
            } else {
                null
            },
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
                item(key = "header-${group.labelRes}") {
                    Text(
                        text = stringResource(group.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                items(group.formats, key = { "${group.labelRes}-$it" }) { fmt ->
                    val selected = fmt.equals(currentValue, ignoreCase = true)
                    Text(
                        text = if (selected) stringResource(R.string.sort_option_selected, fmt) else fmt,
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
            state.pendingArtwork != null -> {
                coil3.compose.AsyncImage(
                    model = state.pendingArtwork.uri,
                    contentDescription = stringResource(R.string.add_edit_selected_artwork_a11y),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            !state.pendingArtworkUrl.isNullOrBlank() -> {
                coil3.compose.AsyncImage(
                    model = state.pendingArtworkUrl,
                    contentDescription = stringResource(R.string.add_edit_selected_artwork_a11y),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            state.isEditing && !state.removeArtwork && state.editingItemId != null -> {
                ArtworkImage(
                    itemId = state.editingItemId,
                    contentDescription = state.title,
                    size = ArtworkSize.Thumb,
                    updatedAt = state.itemUpdatedAt,
                    category = state.category,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                Text(
                    text = stringResource(R.string.add_edit_tap_to_pick_artwork),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PhotoSlotsRow(
    state: AddEditUiState,
    onPickPhoto: (Int) -> Unit,
    onRemovePhoto: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.additional_photos_section),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhotoSlotPreview(
                slot = 1,
                pendingUri = state.pendingPhoto1?.uri,
                hasExisting = state.hasPhoto1 && !state.removePhoto1,
                isEditing = state.isEditing,
                editingItemId = state.editingItemId,
                updatedAt = state.itemUpdatedAt,
                onPick = { onPickPhoto(1) },
                onRemove = { onRemovePhoto(1) },
                modifier = Modifier.weight(1f),
            )
            PhotoSlotPreview(
                slot = 2,
                pendingUri = state.pendingPhoto2?.uri,
                hasExisting = state.hasPhoto2 && !state.removePhoto2,
                isEditing = state.isEditing,
                editingItemId = state.editingItemId,
                updatedAt = state.itemUpdatedAt,
                onPick = { onPickPhoto(2) },
                onRemove = { onRemovePhoto(2) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PhotoSlotPreview(
    slot: Int,
    pendingUri: String?,
    hasExisting: Boolean,
    isEditing: Boolean,
    editingItemId: Long?,
    updatedAt: String?,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val hasPhoto = pendingUri != null || hasExisting
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .size(96.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            when {
                pendingUri != null -> {
                    coil3.compose.AsyncImage(
                        model = pendingUri,
                        contentDescription = stringResource(R.string.detail_photo_a11y, slot),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                hasExisting && isEditing && editingItemId != null -> {
                    PhotoImage(
                        itemId = editingItemId,
                        slot = slot,
                        contentDescription = stringResource(R.string.detail_photo_a11y, slot),
                        updatedAt = updatedAt,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    Text(
                        text = stringResource(R.string.photo_slot_label, slot),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (hasPhoto) R.string.action_replace else R.string.action_upload))
        }
        if (hasPhoto) {
            OutlinedButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_remove))
            }
        }
    }
}

/**
 * Field names, placeholders and headings for one category. [barcodePlaceholderRes]
 * is null where the category has no example worth showing.
 */
private data class CategoryLabels(
    @param:StringRes val artistRes: Int,
    @param:StringRes val titleRes: Int,
    @param:StringRes val formatRes: Int,
    @param:StringRes val barcodeRes: Int,
    @param:StringRes val labelRes: Int,
    @param:StringRes val providerNameRes: Int,
    @param:StringRes val singularNounRes: Int,
    @param:StringRes val artistPlaceholderRes: Int,
    @param:StringRes val titlePlaceholderRes: Int,
    @param:StringRes val labelPlaceholderRes: Int,
    @param:StringRes val barcodePlaceholderRes: Int?,
) {
    companion object {
        // Mirror of crate/src/utils/categoryFormats.js FIELD_CONFIG plus the
        // per-category placeholder/heading text from AddEditModal.vue.
        fun forCategory(category: Category): CategoryLabels =
            when (category) {
                Category.Music -> CategoryLabels(
                    artistRes = R.string.field_artist_music,
                    titleRes = R.string.field_title_music,
                    formatRes = R.string.field_format,
                    barcodeRes = R.string.field_barcode,
                    labelRes = R.string.field_label_music,
                    providerNameRes = R.string.provider_music,
                    singularNounRes = R.string.add_edit_noun_music,
                    artistPlaceholderRes = R.string.hint_artist_music,
                    titlePlaceholderRes = R.string.hint_title_music,
                    labelPlaceholderRes = R.string.hint_label_music,
                    barcodePlaceholderRes = R.string.hint_barcode_music,
                )

                Category.Films -> CategoryLabels(
                    artistRes = R.string.field_artist_films,
                    titleRes = R.string.field_title_films,
                    formatRes = R.string.field_format,
                    barcodeRes = R.string.field_barcode,
                    labelRes = R.string.field_label_films,
                    providerNameRes = R.string.provider_films,
                    singularNounRes = R.string.add_edit_noun_films,
                    artistPlaceholderRes = R.string.hint_artist_films,
                    titlePlaceholderRes = R.string.hint_title_films,
                    labelPlaceholderRes = R.string.hint_label_films,
                    barcodePlaceholderRes = null,
                )

                Category.Books -> CategoryLabels(
                    artistRes = R.string.field_artist_books,
                    titleRes = R.string.field_title_books,
                    formatRes = R.string.field_format,
                    barcodeRes = R.string.field_isbn,
                    labelRes = R.string.field_label_books,
                    providerNameRes = R.string.provider_books,
                    singularNounRes = R.string.add_edit_noun_books,
                    artistPlaceholderRes = R.string.hint_artist_books,
                    titlePlaceholderRes = R.string.hint_title_books,
                    labelPlaceholderRes = R.string.hint_label_books,
                    barcodePlaceholderRes = R.string.hint_barcode_books,
                )

                Category.Games -> CategoryLabels(
                    artistRes = R.string.field_artist_games,
                    titleRes = R.string.field_title_games,
                    formatRes = R.string.field_platform,
                    barcodeRes = R.string.field_barcode,
                    labelRes = R.string.field_label_games,
                    providerNameRes = R.string.provider_games,
                    singularNounRes = R.string.add_edit_noun_games,
                    artistPlaceholderRes = R.string.hint_artist_games,
                    titlePlaceholderRes = R.string.hint_title_games,
                    labelPlaceholderRes = R.string.hint_label_games,
                    barcodePlaceholderRes = null,
                )

                Category.Comics -> CategoryLabels(
                    artistRes = R.string.field_artist_comics,
                    titleRes = R.string.field_title_comics,
                    formatRes = R.string.field_format,
                    barcodeRes = R.string.field_barcode,
                    labelRes = R.string.field_label_comics,
                    providerNameRes = R.string.provider_comics,
                    singularNounRes = R.string.add_edit_noun_comics,
                    artistPlaceholderRes = R.string.hint_artist_comics,
                    titlePlaceholderRes = R.string.hint_title_comics,
                    labelPlaceholderRes = R.string.hint_label_comics,
                    barcodePlaceholderRes = null,
                )
            }
    }
}
