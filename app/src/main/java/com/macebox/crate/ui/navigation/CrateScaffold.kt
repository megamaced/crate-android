package com.macebox.crate.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

data class TopLevelRoute(
    val label: String,
    val destination: Destination,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val topLevelRoutes = listOf(
    TopLevelRoute("Home", Destination.Home, Icons.Filled.Home, Icons.Outlined.Home),
    TopLevelRoute("Collection", Destination.Collection, Icons.Filled.Album, Icons.Outlined.Album),
    TopLevelRoute("Playlists", Destination.Playlists, Icons.Filled.QueueMusic, Icons.Outlined.QueueMusic),
    TopLevelRoute("Search", Destination.Search, Icons.Filled.Search, Icons.Outlined.Search),
    TopLevelRoute("Settings", Destination.Settings, Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun CrateScaffold(widthSizeClass: WindowWidthSizeClass) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val useNavRail = widthSizeClass != WindowWidthSizeClass.Compact

    if (useNavRail) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail {
                topLevelRoutes.forEach { route ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.hasRoute(route.destination::class)
                    } == true
                    NavigationRailItem(
                        selected = selected,
                        onClick = { navController.navigateTopLevel(route.destination) },
                        icon = {
                            Icon(
                                imageVector = if (selected) route.selectedIcon else route.unselectedIcon,
                                contentDescription = route.label,
                            )
                        },
                        label = { Text(route.label) },
                    )
                }
            }
            CrateNavHost(
                navController = navController,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    topLevelRoutes.forEach { route ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.hasRoute(route.destination::class)
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateTopLevel(route.destination) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) route.selectedIcon else route.unselectedIcon,
                                    contentDescription = route.label,
                                )
                            },
                            label = { Text(route.label) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            CrateNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

private fun NavHostController.navigateTopLevel(destination: Destination) {
    navigate(route = destination) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
