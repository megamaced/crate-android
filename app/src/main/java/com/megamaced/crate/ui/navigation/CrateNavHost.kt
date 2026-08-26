package com.megamaced.crate.ui.navigation

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.megamaced.crate.data.api.dto.SuggestionDto
import com.megamaced.crate.ui.screen.addedit.AddEditItemScreen
import com.megamaced.crate.ui.screen.addedit.ExternalSearchResult
import com.megamaced.crate.ui.screen.addedit.SCAN_RESULT_KEY
import com.megamaced.crate.ui.screen.collection.CollectionScreen
import com.megamaced.crate.ui.screen.detail.DetailFilterAxis
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

/**
 * A provider suggestion in the shape the add form already understands. The
 * server returns each suggestion in its provider's search-result shape, so
 * exactly one id field is set; they all land in `discogsId`, which is the
 * generic enrichment-id field on both the form and the server's schema.
 */
private fun SuggestionDto.toExternalSearchResult(): ExternalSearchResult =
    ExternalSearchResult(
        title = title,
        artist = artist,
        format = format,
        year = year,
        barcode = barcode,
        label = label,
        discogsId = discogsId ?: tmdbId ?: workKey ?: rawgId ?: comicVineId,
        subtitle = genres,
        coverUrl = thumb ?: artworkUrl,
    )

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
                onItemClick = { id -> navController.navigateOnce(Destination.Detail(id)) },
            )
        }
        composable<Destination.Collection> {
            CollectionScreen(
                onItemClick = { id -> navController.navigateOnce(Destination.Detail(id)) },
                onAddItem = { category ->
                    navController.navigateOnce(Destination.AddEdit(category = category.apiValue))
                },
                widthSizeClass = widthSizeClass,
            )
        }
        composable<Destination.Playlists> {
            PlaylistListScreen(
                onPlaylistClick = { id -> navController.navigateOnce(Destination.PlaylistDetail(id)) },
                onOpenSharedWithMe = { navController.navigateOnce(Destination.SharedWithMe) },
            )
        }
        composable<Destination.Search> {
            SearchScreen(
                onItemClick = { id -> navController.navigateOnce(Destination.Detail(id)) },
                onAddFromExternal = { category, result ->
                    val prefill = Json.encodeToString(ExternalSearchResult.serializer(), result)
                    navController.navigateOnce(
                        Destination.AddEdit(category = category.apiValue, prefillJson = prefill),
                    )
                },
            )
        }
        composable<Destination.Settings> {
            SettingsScreen(
                onOpenSharedWithMe = { navController.navigateOnce(Destination.SharedWithMe) },
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
                    navController.navigateOnce(Destination.AddEdit(itemId = id, category = categoryApiValue))
                },
                // A tapped genre / format / year goes back to the list the
                // item lives in — the shared-category page for a shared item,
                // otherwise the collection — pre-filtered to that value.
                // popUpTo replaces the list we came from rather than stacking a
                // second copy on top of the detail screen; when we arrived from
                // Home or Search there is nothing to pop and it simply pushes.
                onFilterCollection = { target ->
                    val genre = target.value.takeIf { target.axis == DetailFilterAxis.Genre }
                    val format = target.value.takeIf { target.axis == DetailFilterAxis.Format }
                    val decade = target.value.takeIf { target.axis == DetailFilterAxis.Decade }
                    if (target.isShared) {
                        navController.navigateOnce(
                            Destination.SharedCategory(
                                category = target.categoryApiValue,
                                genre = genre,
                                format = format,
                                decade = decade,
                            ),
                        ) {
                            popUpTo<Destination.SharedCategory> { inclusive = true }
                        }
                    } else {
                        navController.navigateOnce(
                            Destination.Collection(
                                category = target.categoryApiValue,
                                genre = genre,
                                format = format,
                                decade = decade,
                            ),
                        ) {
                            popUpTo<Destination.Collection> { inclusive = true }
                        }
                    }
                },
                onOpenItem = { id -> navController.navigateOnce(Destination.Detail(id)) },
                // An "If you like this…" suggestion is something the user
                // doesn't own, so it goes to the add form pre-filled and
                // defaulted to the wishlist rather than straight into the
                // collection.
                onAddSuggestion = { category, suggestion ->
                    val prefill = Json.encodeToString(
                        ExternalSearchResult.serializer(),
                        suggestion.toExternalSearchResult(),
                    )
                    navController.navigateOnce(
                        Destination.AddEdit(
                            category = category.apiValue,
                            prefillJson = prefill,
                            defaultWanted = true,
                        ),
                    )
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
                    navController.navigateOnce(Destination.Scan(category = categoryApiValue))
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
                onItemClick = { id -> navController.navigateOnce(Destination.Detail(id)) },
            )
        }
        composable<Destination.SharedWithMe> {
            SharedWithMeScreen(
                onBack = { navController.popBackStack() },
                onOpenCategory = { category -> navController.navigateOnce(Destination.SharedCategory(category)) },
                onPlaylistClick = { id -> navController.navigateOnce(Destination.PlaylistDetail(id)) },
            )
        }
        composable<Destination.SharedCategory> {
            SharedCategoryScreen(
                onBack = { navController.popBackStack() },
                onItemClick = { id -> navController.navigateOnce(Destination.Detail(id)) },
                onAddItem = { owner, category ->
                    navController.navigateOnce(
                        Destination.AddEdit(category = category, owner = owner),
                    )
                },
                widthSizeClass = widthSizeClass,
            )
        }
    }
}

/**
 * Push [destination] only while the current entry is RESUMED.
 *
 * None of these destinations are launchSingleTop, so a second tap arriving
 * during the enter transition would stack a duplicate entry — and a duplicate
 * detail entry means a second ViewModel firing the same refresh, auto-enrich
 * and market-value writes. Once the first tap starts navigating, the entry it
 * came from is no longer RESUMED, which is what makes the second tap a no-op.
 */
private fun NavHostController.navigateOnce(
    destination: Destination,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    if (currentBackStackEntry?.lifecycle?.currentState != Lifecycle.State.RESUMED) return
    navigate(route = destination, builder = builder)
}
