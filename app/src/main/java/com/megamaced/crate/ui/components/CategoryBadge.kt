package com.megamaced.crate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.megamaced.crate.R
import com.megamaced.crate.domain.model.Category

@Composable
fun CategoryBadge(
    category: Category,
    modifier: Modifier = Modifier,
) {
    val (container, content) = badgeColors(category)
    Text(
        text = stringResource(category.labelRes),
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun badgeColors(category: Category): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (category) {
        Category.Music -> scheme.primaryContainer to scheme.onPrimaryContainer
        Category.Films -> scheme.secondaryContainer to scheme.onSecondaryContainer
        Category.Books -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        Category.Games -> scheme.surfaceContainerHigh to scheme.onSurface
        Category.Comics -> scheme.errorContainer to scheme.onErrorContainer
    }
}

/**
 * The glyph that stands for a category. One mapping for the whole app: the
 * artwork placeholder and the collection toolbar show the same icon, so the
 * button in the toolbar reads as the category the grid beneath it is full of.
 *
 * Lives here rather than on the [Category] enum because an ImageVector is a
 * Compose type and the domain model deliberately carries only resource ids.
 * Null is the unknown category, which files under the music glyph.
 */
fun categoryIcon(category: Category?): ImageVector =
    when (category) {
        Category.Films -> Icons.Filled.Movie
        Category.Books -> Icons.Outlined.AutoStories
        Category.Games -> Icons.Filled.SportsEsports
        Category.Comics -> Icons.AutoMirrored.Filled.MenuBook
        Category.Music, null -> Icons.Filled.Album
    }
