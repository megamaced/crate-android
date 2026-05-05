package com.macebox.crate.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.macebox.crate.domain.model.Category
import com.macebox.crate.domain.model.CategoryFeed
import com.macebox.crate.domain.model.HomeFeed
import com.macebox.crate.domain.model.MarketValue
import com.macebox.crate.domain.model.MediaItem
import com.macebox.crate.ui.components.ArtworkImage
import com.macebox.crate.ui.components.ArtworkSize
import com.macebox.crate.ui.components.EmptyState
import com.macebox.crate.ui.components.LoadingState
import com.macebox.crate.ui.components.MediaCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Home") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> LoadingState()
                uiState.feed == null ->
                    EmptyState(
                        title = "Nothing to show",
                        subtitle = "Pull to refresh once your collection has items.",
                    )
                else -> HomeContent(feed = uiState.feed!!, onItemClick = onItemClick)
            }
        }
    }
}

@Composable
private fun HomeContent(
    feed: HomeFeed,
    onItemClick: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        feed.categoryFeeds.forEach { categoryFeed ->
            categoryFeed.itemOfDay?.let { hero ->
                item(key = "hero-${categoryFeed.category.apiValue}") {
                    HeroItemOfTheDay(
                        item = hero,
                        category = categoryFeed.category,
                        onClick = { onItemClick(hero.id) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }

        if (feed.recentlyAdded.isNotEmpty()) {
            item(key = "recently-added") {
                RecentlyAddedSection(items = feed.recentlyAdded, onItemClick = onItemClick)
            }
        }

        feed.categoryFeeds.forEach { categoryFeed ->
            if (categoryFeed.recentItems.isNotEmpty()) {
                item(key = "category-${categoryFeed.category.apiValue}") {
                    CategoryRecentSection(categoryFeed = categoryFeed, onItemClick = onItemClick)
                }
            }
        }

        if (feed.mostValuable.isNotEmpty()) {
            item(key = "most-valuable") {
                MostValuableSection(items = feed.mostValuable, onItemClick = onItemClick)
            }
        }
    }
}

@Composable
private fun HeroItemOfTheDay(
    item: MediaItem,
    category: Category,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = categoryAccent(category)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = accent.container,
            contentColor = accent.onContainer,
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(accent.container),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "${category.label} · Item of the Day",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Row(modifier = Modifier.padding(16.dp)) {
                ArtworkImage(
                    itemId = item.id,
                    contentDescription = item.title,
                    updatedAt = item.updatedAt,
                    size = ArtworkSize.Full,
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.artist?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val tail = listOfNotNull(item.format, item.year?.toString())
                        .joinToString(" · ")
                        .takeIf { it.isNotBlank() }
                    if (tail != null) {
                        Text(
                            text = tail,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentlyAddedSection(
    items: List<MediaItem>,
    onItemClick: (Long) -> Unit,
) {
    SectionHeader(
        title = "Recently Added",
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        items(items, key = { it.id }) { item ->
            MediaCard(
                item = item,
                onClick = { onItemClick(item.id) },
                modifier = Modifier.width(140.dp),
            )
        }
    }
}

@Composable
private fun CategoryRecentSection(
    categoryFeed: CategoryFeed,
    onItemClick: (Long) -> Unit,
) {
    SectionHeader(
        title = "${categoryFeed.category.label} · Recent",
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        items(categoryFeed.recentItems, key = { it.id }) { item ->
            MediaCard(
                item = item,
                onClick = { onItemClick(item.id) },
                modifier = Modifier.width(140.dp),
            )
        }
    }
}

@Composable
private fun MostValuableSection(
    items: List<MediaItem>,
    onItemClick: (Long) -> Unit,
) {
    SectionHeader(
        title = "Most Valuable",
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        items(items, key = { it.id }) { item ->
            MostValuableCard(
                item = item,
                onClick = { onItemClick(item.id) },
                modifier = Modifier.width(160.dp),
            )
        }
    }
}

@Composable
private fun MostValuableCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            )
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                formatMarketValue(item.marketValue)?.let { value ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

private data class CategoryAccent(
    val container: Color,
    val onContainer: Color,
)

@Composable
private fun categoryAccent(category: Category): CategoryAccent {
    val scheme = MaterialTheme.colorScheme
    return when (category) {
        Category.Music -> CategoryAccent(scheme.primaryContainer, scheme.onPrimaryContainer)
        Category.Films -> CategoryAccent(scheme.secondaryContainer, scheme.onSecondaryContainer)
        Category.Books -> CategoryAccent(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        Category.Games -> CategoryAccent(scheme.surfaceContainerHigh, scheme.onSurface)
        Category.Comics -> CategoryAccent(scheme.errorContainer, scheme.onErrorContainer)
    }
}

private fun formatMarketValue(value: MarketValue): String? {
    val main = value.main ?: value.new ?: value.loose ?: return null
    val symbol = when (value.currency?.uppercase()) {
        "GBP" -> "£"
        "USD" -> "$"
        "EUR" -> "€"
        null, "" -> ""
        else -> "${value.currency} "
    }
    return "$symbol${"%.0f".format(main)}"
}
