package com.megamaced.crate.ui.components

import android.Manifest
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.megamaced.crate.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 37)
class LocalNetworkAccessTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val permissionContext = PermissionContext()
    private val registry = PermissionRegistry()
    private val lifecycleOwner =
        object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
    private var accessGrants = 0

    @Test
    fun grantingAccessNotifiesOnceAcrossResume() {
        showAccessControl()
        compose.onNodeWithText(context.getString(R.string.local_network_allow)).performClick()
        compose.runOnIdle { registry.respond(granted = true) }
        compose.onNodeWithText(context.getString(R.string.local_network_allow)).assertDoesNotExist()

        resumeAgain()
        compose.runOnIdle { assertEquals(1, accessGrants) }
    }

    @Test
    fun denialOffersSettingsAndGrantIsDetectedOnResume() {
        showAccessControl()
        compose.onNodeWithText(context.getString(R.string.local_network_allow)).performClick()
        compose.runOnIdle { registry.respond(granted = false) }
        compose.onNodeWithText(context.getString(R.string.local_network_open_settings)).assertExists()
        compose.runOnIdle {
            assertEquals(0, accessGrants)
            permissionContext.granted = true
        }

        resumeAgain()
        compose.onNodeWithText(context.getString(R.string.local_network_open_settings)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, accessGrants) }
    }

    @Test
    fun requestActionCanBeDisabledDuringSignIn() {
        showAccessControl(enabled = false)
        compose.onNodeWithText(context.getString(R.string.local_network_allow)).assertIsNotEnabled()
    }

    private fun showAccessControl(enabled: Boolean = true) {
        val registryOwner =
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lifecycleOwner.lifecycle.currentState = Lifecycle.State.RESUMED
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides permissionContext,
                LocalActivityResultRegistryOwner provides registryOwner,
                LocalLifecycleOwner provides lifecycleOwner,
            ) {
                LocalNetworkAccess(enabled = enabled, onAccessGranted = { accessGrants++ })
            }
        }
    }

    private fun resumeAgain() {
        compose.runOnIdle {
            lifecycleOwner.lifecycle.currentState = Lifecycle.State.STARTED
            lifecycleOwner.lifecycle.currentState = Lifecycle.State.RESUMED
        }
    }

    private inner class PermissionContext : ContextWrapper(context) {
        var granted = false

        override fun checkPermission(
            permission: String,
            pid: Int,
            uid: Int,
        ): Int {
            if (permission != Manifest.permission.ACCESS_LOCAL_NETWORK) return super.checkPermission(permission, pid, uid)
            return if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        }
    }

    private inner class PermissionRegistry : ActivityResultRegistry() {
        private var launchedRequest: Int? = null

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            assertEquals(Manifest.permission.ACCESS_LOCAL_NETWORK, input)
            launchedRequest = requestCode
        }

        fun respond(granted: Boolean) {
            permissionContext.granted = granted
            dispatchResult(checkNotNull(launchedRequest), granted)
        }
    }
}
