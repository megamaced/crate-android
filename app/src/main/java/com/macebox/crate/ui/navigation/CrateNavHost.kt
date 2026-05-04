package com.macebox.crate.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.macebox.crate.ui.screen.addedit.AddEditItemScreen
import com.macebox.crate.ui.screen.addedit.ExternalSearchResult
import com.macebox.crate.ui.screen.addedit.SCAN_RESULT_KEY
import com.macebox.crate.ui.screen.collection.CollectionScreen
import com.macebox.crate.ui.screen.detail.ItemDetailScreen
import com.macebox.crate.ui.screen.home.HomeScreen
import com.macebox.crate.ui.screen.login.LoginScreen
import com.macebox.crate.ui.screen.playlist.PlaylistDetailScreen
import com.macebox.crate.ui.screen.playlist.PlaylistListScreen
import com.macebox.crate.ui.screen.scan.BarcodeScanScreen
import com.macebox.crate.ui.screen.search.SearchScreen
import com.macebox.crate.ui.screen.settings.SettingsScreen
import com.macebox.crate.ui.screen.shared.SharedWithMeScreen
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
        composable<Destination.AddEdit> {
            AddEditItemScreen(
                onBack = { navController.popBackStack() },
                onScan = { categoryApiValue ->
                    navController.navigate(Destination.Scan(category = categoryApiValue))
                },
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
                onItemClick = { id -> navController.navigate(Destination.Detail(id)) },
                onPlaylistClick = { id -> navController.navigate(Destination.PlaylistDetail(id)) },
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
