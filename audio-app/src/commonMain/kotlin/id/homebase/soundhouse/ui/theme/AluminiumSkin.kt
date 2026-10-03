package id.homebase.soundhouse.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** The metal finish's own tones, beyond what a Material colour scheme has roles for. */
@Immutable
class AluminiumColors(
    val metalTop: Color,
    val metalBottom: Color,
    val grainLight: Color,
    val grainDark: Color,
    val highlight: Color,
    val shadow: Color,
    val panel: Color,
    val onPanel: Color,
    val onPanelDim: Color,
    val knobCenter: Color,
    val knobEdge: Color,
    val knobRing: Color,
    val knobSheen: Color,
    val engraving: Color,
    val engravingDim: Color,
)

private val Light = AluminiumColors(
    metalTop = Color(0xFFE8E9EB),
    metalBottom = Color(0xFFC6C9CD),
    grainLight = Color(0x33FFFFFF),
    grainDark = Color(0x10000000),
    highlight = Color(0xE6FFFFFF),
    shadow = Color(0x59000000),
    panel = Color(0xFF1C1E20),
    onPanel = Color(0xFFEDE6D6),
    onPanelDim = Color(0xFF9C978C),
    knobCenter = Color(0xFFF6F7F8),
    knobEdge = Color(0xFFB4B8BD),
    knobRing = Color(0x0F000000),
    knobSheen = Color(0x66FFFFFF),
    engraving = Color(0xFF2A2C2F),
    engravingDim = Color(0xFF5E6268),
)

// "Space grey": anodised graphite rather than bare metal.
private val Dark = AluminiumColors(
    metalTop = Color(0xFF4B4E53),
    metalBottom = Color(0xFF2C2E32),
    grainLight = Color(0x14FFFFFF),
    grainDark = Color(0x1F000000),
    highlight = Color(0x40FFFFFF),
    shadow = Color(0xB3000000),
    panel = Color(0xFF111214),
    onPanel = Color(0xFFEDE6D6),
    onPanelDim = Color(0xFF8E897F),
    knobCenter = Color(0xFF6E7278),
    knobEdge = Color(0xFF383B40),
    knobRing = Color(0x14000000),
    knobSheen = Color(0x26FFFFFF),
    engraving = Color(0xFFE4E5E7),
    engravingDim = Color(0xFFA3A7AD),
)

val LocalAluminium = staticCompositionLocalOf { Light }

/**
 * Re-skins [content] in brushed aluminium: neutral colour roles with the amber accent kept as the
 * only colour, plus [LocalAluminium] for the finish itself. Follows the surrounding light/dark mode.
 */
@Composable
fun AluminiumTheme(content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val colors = if (base.surface.luminance() < 0.5f) Dark else Light
    val scheme = base.copy(
        primary = colors.engraving,
        onPrimary = colors.metalTop,
        primaryContainer = colors.knobEdge,
        onPrimaryContainer = colors.engraving,
        secondaryContainer = colors.knobEdge,
        onSecondaryContainer = colors.engraving,
        surface = colors.metalTop,
        onSurface = colors.engraving,
        onSurfaceVariant = colors.engravingDim,
        surfaceContainerHighest = colors.knobEdge,
    )
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography) {
        CompositionLocalProvider(LocalAluminium provides colors, content = content)
    }
}

private const val GRAIN_TILE = 256

// Horizontal streaks of random strength: full-width rows tile seamlessly, and short glints wrap
// across the tile edge so the seam doesn't show either.
private fun grainTile(colors: AluminiumColors): ImageBitmap {
    val bitmap = ImageBitmap(GRAIN_TILE, GRAIN_TILE)
    val canvas = Canvas(bitmap)
    val random = Random(7)
    val paint = Paint().apply { strokeWidth = 1f }
    for (y in 0 until GRAIN_TILE) {
        val row = y + 0.5f
        val tone = if (random.nextBoolean()) colors.grainLight else colors.grainDark
        paint.color = tone.copy(alpha = tone.alpha * random.nextFloat())
        canvas.drawLine(Offset(0f, row), Offset(GRAIN_TILE.toFloat(), row), paint)
        repeat(3) {
            val start = random.nextFloat() * GRAIN_TILE
            val length = 20f + random.nextFloat() * 120f
            paint.color = if (random.nextBoolean()) colors.grainLight else colors.grainDark
            canvas.drawLine(Offset(start, row), Offset(start + length, row), paint)
            canvas.drawLine(Offset(start - GRAIN_TILE, row), Offset(start + length - GRAIN_TILE, row), paint)
        }
    }
    return bitmap
}

/** Brushed metal: a soft top-lit gradient under a fine horizontal grain. */
fun Modifier.brushedMetal(colors: AluminiumColors): Modifier = drawWithCache {
    val light = Brush.verticalGradient(listOf(colors.metalTop, colors.metalBottom))
    val grain = ShaderBrush(ImageShader(grainTile(colors), TileMode.Repeated, TileMode.Repeated))
    onDrawBehind {
        drawRect(light)
        drawRect(grain)
    }
}

/** A one-pixel machined edge: lit from above when raised, from below when [inset]. */
fun Modifier.bevel(shape: Shape, colors: AluminiumColors, inset: Boolean = false, width: Dp = 1.dp): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val (top, bottom) = if (inset) colors.shadow to colors.highlight else colors.highlight to colors.shadow
        val brush = Brush.verticalGradient(listOf(top, Color.Transparent, bottom))
        val stroke = Stroke(width.toPx())
        onDrawWithContent {
            drawContent()
            drawOutline(outline, brush, style = stroke)
        }
    }

/** A dark display window set into the metal, shaded along its top edge like a recess. */
fun Modifier.recessedPanel(shape: Shape, colors: AluminiumColors): Modifier =
    bevel(shape, colors, inset = true).drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val depth = Brush.verticalGradient(
            0f to colors.shadow,
            (10.dp.toPx() / size.height).coerceAtMost(1f) to Color.Transparent,
        )
        onDrawBehind {
            drawOutline(outline, colors.panel)
            drawOutline(outline, depth)
        }
    }

/**
 * A spun-aluminium knob face: radial light from the upper left, fine concentric turning rings, and
 * the four-lobed sheen that circular brushing throws back. [pressed] dims it as if pushed in.
 */
fun Modifier.spunKnob(colors: AluminiumColors, pressed: Boolean = false): Modifier = drawWithCache {
    val radius = size.minDimension / 2
    val face = Brush.radialGradient(
        listOf(if (pressed) colors.knobEdge else colors.knobCenter, colors.knobEdge),
        center = Offset(size.width * 0.4f, size.height * 0.35f),
        radius = radius * 1.4f,
    )
    val sheen = Brush.sweepGradient(
        listOf(colors.knobSheen, Color.Transparent, colors.knobSheen, Color.Transparent, colors.knobSheen),
        center = size.center,
    )
    val ringStroke = Stroke(1f)
    val ringStep = 3f
    onDrawBehind {
        drawCircle(face, radius)
        var r = ringStep
        while (r < radius) {
            drawCircle(colors.knobRing, r, style = ringStroke)
            r += ringStep
        }
        drawCircle(sheen, radius)
    }
}
