package com.megamaced.crate.ui.components

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
import androidx.compose.ui.res.stringResource
import com.megamaced.crate.R
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.CollectionSort
import com.megamaced.crate.domain.model.sortOptionsFor

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
            contentDescription = stringResource(R.string.action_sort),
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        options.forEach { option ->
            // The noun ("Artist", "Album", …) is a resource of its own so the
            // label can put it wherever the language wants it.
            val label =
                option.nounRes
                    ?.let { stringResource(option.labelRes, stringResource(it)) }
                    ?: stringResource(option.labelRes)
            DropdownMenuItem(
                text = {
                    Text(
                        if (option.sort == selected) {
                            stringResource(R.string.sort_option_selected, label)
                        } else {
                            label
                        },
                    )
                },
                onClick = {
                    onSelected(option.sort)
                    expanded = false
                },
            )
        }
    }
}
