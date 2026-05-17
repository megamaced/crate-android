package com.macebox.crate.ui.screen.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.macebox.crate.ui.screen.addedit.ExternalSearchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScanScreen(
    onBack: () -> Unit,
    onResultPicked: (ExternalSearchResult) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BarcodeScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPermission = granted
        }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Scan barcode") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                hasPermission ->
                    CameraPreview(
                        paused = state.sheetOpen,
                        onBarcode = viewModel::onBarcodeDetected,
                    )
                else -> PermissionPrompt(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
            }
        }
    }

    if (state.sheetOpen) {
        CandidateSheet(
            state = state,
            onDismiss = viewModel::dismissSheet,
            onPick = { result ->
                viewModel.dismissSheet()
                onResultPicked(result)
            },
        )
    }
}

@Composable
private fun CameraPreview(
    paused: Boolean,
    onBarcode: (String) -> Unit,
) {
    val context = LocalContext.current
    val barcodeView =
        remember {
            DecoratedBarcodeView(context).apply {
                barcodeView.decoderFactory = DefaultDecoderFactory()
                // Continuous autofocus — without it, the preview comes up
                // blurry and the user has to leave & re-enter to trigger focus.
                barcodeView.cameraSettings.apply {
                    isAutoFocusEnabled = true
                    isContinuousFocusEnabled = true
                }
                setStatusText("Point at a barcode")
            }
        }

    DisposableEffect(barcodeView) {
        val callback =
            object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult) {
                    onBarcode(result.text)
                }

                override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) = Unit
            }
        barcodeView.decodeContinuous(callback)
        barcodeView.resume()
        onDispose { barcodeView.pause() }
    }

    LaunchedEffect(paused) {
        if (paused) barcodeView.pause() else barcodeView.resume()
    }

    AndroidView(
        factory = { barcodeView },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Camera permission is needed to scan barcodes.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onRequest, modifier = Modifier.padding(top = 16.dp)) {
            Text("Grant access")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CandidateSheet(
    state: BarcodeScanUiState,
    onDismiss: () -> Unit,
    onPick: (ExternalSearchResult) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                text = state.barcode?.let { "Barcode: $it" } ?: "Scanning…",
                style = MaterialTheme.typography.titleMedium,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 480.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.isLooking -> CircularProgressIndicator()
                    state.errorMessage != null ->
                        Text(
                            text = state.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    state.candidates.isNotEmpty() ->
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            items(state.candidates, key = { it.identityKey() }) { result ->
                                CandidateRow(result, onClick = { onPick(result) })
                                HorizontalDivider()
                            }
                        }
                    else ->
                        Text(
                            text = "No matches. Use the raw barcode and fill the form manually.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
            }
            if (!state.isLooking && state.barcode != null) {
                OutlinedButton(
                    onClick = {
                        onPick(ExternalSearchResult(title = "", barcode = state.barcode))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Use raw barcode")
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Keep scanning")
            }
        }
    }
}

@Composable
private fun CandidateRow(
    result: ExternalSearchResult,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TextButton(onClick = onClick) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = result.title.ifBlank { "(untitled)" },
                    style = MaterialTheme.typography.titleSmall,
                )
                val sub =
                    listOfNotNull(
                        result.artist?.takeIf { it.isNotBlank() },
                        result.year?.toString(),
                        result.format?.takeIf { it.isNotBlank() },
                        result.label?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ").ifBlank { null }
                if (sub != null) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun ExternalSearchResult.identityKey(): String =
    discogsId
        ?: barcode
        ?: "$title|${artist.orEmpty()}|${year ?: 0}"
