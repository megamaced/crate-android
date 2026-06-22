package com.megamaced.crate.ui.screen.collection

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

// Mirrors getGroupKey(createdAt) in CollectionView.vue. The backend writes
// createdAt as 'Y-m-d H:i:s' (local server time, no timezone marker), so
// LocalDateTime.parse is the right shape.
internal object DateBucket {
    private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun labelFor(
        createdAt: String?,
        today: LocalDate = LocalDate.now(),
    ): String {
        if (createdAt.isNullOrBlank()) return "Unknown"
        val itemDate = try {
            LocalDateTime.parse(createdAt, FORMATTER).toLocalDate()
        } catch (_: DateTimeParseException) {
            return "Unknown"
        }
        val diffDays = ChronoUnit.DAYS.between(itemDate, today)
        if (diffDays == 0L) return "Today"
        if (diffDays == 1L) return "Yesterday"
        if (diffDays < 7L) return "Earlier this week"
        if (diffDays < 14L) return "Last week"
        val sameYear = itemDate.year == today.year
        if (sameYear && itemDate.month == today.month) return "Earlier this month"
        val lastMonth = today.minusMonths(1)
        if (itemDate.year == lastMonth.year && itemDate.month == lastMonth.month) return "Last month"
        if (sameYear) return "Earlier this year"
        if (itemDate.year == today.year - 1) return "Last year"
        return itemDate.year.toString()
    }
}
