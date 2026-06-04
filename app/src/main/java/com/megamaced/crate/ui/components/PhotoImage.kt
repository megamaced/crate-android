package com.megamaced.crate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.megamaced.crate.data.api.PlaceholderHost

/**
 * Renders one of the two user-supplied photo slots from
 * `/apps/crate/photo/{itemId}/{slot}`. Mirrors [ArtworkImage]'s contract:
 * the URL host is the Retrofit placeholder, [HostInterceptor][com.megamaced.crate.data.api.HostInterceptor]
 * rewrites it to the user's Nextcloud at request time, and [AuthInterceptor][com.megamaced.crate.data.api.AuthInterceptor]
 * attaches Basic Auth.
 */
@Composable
fun PhotoImage(
    itemId: Long,
    slot: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: ArtworkSize = ArtworkSize.Thumb,
    updatedAt: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val url = PlaceholderHost.urlPath("apps/crate/photo/$itemId/$slot?size=${size.apiValue}")
    val cacheKey = "photo-$itemId-$slot-${size.apiValue}-${updatedAt.orEmpty()}"

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
        loading = { PhotoPlaceholder(modifier = Modifier.fillMaxSize()) },
        error = { PhotoPlaceholder(modifier = Modifier.fillMaxSize()) },
    )
}

@Composable
private fun PhotoPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.4f),
        )
    }
}
