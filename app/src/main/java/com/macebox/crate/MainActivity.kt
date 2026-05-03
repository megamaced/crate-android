package com.macebox.crate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.macebox.crate.ui.navigation.CrateScaffold
import com.macebox.crate.ui.theme.CrateTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CrateTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                CrateScaffold(widthSizeClass = windowSizeClass.widthSizeClass)
            }
        }
    }
}
