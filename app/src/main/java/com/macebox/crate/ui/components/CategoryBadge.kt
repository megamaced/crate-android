package com.macebox.crate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.macebox.crate.domain.model.Category

@Composable
fun CategoryBadge(
    category: Category,
    modifier: Modifier = Modifier,
) {
    val (container, content) = badgeColors(category)
    Text(
        text = category.label,
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
