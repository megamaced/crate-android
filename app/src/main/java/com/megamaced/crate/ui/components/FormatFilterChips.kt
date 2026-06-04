package com.megamaced.crate.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.megamaced.crate.R

data class FormatBucket(
    val format: String,
    val count: Int,
)

@Composable
fun FormatFilterChips(
    formats: List<FormatBucket>,
    totalCount: Int,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (formats.size < 2) return
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val allLabel = stringResource(R.string.collection_filter_all_chip, totalCount)
        val allA11y = stringResource(R.string.collection_chip_all_a11y, totalCount)
        FilterChip(
            selected = selected.isEmpty(),
            onClick = onClear,
            label = { Text(allLabel) },
            modifier = Modifier.semantics { contentDescription = allA11y },
        )
        formats.forEach { bucket ->
            val label = stringResource(
                R.string.collection_filter_format_chip,
                bucket.format,
                bucket.count,
            )
            val a11y = stringResource(
                R.string.collection_chip_format_a11y,
                bucket.format,
                bucket.count,
            )
            FilterChip(
                selected = bucket.format in selected,
                onClick = { onToggle(bucket.format) },
                label = { Text(label) },
                modifier = Modifier.semantics { contentDescription = a11y },
            )
        }
    }
}
