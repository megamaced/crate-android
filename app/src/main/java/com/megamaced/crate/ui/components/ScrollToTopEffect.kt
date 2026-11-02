package com.megamaced.crate.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * Runs [scrollToTop] each time [requests] emits. A tab's root screen gets its
 * requests from the navigation bar: re-selecting the tab while its root is
 * already showing scrolls the screen back to its top.
 *
 * A user's own scroll interrupts the animation by cancelling it. Under
 * collectLatest that cancellation ends only the one request; under collect it
 * would end the collection, and every later request would be ignored.
 */
@Composable
fun ScrollToTopEffect(
    requests: Flow<Unit>,
    scrollToTop: suspend () -> Unit,
) {
    val currentScrollToTop by rememberUpdatedState(scrollToTop)
    LaunchedEffect(requests) {
        requests.collectLatest { currentScrollToTop() }
    }
}
