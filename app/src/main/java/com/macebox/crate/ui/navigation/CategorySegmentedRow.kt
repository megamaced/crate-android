package com.macebox.crate.ui.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class Category(
    val label: String,
) {
    Music("Music"),
    Films("Films"),
    Books("Books"),
    Games("Games"),
    Comics("Comics"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySegmentedRow(
    selected: Category,
    onCategorySelected: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Category.entries.forEachIndexed { index, category ->
            SegmentedButton(
                selected = category == selected,
                onClick = { onCategorySelected(category) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = Category.entries.size,
                ),
            ) {
                Text(category.label)
            }
        }
    }
}
