package com.megamaced.crate.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Server timestamps (`createdAt`/`updatedAt`/`wipedAt`) are written as
 * 'Y-m-d H:i:s' — local server time, zero-padded, no timezone or fractional
 * part. For that canonical format lexicographic and chronological ordering
 * coincide, but comparing as [LocalDateTime] is robust to incidental
 * whitespace and keeps ordering correct if the format ever grows a component.
 * When either value fails to parse we fall back to lexicographic comparison so
 * behaviour never regresses below the previous string-compare approach.
 */
object ServerTimestamps {
    private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** True if [candidate] is strictly newer than [reference]. */
    fun isNewer(
        candidate: String,
        reference: String,
    ): Boolean = compare(candidate, reference) > 0

    fun compare(
        a: String,
        b: String,
    ): Int {
        val da = parse(a)
        val db = parse(b)
        return if (da != null && db != null) da.compareTo(db) else a.compareTo(b)
    }

    private fun parse(value: String): LocalDateTime? =
        try {
            LocalDateTime.parse(value.trim(), FORMATTER)
        } catch (_: Exception) {
            null
        }
}
