package com.macebox.crate.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.macebox.crate.domain.model.Category
import com.macebox.crate.domain.model.CollectionSort
import com.macebox.crate.domain.model.sortOptionsFor

@Composable
fun SortMenuButton(
    category: Category,
    selected: CollectionSort,
    onSelected: (CollectionSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(category) { sortOptionsFor(category) }

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Sort,
            contentDescription = "Sort",
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        options.forEach { (option, label) ->
            DropdownMenuItem(
                text = { Text(label + if (option == selected) "  ✓" else "") },
                onClick = {
                    onSelected(option)
                    expanded = false
                },
            )
        }
    }
}
