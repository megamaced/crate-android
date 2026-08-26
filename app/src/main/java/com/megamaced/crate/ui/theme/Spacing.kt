package com.megamaced.crate.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Gap between one section of a screen and the next. A section takes its
 * separation from its container's arrangement or from the section composable
 * itself rather than from padding written out at each call site, so no section
 * can end up spaced differently from its neighbours.
 */
val SectionSpacing = 20.dp

/** Gap inside a section — a heading and its body, a caption and its chips. */
val WithinSectionSpacing = 8.dp
