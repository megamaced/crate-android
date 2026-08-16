package com.megamaced.crate.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.megamaced.crate.R
import com.megamaced.crate.ui.screen.collection.FilterBucket

/**
 * Every collection filter on one scrolling row: year (by decade) and genre as
 * dropdowns first, then the format chips. Dropdowns rather than chips for the
 * first two because an enriched collection carries dozens of genres — and one
 * shared row rather than three keeps the list itself on screen.
 *
 * Each control is dropped entirely when there is nothing to choose between, so
 * a single-format category shows only what's useful.
 */
@Composable
fun CollectionFilterBar(
    formats: List<FilterBucket>,
    totalCount: Int,
    selectedFormats: Set<String>,
    onToggleFormat: (String) -> Unit,
    onClearFormats: () -> Unit,
    decades: List<FilterBucket>,
    genres: List<FilterBucket>,
    selectedDecade: String?,
    selectedGenre: String?,
    onDecadeSelected: (String?) -> Unit,
    onGenreSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showFormats = formats.size >= 2
    val showDecades = decades.size >= 2
    val showGenres = genres.size >= 2
    if (!showFormats && !showDecades && !showGenres) return

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showDecades) {
            FilterDropdown(
                label = selectedDecade ?: stringResource(R.string.collection_filter_any_year),
                selected = selectedDecade != null,
                anyLabel = stringResource(R.string.collection_filter_any_year_count, totalCount),
                options = decades,
                onSelected = onDecadeSelected,
            )
        }
        if (showGenres) {
            FilterDropdown(
                label = selectedGenre ?: stringResource(R.string.collection_filter_any_genre),
                selected = selectedGenre != null,
                anyLabel = stringResource(R.string.collection_filter_any_genre_count, totalCount),
                options = genres,
                onSelected = onGenreSelected,
            )
        }
        if (showFormats) {
            val allLabel = stringResource(R.string.collection_filter_all_chip, totalCount)
            val allA11y = stringResource(R.string.collection_chip_all_a11y, totalCount)
            FilterChip(
                selected = selectedFormats.isEmpty(),
                onClick = onClearFormats,
                label = { Text(allLabel) },
                modifier = Modifier.semantics { contentDescription = allA11y },
            )
            formats.forEach { bucket ->
                val label = stringResource(
                    R.string.collection_filter_format_chip,
                    bucket.value,
                    bucket.count,
                )
                val a11y = stringResource(
                    R.string.collection_chip_format_a11y,
                    bucket.value,
                    bucket.count,
                )
                FilterChip(
                    selected = bucket.value in selectedFormats,
                    onClick = { onToggleFormat(bucket.value) },
                    label = { Text(label) },
                    modifier = Modifier.semantics { contentDescription = a11y },
                )
            }
        }
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    selected: Boolean,
    anyLabel: String,
    options: List<FilterBucket>,
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { expanded = true },
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                )
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(anyLabel) },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            options.forEach { bucket ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                R.string.collection_filter_value_option,
                                bucket.value,
                                bucket.count,
                            ),
                        )
                    },
                    onClick = {
                        onSelected(bucket.value)
                        expanded = false
                    },
                )
            }
        }
    }
}
