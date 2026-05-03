package com.macebox.crate.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute

@Composable
fun CrateNavHost(
    navController: NavHostController,
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
            PlaceholderScreen("Collection")
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
            PlaceholderScreen("Login")
        }
        composable<Destination.Detail> { backStackEntry ->
            val detail = backStackEntry.toRoute<Destination.Detail>()
            PlaceholderScreen("Detail — item ${detail.itemId}")
        }
        composable<Destination.AddEdit> { backStackEntry ->
            val addEdit = backStackEntry.toRoute<Destination.AddEdit>()
            val label = if (addEdit.itemId != null) "Edit ${addEdit.itemId}" else "Add"
            PlaceholderScreen(label)
        }
        composable<Destination.Scan> {
            PlaceholderScreen("Barcode Scan")
        }
        composable<Destination.PlaylistDetail> { backStackEntry ->
            val detail = backStackEntry.toRoute<Destination.PlaylistDetail>()
            PlaceholderScreen("Playlist ${detail.playlistId}")
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
