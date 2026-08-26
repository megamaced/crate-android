package com.megamaced.crate.util

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * Text that is named rather than spelled out, so whatever produced it needs no
 * Context: ViewModels, the shared-content store and the login flow all hand one
 * of these to the UI, which resolves it against resources during composition.
 *
 * [Raw] is the escape hatch for text that never came from resources in the
 * first place — an explanation the server or an exception supplied, which there
 * is nothing to translate.
 */
sealed interface UiText {
    data class Res(
        @param:StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    /** A quantity string; [count] both selects the plural form and fills `%1$d`. */
    data class Plural(
        @param:PluralsRes val id: Int,
        val count: Int,
    ) : UiText

    data class Raw(
        val value: String,
    ) : UiText
}

@Composable
@ReadOnlyComposable
fun UiText.resolve(): String =
    when (this) {
        is UiText.Res -> {
            if (args.isEmpty()) {
                stringResource(id)
            } else {
                stringResource(id, *args.toTypedArray())
            }
        }

        is UiText.Plural -> {
            pluralStringResource(id, count, count)
        }

        is UiText.Raw -> {
            value
        }
    }
