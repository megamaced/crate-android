package com.megamaced.crate.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Home
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.megamaced.crate.R
import com.megamaced.crate.data.auth.AuthState
import com.megamaced.crate.data.auth.SessionManager
import com.megamaced.crate.ui.components.OfflineBanner
import com.megamaced.crate.ui.network.LocalIsOnline
import com.megamaced.crate.util.NetworkMonitor
import kotlinx.coroutines.flow.MutableSharedFlow

data class TopLevelRoute(
    @param:StringRes val labelRes: Int,
    val destination: Destination,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val topLevelRoutes = listOf(
    TopLevelRoute(R.string.nav_home, Destination.Home, Icons.Filled.Home, Icons.Outlined.Home),
    TopLevelRoute(R.string.nav_collection, Destination.Collection(), Icons.Filled.Album, Icons.Outlined.Album),
    TopLevelRoute(R.string.nav_search, Destination.Search, Icons.Filled.Search, Icons.Outlined.Search),
    TopLevelRoute(
        R.string.nav_playlists,
        Destination.Playlists,
        Icons.AutoMirrored.Filled.QueueMusic,
        Icons.AutoMirrored.Outlined.QueueMusic,
    ),
    TopLevelRoute(R.string.nav_settings, Destination.Settings, Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun CrateScaffold(
    widthSizeClass: WindowWidthSizeClass,
    sessionManager: SessionManager,
    networkMonitor: NetworkMonitor,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentTopLevel = navBackStackEntry?.owningTopLevelRoute()
    val authState by sessionManager.authState.collectAsState()
    val isOnline by networkMonitor.isOnline.collectAsState(initial = true)

    DisposableEffect(navController) {
        navController.addOnDestinationChangedListener(recordOwningTopLevelRoute)
        onDispose { navController.removeOnDestinationChangedListener(recordOwningTopLevelRoute) }
    }

    LaunchedEffect(authState) {
        when (authState) {
            AuthState.Unauthenticated -> {
                navController.navigate(Destination.Login) {
                    popUpTo(0) { inclusive = true }
                }
            }

            AuthState.Authenticated, AuthState.Unknown -> { /* no-op */ }
        }
    }

    val isOnLogin = currentDestination?.hasRoute(Destination.Login::class) == true
    val useNavRail = widthSizeClass != WindowWidthSizeClass.Compact
    // The add/edit form and the barcode scanner are full-screen tasks, left by
    // saving or going back rather than by switching tab, so they cover the
    // navigation bar or rail the way Material's full-screen dialogs do.
    val showNavigation = currentDestination?.let {
        it.hasRoute(Destination.AddEdit::class) || it.hasRoute(Destination.Scan::class)
    } != true

    val tabReselections = remember { MutableSharedFlow<Destination>(extraBufferCapacity = 1) }
    val onTabClick: (TopLevelRoute) -> Unit = { route ->
        navController.onTopLevelClick(route) { tabReselections.tryEmit(route.destination) }
    }

    // The nav host is declared once, as movable content, and only the chrome
    // around it varies. Compose identity is otherwise positional, so three
    // separate CrateNavHost calls would give the host three different slots:
    // crossing the width-class boundary on a tablet rotation, or the login →
    // authenticated transition, would dispose the entire nav graph and rebuild
    // it, losing every list scroll position, sheet and non-saveable remember.
    // The size class is a parameter rather than a capture so the moved content
    // still sees the current value.
    val host = remember(navController) {
        movableContentOf<Modifier, WindowWidthSizeClass> { hostModifier, sizeClass ->
            CrateNavHost(
                navController = navController,
                widthSizeClass = sizeClass,
                tabReselections = tabReselections,
                modifier = hostModifier,
            )
        }
    }

    CompositionLocalProvider(LocalIsOnline provides isOnline) {
        when {
            isOnLogin -> {
                host(Modifier, widthSizeClass)
            }

            useNavRail -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(visible = showNavigation) {
                        NavigationRail {
                            topLevelRoutes.forEach { route ->
                                val selected = route == currentTopLevel
                                val label = stringResource(route.labelRes)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { onTabClick(route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) route.selectedIcon else route.unselectedIcon,
                                            contentDescription = label,
                                        )
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        OfflineBanner(isOnline = isOnline)
                        host(Modifier.weight(1f), widthSizeClass)
                    }
                }
            }

            else -> {
                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        AnimatedVisibility(
                            visible = showNavigation,
                            enter = slideInVertically { it } + expandVertically(),
                            exit = slideOutVertically { it } + shrinkVertically(),
                        ) {
                            NavigationBar {
                                topLevelRoutes.forEach { route ->
                                    val selected = route == currentTopLevel
                                    val label = stringResource(route.labelRes)
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { onTabClick(route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) route.selectedIcon else route.unselectedIcon,
                                                contentDescription = label,
                                            )
                                        },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                    },
                ) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        OfflineBanner(isOnline = isOnline)
                        host(Modifier.weight(1f), widthSizeClass)
                    }
                }
            }
        }
    }
}

// SavedStateHandle key under which a secondary back-stack entry records the
// tab it was opened from (the tab route's class name). Living in the entry's
// own saved state, it survives the entry being saved and restored with its
// tab's stack, and process death.
private const val OWNING_TAB_KEY = "crate.owningTopLevelRoute"

private val TopLevelRoute.key: String get() = destination::class.java.name

/**
 * The tab this entry belongs to: itself for a tab's root screen, otherwise the
 * tab recorded by [recordOwningTopLevelRoute] when the entry was pushed.
 *
 * Secondary screens (item detail, playlist detail, the shared views…) sit in
 * one flat graph rather than a nested graph per tab, so their destination
 * hierarchy never contains a tab and can't say which one should be selected.
 */
private fun NavBackStackEntry.owningTopLevelRoute(): TopLevelRoute? =
    topLevelRoutes.firstOrNull { destination.hasRoute(it.destination::class) }
        ?: savedStateHandle.get<String>(OWNING_TAB_KEY)?.let { key ->
            topLevelRoutes.firstOrNull { it.key == key }
        }

/**
 * Gives each newly pushed secondary entry the tab of the entry it was opened
 * from. Listeners run before the current-entry flow emits, so the record is in
 * place by the time the bar recomposes for the new entry.
 */
private val recordOwningTopLevelRoute =
    NavController.OnDestinationChangedListener { controller, _, _ ->
        val entry = controller.currentBackStackEntry ?: return@OnDestinationChangedListener
        if (entry.owningTopLevelRoute() != null) return@OnDestinationChangedListener
        val owner = controller.previousBackStackEntry?.owningTopLevelRoute()
            ?: return@OnDestinationChangedListener
        entry.savedStateHandle[OWNING_TAB_KEY] = owner.key
    }

/**
 * Selecting another tab switches to it with its saved stack. Re-selecting the
 * active tab returns to its root screen or, when the root is already showing,
 * calls [onReselectedAtRoot] so the screen scrolls back to its top.
 * Re-selection can't go through [navigateTopLevel]: that saves the active
 * tab's stack and immediately restores it, leaving the user on the same screen.
 *
 * The active tab is read from the controller at tap time, not from the last
 * composition: two taps handled before a recomposition would otherwise see a
 * stale tab, take a switch for a re-selection and pop another tab's stack
 * without saving it.
 */
private fun NavHostController.onTopLevelClick(
    route: TopLevelRoute,
    onReselectedAtRoot: () -> Unit,
) {
    val entry = currentBackStackEntry
    val tab = route.destination::class
    when {
        entry == null || entry.owningTopLevelRoute() != route -> navigateTopLevel(route.destination)
        entry.destination.hasRoute(tab) -> onReselectedAtRoot()
        !popBackStack(tab, inclusive = false) -> navigateTopLevel(route.destination)
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
