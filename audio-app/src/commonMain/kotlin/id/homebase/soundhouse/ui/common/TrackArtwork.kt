package id.homebase.soundhouse.ui.common

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.runtime.remember
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Gradient colours, a readable content colour and a gradient direction, picked deterministically per track. */
data class ArtworkPalette(val start: Color, val end: Color, val content: Color, val angleIndex: Int)

// Offsets from the theme primary's hue (Homebase blue): indigo, violet, plum, cyan, teal and one
// amber-dusk echoing the accent. Wider rotation reached lime and yellow-green, which read as cheap.
private val HUE_STEPS = listOf(0f, 22f, 48f, 82f, -28f, -52f, 165f, 12f)
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
    val rotated = (hue + HUE_STEPS[variant % HUE_STEPS.size] + 360f) % 360f
    // Dark themes have a light primary; artwork there sits deeper so it doesn't glare.
    val dark = surface.luminance() < 0.5f
    val startLightness = if (dark) 0.36f else lightness.coerceIn(0.38f, 0.5f)
    val start = Color.hsl(rotated, saturation.coerceIn(0.32f, 0.52f), startLightness)
    val end = Color.hsl((rotated + 24f) % 360f, (saturation * 0.8f).coerceIn(0.28f, 0.48f), startLightness + 0.12f)
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

/**
 * A track's soundprint: [bars] amplitudes (0..1) seeded by [seed], shaped like a phrase of sound
 * (swelling and fading) so it reads as audio rather than noise. Same seed, same print, on every device.
 */
fun soundprint(seed: String, bars: Int): List<Float> {
    val random = Random(seed.hashCode())
    val phase = random.nextFloat() * PI.toFloat()
    val swells = 1 + random.nextInt(3)
    var previous = random.nextFloat()
    return List(bars) { index ->
        val position = (index + 0.5f) / bars
        val envelope = 0.35f + 0.65f * abs(sin(phase + position * PI.toFloat() * swells))
        // Neighbouring bars lean on each other, like a real waveform.
        previous = (previous * 0.45f + random.nextFloat() * 0.55f)
        (envelope * (0.35f + 0.65f * previous)).coerceIn(0.12f, 1f)
    }
}

/** Generated cover for tracks without art: a theme-tinted gradient carrying the track's soundprint. */
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
    ) {
        val bars = if (minOf(maxWidth, maxHeight) < 80.dp) 7 else 19
        val print = remember(seed, bars) { soundprint(seed, bars) }
        Canvas(Modifier.fillMaxSize()) {
            val width = size.width * 0.64f
            val slot = width / bars
            val stroke = slot * 0.55f
            val left = (size.width - width) / 2
            val maxHeight = size.height * 0.58f
            print.forEachIndexed { index, amplitude ->
                val x = left + (index + 0.5f) * slot
                val height = (amplitude * maxHeight).coerceAtLeast(stroke)
                drawLine(
                    color = palette.content.copy(alpha = 0.92f),
                    start = Offset(x, size.height / 2 - height / 2),
                    end = Offset(x, size.height / 2 + height / 2),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
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
