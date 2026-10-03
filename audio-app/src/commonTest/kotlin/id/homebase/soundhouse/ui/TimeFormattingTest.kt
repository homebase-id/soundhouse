package id.homebase.soundhouse.ui

import id.homebase.soundhouse.ui.common.formatDate
import id.homebase.soundhouse.ui.common.formatDuration
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormattingTest {
    @Test
    fun durations() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:00", formatDuration(-5))
        assertEquals("0:59", formatDuration(59_999))
        assertEquals("3:07", formatDuration(187_000))
        assertEquals("1:00:05", formatDuration(3_605_000))
    }

    @Test
    fun dates() {
        assertEquals("Nov 14, 2023", formatDate(1_700_000_000_000, TimeZone.UTC))
    }
}
