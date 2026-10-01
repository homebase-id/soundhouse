package id.homebase.audio.ui.common

import androidx.compose.runtime.Composable
import id.homebase.audio.resources.AR
import id.homebase.audio.resources.played_days_ago
import id.homebase.audio.resources.played_hours_ago
import id.homebase.audio.resources.played_just_now
import id.homebase.audio.resources.played_minutes_ago
import id.homebase.audio.resources.played_on
import id.homebase.audio.resources.played_yesterday
import id.homebase.audio.resources.time_left_minutes
import id.homebase.audio.resources.time_left_seconds
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class Minutes(val count: Int) : RelativeTime
    data class Hours(val count: Int) : RelativeTime
    data object Yesterday : RelativeTime
    data class Days(val count: Int) : RelativeTime
    data class On(val epochMs: Long) : RelativeTime
}

fun relativeTime(thenMs: Long, nowMs: Long): RelativeTime {
    val minutes = ((nowMs - thenMs).coerceAtLeast(0) / 60_000).toInt()
    return when {
        minutes < 1 -> RelativeTime.JustNow
        minutes < 60 -> RelativeTime.Minutes(minutes)
        minutes < 24 * 60 -> RelativeTime.Hours(minutes / 60)
        minutes < 48 * 60 -> RelativeTime.Yesterday
        minutes < 7 * 24 * 60 -> RelativeTime.Days(minutes / (24 * 60))
        else -> RelativeTime.On(thenMs)
    }
}

@Composable
fun playedAgo(thenMs: Long, nowMs: Long = Clock.System.now().toEpochMilliseconds()): String =
    when (val relative = relativeTime(thenMs, nowMs)) {
        RelativeTime.JustNow -> stringResource(AR.string.played_just_now)
        is RelativeTime.Minutes -> pluralStringResource(AR.plurals.played_minutes_ago, relative.count, relative.count)
        is RelativeTime.Hours -> pluralStringResource(AR.plurals.played_hours_ago, relative.count, relative.count)
        RelativeTime.Yesterday -> stringResource(AR.string.played_yesterday)
        is RelativeTime.Days -> pluralStringResource(AR.plurals.played_days_ago, relative.count, relative.count)
        is RelativeTime.On -> stringResource(AR.string.played_on, formatDate(relative.epochMs))
    }

@Composable
fun timeLeft(remainingMs: Long): String {
    val minutes = (remainingMs / 60_000).toInt()
    return if (minutes >= 1) pluralStringResource(AR.plurals.time_left_minutes, minutes, minutes)
    else pluralStringResource(AR.plurals.time_left_seconds, (remainingMs / 1000).toInt(), (remainingMs / 1000).toInt())
}
