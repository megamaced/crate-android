package com.macebox.crate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.macebox.crate.data.auth.SessionManager
import com.macebox.crate.data.prefs.ThemeMode
import com.macebox.crate.data.prefs.UserPreferences
import com.macebox.crate.ui.navigation.CrateScaffold
import com.macebox.crate.ui.theme.CrateTheme
import com.macebox.crate.util.NetworkMonitor
import com.macebox.crate.util.UpdateChecker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var updateChecker: UpdateChecker

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { updateChecker.checkOnStartup() }
        setContent {
            val prefs by userPreferences.flow.collectAsState(initial = null)
            val systemDark = isSystemInDarkTheme()
            val darkTheme =
                when (prefs?.themeMode ?: ThemeMode.System) {
                    ThemeMode.System -> systemDark
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }
            CrateTheme(darkTheme = darkTheme) {
                val windowSizeClass = calculateWindowSizeClass(this)
                CrateScaffold(
                    widthSizeClass = windowSizeClass.widthSizeClass,
                    sessionManager = sessionManager,
                    networkMonitor = networkMonitor,
                )
            }
        }
    }
}
