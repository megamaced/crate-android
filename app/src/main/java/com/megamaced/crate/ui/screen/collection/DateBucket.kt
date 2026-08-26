package com.megamaced.crate.ui.screen.collection

import com.megamaced.crate.R
import com.megamaced.crate.util.UiText
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
    ): UiText {
        if (createdAt.isNullOrBlank()) return UNKNOWN_BUCKET
        val itemDate = try {
            LocalDateTime.parse(createdAt, FORMATTER).toLocalDate()
        } catch (_: DateTimeParseException) {
            return UNKNOWN_BUCKET
        }
        val diffDays = ChronoUnit.DAYS.between(itemDate, today)
        // Guard clock skew / server-vs-device timezone: a createdAt ahead of
        // the device date yields a negative diff, which would otherwise fall
        // through to "Earlier this week". Treat future dates as newest.
        if (diffDays <= 0L) return UiText.Res(R.string.date_bucket_today)
        if (diffDays == 1L) return UiText.Res(R.string.date_bucket_yesterday)
        if (diffDays < 7L) return UiText.Res(R.string.date_bucket_earlier_this_week)
        if (diffDays < 14L) return UiText.Res(R.string.date_bucket_last_week)
        val sameYear = itemDate.year == today.year
        if (sameYear && itemDate.month == today.month) return UiText.Res(R.string.date_bucket_earlier_this_month)
        val lastMonth = today.minusMonths(1)
        if (itemDate.year == lastMonth.year && itemDate.month == lastMonth.month) {
            return UiText.Res(R.string.date_bucket_last_month)
        }
        if (sameYear) return UiText.Res(R.string.date_bucket_earlier_this_year)
        if (itemDate.year == today.year - 1) return UiText.Res(R.string.date_bucket_last_year)
        // A calendar year is the same in every language, so it needs no resource.
        return UiText.Raw(itemDate.year.toString())
    }
}
