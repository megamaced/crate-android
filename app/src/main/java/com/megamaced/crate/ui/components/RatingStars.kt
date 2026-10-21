package com.megamaced.crate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.megamaced.crate.R
import com.megamaced.crate.domain.model.MAX_RATING

/** The filled stars' colour: the amber the web app uses for its stars. */
private val StarColour = Color(0xFFE9A700)

/**
 * An item's star rating, shown and not editable. Read out as one label,
 * "4 out of 5 stars", rather than five separate stars.
 */
@Composable
fun RatingStars(
    rating: Int,
    modifier: Modifier = Modifier,
    starSize: Dp = 18.dp,
) {
    val summary = pluralStringResource(R.plurals.rating_summary, MAX_RATING, rating, MAX_RATING)
    Row(modifier = modifier.semantics(mergeDescendants = true) { contentDescription = summary }) {
        repeat(MAX_RATING) { index ->
            Star(on = index < rating, size = starSize)
        }
    }
}

/**
 * The rating control on the add/edit form: five stars and "Not rated", one
 * radio group, so each choice is read out as a selectable option.
 */
@Composable
fun RatingInput(
    rating: Int?,
    onRatingChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.field_rating),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (stars in 1..MAX_RATING) {
                val label = pluralStringResource(R.plurals.rating_stars, stars, stars)
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .selectable(
                                selected = rating == stars,
                                role = Role.RadioButton,
                                onClick = { onRatingChange(stars) },
                            ).semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    Star(on = stars <= (rating ?: 0), size = 32.dp)
                }
            }
            Box(
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = rating == null,
                            role = Role.RadioButton,
                            onClick = { onRatingChange(null) },
                        ).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.rating_none),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (rating == null) FontWeight.SemiBold else FontWeight.Normal,
                    color =
                        if (rating == null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

@Composable
private fun Star(
    on: Boolean,
    size: Dp,
) {
    Icon(
        imageVector = if (on) Icons.Filled.Star else Icons.Outlined.StarBorder,
        contentDescription = null,
        tint = if (on) StarColour else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(size),
    )
}
