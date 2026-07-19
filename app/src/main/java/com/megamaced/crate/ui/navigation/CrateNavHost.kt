package com.megamaced.crate.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.megamaced.crate.ui.screen.addedit.AddEditItemScreen
import com.megamaced.crate.ui.screen.addedit.ExternalSearchResult
import com.megamaced.crate.ui.screen.addedit.SCAN_RESULT_KEY
import com.megamaced.crate.ui.screen.collection.CollectionScreen
import com.megamaced.crate.ui.screen.detail.ItemDetailScreen
import com.megamaced.crate.ui.screen.home.HomeScreen
import com.megamaced.crate.ui.screen.login.LoginScreen
import com.megamaced.crate.ui.screen.playlist.PlaylistDetailScreen
import com.megamaced.crate.ui.screen.playlist.PlaylistListScreen
import com.megamaced.crate.ui.screen.scan.BarcodeScanScreen
import com.megamaced.crate.ui.screen.search.SearchScreen
import com.megamaced.crate.ui.screen.settings.SettingsScreen
import com.megamaced.crate.ui.screen.shared.SharedCategoryScreen
import com.megamaced.crate.ui.screen.shared.SharedWithMeScreen
import kotlinx.serialization.json.Json

@Composable
fun CrateNavHost(
    navController: NavHostController,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Destination.Home,
        modifier = modifier,
    ) {
        composable<Destination.Home> {
            HomeScreen(
                onItemClick = { id -> navController.navigate(Destination.Detail(id)) },
            )
        }
        composable<Destination.Collection> {
            CollectionScreen(
                onItemClick = { id -> navController.navigate(Destination.Detail(id)) },
                onAddItem = { category ->
                    navController.navigate(Destination.AddEdit(category = category.apiValue))
                },
                widthSizeClass = widthSizeClass,
            )
        }
        composable<Destination.Playlists> {
            PlaylistListScreen(
                onPlaylistClick = { id -> navController.navigate(Destination.PlaylistDetail(id)) },
                onOpenSharedWithMe = { navController.navigate(Destination.SharedWithMe) },
            )
        }
        composable<Destination.Search> {
            SearchScreen(
                onItemClick = { id -> navController.navigate(Destination.Detail(id)) },
                onAddFromExternal = { category, result ->
                    val prefill = Json.encodeToString(ExternalSearchResult.serializer(), result)
                    navController.navigate(
                        Destination.AddEdit(category = category.apiValue, prefillJson = prefill),
                    )
                },
            )
        }
        composable<Destination.Settings> {
            SettingsScreen(
                onOpenSharedWithMe = { navController.navigate(Destination.SharedWithMe) },
            )
        }
        composable<Destination.Login> {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Destination.Home) {
                        popUpTo(Destination.Login) { inclusive = true }
                    }
                },
            )
        }
        composable<Destination.Detail> {
            ItemDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id, categoryApiValue ->
                    navController.navigate(Destination.AddEdit(itemId = id, category = categoryApiValue))
                },
            )
        }
        composable<Destination.AddEdit> { backStackEntry ->
            val scanResultJson by backStackEntry.savedStateHandle
                .getStateFlow<String?>(SCAN_RESULT_KEY, null)
                .collectAsState(initial = null)
            AddEditItemScreen(
                onBack = { navController.popBackStack() },
                onScan = { categoryApiValue ->
                    navController.navigate(Destination.Scan(category = categoryApiValue))
                },
                scanResultJson = scanResultJson,
                onScanResultConsumed = { backStackEntry.savedStateHandle[SCAN_RESULT_KEY] = null },
            )
        }
        composable<Destination.Scan> {
            BarcodeScanScreen(
                onBack = { navController.popBackStack() },
                onResultPicked = { result ->
                    val handle = navController.previousBackStackEntry?.savedStateHandle
                    val json = Json.encodeToString(ExternalSearchResult.serializer(), result)
                    handle?.set(SCAN_RESULT_KEY, json)
                    navController.popBackStack()
                },
            )
        }
        composable<Destination.PlaylistDetail> {
            PlaylistDetailScreen(
                onBack = { navController.popBackStack() },
                onItemClick = { id -> navController.navigate(Destination.Detail(id)) },
            )
        }
        composable<Destination.SharedWithMe> {
            SharedWithMeScreen(
                onBack = { navController.popBackStack() },
                onOpenCategory = { category -> navController.navigate(Destination.SharedCategory(category)) },
                onPlaylistClick = { id -> navController.navigate(Destination.PlaylistDetail(id)) },
            )
        }
        composable<Destination.SharedCategory> {
            SharedCategoryScreen(
                onBack = { navController.popBackStack() },
                onItemClick = { id -> navController.navigate(Destination.Detail(id)) },
                onAddItem = { owner, category ->
                    navController.navigate(
                        Destination.AddEdit(category = category, owner = owner),
                    )
                },
                widthSizeClass = widthSizeClass,
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(title)
    }
}
