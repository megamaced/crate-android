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
import com.macebox.crate.ui.screen.collection.CollectionScreen
import com.macebox.crate.ui.screen.detail.ItemDetailScreen
import com.macebox.crate.ui.screen.login.LoginScreen

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
            PlaceholderScreen("Home")
        }
        composable<Destination.Collection> {
            CollectionScreen(
                onItemClick = { id -> navController.navigate(Destination.Detail(id)) },
                widthSizeClass = widthSizeClass,
            )
        }
        composable<Destination.Playlists> {
            PlaceholderScreen("Playlists")
        }
        composable<Destination.Search> {
            PlaceholderScreen("Search")
        }
        composable<Destination.Settings> {
            PlaceholderScreen("Settings")
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
            )
        }
        composable<Destination.AddEdit> {
            PlaceholderScreen("Add / Edit")
        }
        composable<Destination.Scan> {
            PlaceholderScreen("Barcode Scan")
        }
        composable<Destination.PlaylistDetail> {
            PlaceholderScreen("Playlist")
        }
        composable<Destination.SharedWithMe> {
            PlaceholderScreen("Shared With Me")
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
