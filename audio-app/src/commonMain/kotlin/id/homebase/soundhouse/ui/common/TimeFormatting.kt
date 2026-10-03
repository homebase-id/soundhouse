package id.homebase.soundhouse.ui.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** `m:ss`, or `h:mm:ss` from an hour up. */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$ss" else "$minutes:$ss"
}

private val dateFormat = LocalDate.Format {
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    chars(" ")
    day(padding = kotlinx.datetime.format.Padding.NONE)
    chars(", ")
    year()
}

fun formatDate(epochMs: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone).date.format(dateFormat)

private val dateTimeFormat = kotlinx.datetime.LocalDateTime.Format {
    date(dateFormat)
    chars(" ")
    hour()
    chars(":")
    minute()
}

fun formatDateTime(epochMs: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone).format(dateTimeFormat)
