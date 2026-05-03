package com.macebox.crate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest

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
    updatedAt: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val url = "https://placeholder.invalid/apps/crate/artwork/$itemId?size=${size.apiValue}"
    val cacheKey = "artwork-$itemId-${size.apiValue}-${updatedAt.orEmpty()}"

    val request = ImageRequest
        .Builder(context)
        .data(url)
        .memoryCacheKey(cacheKey)
        .diskCacheKey(cacheKey)
        .build()

    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = { ArtworkPlaceholder(modifier = Modifier.fillMaxSize()) },
        error = { ArtworkPlaceholder(modifier = Modifier.fillMaxSize()) },
    )
}

@Composable
fun ArtworkPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Album,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.4f),
        )
    }
}
