package id.homebase.soundhouse.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

/**
 * Vertical bars centred on the midline. Bars left of [playedFraction] use [playedColor], the rest
 * [color]; [slots] fixes the bar width so a live meter fills in from the end instead of stretching.
 */
@Composable
fun LevelBars(
    levels: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    playedColor: Color = color,
    playedFraction: Float = 0f,
    slots: Int = levels.size,
) {
    Canvas(modifier) {
        if (slots <= 0) return@Canvas
        val slot = size.width / slots
        val stroke = slot * 0.6f
        val minHeight = stroke
        val offset = slots - levels.size
        levels.forEachIndexed { index, level ->
            val x = (offset + index + 0.5f) * slot
            val height = (level.coerceIn(0f, 1f) * (size.height - stroke)).coerceAtLeast(minHeight)
            val played = (index + 0.5f) / levels.size <= playedFraction
            drawLine(
                color = if (played) playedColor else color,
                start = Offset(x, size.height / 2 - height / 2),
                end = Offset(x, size.height / 2 + height / 2),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
