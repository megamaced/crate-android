package com.megamaced.crate.ui.screen.addedit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage

/**
 * One provider result — cover thumbnail, title, and a metadata subtitle.
 *
 * Shared by the manual search sheet ([ExternalSearchSheet]) and the barcode
 * scanner's candidate sheet so both render provider hits identically.
 */
@Composable
internal fun ExternalResultRow(
    result: ExternalSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val coverShape = RoundedCornerShape(6.dp)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(coverShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (!result.coverUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = result.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = result.title.ifBlank { "(untitled)" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = result.subtitle ?: buildSubtitle(result)
            if (!sub.isNullOrBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal fun ExternalSearchResult.identityKey(): String =
    discogsId
        ?: barcode
        ?: "$title|${artist.orEmpty()}|${year ?: 0}"

private fun buildSubtitle(result: ExternalSearchResult): String? =
    listOfNotNull(
        result.artist?.takeIf { it.isNotBlank() },
        result.year?.toString(),
        result.format?.takeIf { it.isNotBlank() },
        result.label?.takeIf { it.isNotBlank() },
    ).joinToString(" · ").ifBlank { null }
