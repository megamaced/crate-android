package com.macebox.crate.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
        FilterChip(
            selected = selected.isEmpty(),
            onClick = onClear,
            label = { Text("All ($totalCount)") },
        )
        formats.forEach { bucket ->
            FilterChip(
                selected = bucket.format in selected,
                onClick = { onToggle(bucket.format) },
                label = { Text("${bucket.format} (${bucket.count})") },
            )
        }
    }
}
