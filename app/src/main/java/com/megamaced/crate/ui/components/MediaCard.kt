package com.megamaced.crate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.megamaced.crate.domain.model.MediaItem

/**
 * Collection card. [artistFirst] swaps the two text lines so the artist (or
 * director / author / developer / writer) reads as the headline — used when the
 * active sort is that axis, so scanning the grid matches what it's ordered by.
 *
 * Every card measures to the same height whatever text it carries: the grid
 * reads as even rows, and a card recycled into a horizontal rail cannot change
 * that rail's height mid-scroll and push the content below it around.
 */
@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    artistFirst: Boolean = false,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column {
            ArtworkImage(
                itemId = item.id,
                contentDescription = item.title,
                updatedAt = item.updatedAt,
                category = item.category,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            )
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val artist = item.artist?.takeIf { it.isNotBlank() }
                val headline = if (artistFirst) artist ?: item.title else item.title
                val subtitle = if (artistFirst) item.title.takeIf { artist != null } else artist
                val tail = listOfNotNull(item.format, item.year?.toString()).joinToString(" · ")
                // Each line is pinned to a fixed line count and the two lower
                // lines are laid out whether or not there is anything to put in
                // them, so an item missing an artist or a format reserves the
                // same height as one carrying both. Line counts rather than a
                // height in dp, so the reserved space follows the system font
                // scale instead of clipping at it.
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleSmall,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
