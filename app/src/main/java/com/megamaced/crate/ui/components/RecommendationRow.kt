package com.megamaced.crate.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.megamaced.crate.R
import com.megamaced.crate.data.api.dto.SuggestionDto
import com.megamaced.crate.domain.model.Category

/**
 * Artwork for one suggestion. A suggestion is either something already in the
 * collection (artwork served by the user's own server, via [ArtworkImage]) or
 * something from an enrichment provider (artwork straight off that provider's
 * CDN). Remote URLs are fetched unauthenticated — see AuthInterceptor, which
 * deliberately only attaches credentials for the user's own host.
 */
sealed interface SuggestionArt {
    data class Owned(
        val itemId: Long,
        val updatedAt: String?,
        val category: Category,
    ) : SuggestionArt

    data class Remote(
        val url: String?,
    ) : SuggestionArt
}

/**
 * What tapping a suggestion should do. Typed, so the row's click handler works
 * off the payload rather than parsing an id back out of [SuggestionEntry.key] —
 * a key-format change would break navigation with nothing to catch it.
 */
sealed interface SuggestionTarget {
    /** Something already in the collection: open its detail screen. */
    data class Owned(
        val itemId: Long,
    ) : SuggestionTarget

    /** A provider row the user doesn't own: open the add form pre-filled. */
    data class Provider(
        val suggestion: SuggestionDto,
    ) : SuggestionTarget
}

data class SuggestionEntry(
    /** List identity only — never a carrier for the payload. */
    val key: String,
    val title: String,
    val subtitle: String?,
    val art: SuggestionArt,
    val target: SuggestionTarget,
)

/**
 * A horizontally scrolling row of suggestions, or nothing at all when there
 * are none. Rendering nothing is the point: both suggestion rows are extras,
 * so an empty one should be invisible rather than an empty state — the detail
 * screen has to look complete without them.
 */
@Composable
fun RecommendationRow(
    title: String,
    entries: List<SuggestionEntry>,
    onClick: (SuggestionEntry) -> Unit,
    modifier: Modifier = Modifier,
    source: String? = null,
) {
    if (entries.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            if (source != null) {
                Text(
                    text = stringResource(R.string.recommendation_via_source, source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries, key = { it.key }) { entry ->
                Column(
                    modifier = Modifier
                        .width(108.dp)
                        .clickable { onClick(entry) },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val artModifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))

                    when (val art = entry.art) {
                        is SuggestionArt.Owned -> {
                            ArtworkImage(
                                itemId = art.itemId,
                                contentDescription = entry.title,
                                updatedAt = art.updatedAt,
                                category = art.category,
                                modifier = artModifier,
                            )
                        }

                        is SuggestionArt.Remote -> {
                            AsyncImage(
                                model = art.url,
                                contentDescription = entry.title,
                                contentScale = ContentScale.Crop,
                                modifier = artModifier,
                            )
                        }
                    }

                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!entry.subtitle.isNullOrBlank()) {
                        Text(
                            text = entry.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
