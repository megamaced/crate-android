package com.megamaced.crate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.megamaced.crate.data.auth.SessionManager
import com.megamaced.crate.data.prefs.ThemeMode
import com.megamaced.crate.data.prefs.UserPreferences
import com.megamaced.crate.ui.navigation.CrateScaffold
import com.megamaced.crate.ui.theme.CrateTheme
import com.megamaced.crate.util.NetworkMonitor
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
