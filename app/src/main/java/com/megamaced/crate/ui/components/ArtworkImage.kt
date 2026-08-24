package com.megamaced.crate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.megamaced.crate.data.api.PlaceholderHost
import com.megamaced.crate.domain.model.Category

enum class ArtworkSize(
    val apiValue: String,
) {
    Thumb("thumb"),
    Full("full"),
}

/**
 * Resolves to `{host}/apps/crate/artwork/{itemId}?size=…`. The configured
 * OkHttpClient rewrites the host and adds Basic auth, so a placeholder host
 * here is intentional — see HostInterceptor.
 */
@Composable
fun ArtworkImage(
    itemId: Long,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: ArtworkSize = ArtworkSize.Thumb,
    // Required (no default): it's the only thing that varies the cache key when
    // artwork is replaced, so a call site that forgot it would serve a stale
    // image until eviction. Keep it mandatory so that can't happen.
    updatedAt: String?,
    contentScale: ContentScale = ContentScale.Crop,
    category: Category? = null,
) {
    val context = LocalContext.current
    // This is the hottest composable in the app — every grid cell, list row and
    // suggestion tile. Without remember, each frame rebuilt the request and its
    // three strings for every visible cell.
    val request = remember(context, itemId, size, updatedAt) {
        val cacheKey = "artwork-$itemId-${size.apiValue}-${updatedAt.orEmpty()}"
        ImageRequest
            .Builder(context)
            .data(PlaceholderHost.urlPath("apps/crate/artwork/$itemId?size=${size.apiValue}"))
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .build()
    }

    if (size == ArtworkSize.Full) {
        // One instance on screen at a time (the detail hero), so a subcomposed
        // placeholder is affordable here and nowhere else.
        SubcomposeAsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
            loading = { ArtworkPlaceholder(category = category, modifier = Modifier.fillMaxSize()) },
            error = { ArtworkPlaceholder(category = category, modifier = Modifier.fillMaxSize()) },
        )
        return
    }

    // AsyncImage over a drawn placeholder: no per-cell subcomposition, and the
    // placeholder simply stays visible while loading and after a failure.
    Box(modifier = modifier) {
        ArtworkPlaceholder(category = category, modifier = Modifier.matchParentSize())
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
fun ArtworkPlaceholder(
    modifier: Modifier = Modifier,
    category: Category? = null,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = placeholderIcon(category),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.4f),
        )
    }
}

private fun placeholderIcon(category: Category?): ImageVector =
    when (category) {
        Category.Films -> Icons.Filled.Movie
        Category.Books -> Icons.Outlined.AutoStories
        Category.Games -> Icons.Filled.SportsEsports
        Category.Comics -> Icons.AutoMirrored.Filled.MenuBook
        Category.Music, null -> Icons.Filled.Album
    }
