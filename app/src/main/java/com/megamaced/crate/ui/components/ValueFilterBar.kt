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
import androidx.compose.ui.unit.dp
import com.megamaced.crate.R
import com.megamaced.crate.ui.screen.collection.FilterBucket

/**
 * Year (decade) and genre filters, as dropdowns rather than chip rows: an
 * enriched collection carries far too many genres to lay out as chips. Each
 * dropdown lists only values present in the current category, with counts, and
 * is hidden entirely when there is nothing to choose between.
 */
@Composable
fun ValueFilterBar(
    decades: List<FilterBucket>,
    genres: List<FilterBucket>,
    selectedDecade: String?,
    selectedGenre: String?,
    totalCount: Int,
    onDecadeSelected: (String?) -> Unit,
    onGenreSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (decades.size < 2 && genres.size < 2) return
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (decades.size >= 2) {
            FilterDropdown(
                label = selectedDecade ?: stringResource(R.string.collection_filter_any_year),
                selected = selectedDecade != null,
                anyLabel = stringResource(R.string.collection_filter_any_year_count, totalCount),
                options = decades,
                onSelected = onDecadeSelected,
            )
        }
        if (genres.size >= 2) {
            FilterDropdown(
                label = selectedGenre ?: stringResource(R.string.collection_filter_any_genre),
                selected = selectedGenre != null,
                anyLabel = stringResource(R.string.collection_filter_any_genre_count, totalCount),
                options = genres,
                onSelected = onGenreSelected,
            )
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
