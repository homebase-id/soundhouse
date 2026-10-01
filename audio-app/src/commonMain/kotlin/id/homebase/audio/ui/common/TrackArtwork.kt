package id.homebase.audio.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Gradient colours, a readable content colour and a gradient direction, picked deterministically per track. */
data class ArtworkPalette(val start: Color, val end: Color, val content: Color, val angleIndex: Int)

private val HUE_STEPS = listOf(0f, 35f, 70f, 140f, 180f, 215f, 260f, 300f)
private const val ANGLES = 4

/** Hue step and gradient direction in one number; the same seed always gets the same artwork. */
fun artworkVariant(seed: String): Int = abs(seed.hashCode() % (HUE_STEPS.size * ANGLES))

/**
 * The theme's own colour roles are nearly one hue here, so variety comes from rotating the primary
 * colour's hue while keeping its saturation and lightness: every tile stays on-palette in light and dark.
 */
fun ColorScheme.artworkPalette(seed: String): ArtworkPalette {
    val variant = artworkVariant(seed)
    val (hue, saturation, lightness) = primary.toHsl()
    val rotated = (hue + HUE_STEPS[variant % HUE_STEPS.size]) % 360f
    // Dark themes have a light primary; artwork there sits deeper so it doesn't glare.
    val dark = surface.luminance() < 0.5f
    val startLightness = if (dark) 0.42f else lightness.coerceIn(0.4f, 0.6f)
    val start = Color.hsl(rotated, saturation.coerceIn(0.35f, 0.7f), startLightness)
    val end = Color.hsl((rotated + 28f) % 360f, (saturation * 0.85f).coerceIn(0.3f, 0.65f), startLightness + 0.1f)
    val content = if (start.luminance() > 0.45f) Color.Black.copy(alpha = 0.78f) else Color.White
    return ArtworkPalette(start, end, content, variant / HUE_STEPS.size)
}

private fun Color.toHsl(): Triple<Float, Float, Float> {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val lightness = (max + min) / 2f
    if (max == min) return Triple(0f, 0f, lightness)
    val delta = max - min
    val saturation = if (lightness > 0.5f) delta / (2f - max - min) else delta / (max + min)
    val hue = when (max) {
        red -> ((green - blue) / delta + (if (green < blue) 6f else 0f))
        green -> (blue - red) / delta + 2f
        else -> (red - green) / delta + 4f
    } * 60f
    return Triple(hue, saturation, lightness)
}

/** First user-perceived character of [title], uppercased; never splits a surrogate pair. */
fun artworkGlyph(title: String): String {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return "♪"
    val end = if (trimmed.length > 1 && trimmed[0].isHighSurrogate()) 2 else 1
    return trimmed.substring(0, end).uppercase()
}

/** Cover art for tracks that have none: a theme-coloured gradient with the title's first letter. Size it with [modifier]. */
@Composable
fun TrackArtwork(
    title: String,
    seed: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
) {
    val palette = MaterialTheme.colorScheme.artworkPalette(seed)
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(gradient(palette)),
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, maxHeight).value
        val glyphSize = (side * if (side > 120f) 0.3f else 0.42f).sp
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(palette.content.copy(alpha = 0.10f), radius = size.minDimension * 0.55f, center = Offset(size.width, 0f))
            drawCircle(palette.content.copy(alpha = 0.07f), radius = size.minDimension * 0.35f, center = Offset(0f, size.height))
        }
        Text(
            text = artworkGlyph(title),
            color = palette.content,
            fontSize = glyphSize,
            lineHeight = glyphSize,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun gradient(palette: ArtworkPalette): Brush = when (palette.angleIndex) {
    0 -> Brush.linearGradient(listOf(palette.start, palette.end))
    1 -> Brush.verticalGradient(listOf(palette.start, palette.end))
    2 -> Brush.horizontalGradient(listOf(palette.start, palette.end))
    else -> Brush.linearGradient(listOf(palette.end, palette.start))
}

/** Three bouncing bars marking the track that's playing; frozen while paused. */
@Composable
fun NowPlayingBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val transition = rememberInfiniteTransition()
    val heights = listOf(0.35f to 1f, 0.6f to 0.25f, 0.2f to 0.85f).mapIndexed { index, (from, to) ->
        val animated by transition.animateFloat(
            initialValue = from,
            targetValue = to,
            animationSpec = infiniteRepeatable(tween(durationMillis = 420 + index * 110), RepeatMode.Reverse),
        )
        if (playing) animated else from
    }
    BoxWithConstraints(modifier) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(maxWidth / 9), verticalAlignment = Alignment.Bottom) {
            heights.forEach { fraction ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(color)
                )
            }
        }
    }
}
