package com.megamaced.crate.ui.screen.scan

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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.megamaced.crate.R
import com.megamaced.crate.ui.screen.addedit.ExternalResultRow
import com.megamaced.crate.ui.screen.addedit.ExternalSearchResult
import com.megamaced.crate.ui.screen.addedit.listKey
import com.megamaced.crate.util.resolve

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
                title = { Text(stringResource(R.string.action_scan_barcode)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
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
                hasPermission -> {
                    CameraPreview(
                        paused = state.sheetOpen,
                        onBarcode = viewModel::onBarcodeDetected,
                    )
                }

                else -> {
                    PermissionPrompt(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
                }
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
    val statusText = stringResource(R.string.scan_status_hint)
    val barcodeView =
        remember(statusText) {
            DecoratedBarcodeView(context).apply {
                barcodeView.decoderFactory = DefaultDecoderFactory()
                // Continuous autofocus — without it, the preview comes up
                // blurry and the user has to leave & re-enter to trigger focus.
                barcodeView.cameraSettings.apply {
                    isAutoFocusEnabled = true
                    isContinuousFocusEnabled = true
                }
                setStatusText(statusText)
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
        onDispose { barcodeView.barcodeView.stopDecoding() }
    }

    // The camera follows the host lifecycle, not just composition: releasing it
    // only on removal leaves the device held (and the privacy indicator lit)
    // while the app is in the background, and other camera apps can't acquire
    // it. This effect owns the initial resume too.
    LifecycleResumeEffect(barcodeView) {
        barcodeView.resume()
        onPauseOrDispose { barcodeView.pause() }
    }

    // The candidate sheet pauses the preview while it is up. Skipped on the
    // first pass so it can't double-resume over the lifecycle effect above.
    var sheetHasOpened by remember { mutableStateOf(false) }
    LaunchedEffect(paused) {
        when {
            paused -> {
                sheetHasOpened = true
                barcodeView.pause()
            }

            sheetHasOpened -> {
                barcodeView.resume()
            }
        }
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
            text = stringResource(R.string.scan_permission_required),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onRequest, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.scan_grant_access))
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
                text = state.barcode
                    ?.let { stringResource(R.string.scan_barcode_value, it) }
                    ?: stringResource(R.string.scan_scanning),
                style = MaterialTheme.typography.titleMedium,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 480.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.isLooking -> {
                        CircularProgressIndicator()
                    }

                    state.errorMessage != null -> {
                        Text(
                            text = state.errorMessage.resolve(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    state.candidates.isNotEmpty() -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            itemsIndexed(
                                state.candidates,
                                key = { index, result -> result.listKey(index) },
                            ) { _, result ->
                                ExternalResultRow(result, onClick = { onPick(result) })
                                HorizontalDivider()
                            }
                        }
                    }

                    else -> {
                        Text(
                            text = stringResource(R.string.scan_no_candidates),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!state.isLooking && state.barcode != null) {
                OutlinedButton(
                    onClick = {
                        onPick(ExternalSearchResult(title = "", barcode = state.barcode))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.scan_use_raw_barcode))
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scan_keep_scanning))
            }
        }
    }
}
