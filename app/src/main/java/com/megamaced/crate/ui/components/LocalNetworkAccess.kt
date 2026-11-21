package com.megamaced.crate.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.megamaced.crate.R

@Composable
internal fun LocalNetworkAccess(
    enabled: Boolean = true,
    onAccessGranted: () -> Unit = {},
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return

    val context = LocalContext.current
    val permission = Manifest.permission.ACCESS_LOCAL_NETWORK

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var granted by rememberSaveable { mutableStateOf(hasPermission()) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    fun updatePermission(allowed: Boolean) {
        val newlyGranted = allowed && !granted
        granted = allowed
        if (allowed) showSettings = false
        if (newlyGranted) onAccessGranted()
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        updatePermission(allowed)
        // The request callback also returns false when the dialog is dismissed.
        showSettings = !allowed
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        updatePermission(hasPermission())
    }

    if (granted) return

    // The same hostname can resolve to a local address on the user's Wi-Fi.
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.local_network_access_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { launcher.launch(permission) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.local_network_allow))
        }
        if (showSettings) {
            Text(
                text = stringResource(R.string.local_network_settings_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    val uri = "package:${context.packageName}".toUri()
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.local_network_open_settings))
            }
        }
    }
}
