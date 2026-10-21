package com.megamaced.crate.util

/** First Crate server release that stores an item's star rating. */
private val RATINGS_SINCE = SemVer(1, 15, 0)

/**
 * True when a Crate server reporting [crateVersion] keeps a star rating. An
 * older one ignores the field, so a rating sent to it is lost on the next
 * sync. An unknown or unreadable version counts as older.
 */
internal fun serverSupportsRatings(crateVersion: String?): Boolean = crateVersion?.let(::parseSemVer)?.let { it >= RATINGS_SINCE } ?: false
