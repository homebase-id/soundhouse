package id.homebase.soundhouse.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import id.homebase.soundhouse.ui.theme.LocalAluminium
import id.homebase.soundhouse.ui.theme.bevel
import id.homebase.soundhouse.ui.theme.recessedPanel
import id.homebase.soundhouse.ui.theme.spunKnob

/** [onDrag] reports the fraction under the finger; [onSeek] fires once when the finger lifts or taps. */
@Composable
fun SeekBar(
    fraction: Float,
    enabled: Boolean,
    onDrag: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    machined: Boolean = false,
) {
    val currentFraction by rememberUpdatedState(fraction.coerceIn(0f, 1f))
    var dragging by remember { mutableStateOf(false) }
    val thumbSize by animateDpAsState(if (dragging) 20.dp else 14.dp)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(currentFraction, 0f..1f)
                if (enabled) {
                    setProgress { target ->
                        onSeek(target.coerceIn(0f, 1f))
                        true
                    }
                }
            }
            .then(
                if (!enabled) Modifier
                else Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { offset -> onSeek((offset.x / size.width).coerceIn(0f, 1f)) }
                    }
                    .pointerInput(Unit) {
                        var last = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                dragging = true
                                last = (offset.x / size.width).coerceIn(0f, 1f)
                                onDrag(last)
                            },
                            onDragEnd = {
                                dragging = false
                                onDrag(null)
                                onSeek(last)
                            },
                            onDragCancel = {
                                dragging = false
                                onDrag(null)
                            },
                        ) { change, _ ->
                            last = (change.position.x / size.width).coerceIn(0f, 1f)
                            onDrag(last)
                        }
                    }
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (machined) {
            MachinedGroove(currentFraction, maxWidth * currentFraction - thumbSize / 2, thumbSize)
            return@BoxWithConstraints
        }
        LinearProgressIndicator(
            progress = { currentFraction },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            drawStopIndicator = {},
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 2.dp,
            modifier = Modifier
                .offset(x = maxWidth * currentFraction - thumbSize / 2)
                .size(thumbSize),
        ) {}
    }
}

/** The aluminium skin's track: a groove cut into the metal, lit amber up to the playhead, under a small knob. */
@Composable
private fun MachinedGroove(fraction: Float, thumbOffset: Dp, thumbSize: Dp) {
    val metal = LocalAluminium.current
    val groove = RoundedCornerShape(4.dp)
    val lit = MaterialTheme.colorScheme.tertiary
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .recessedPanel(groove, metal)
            .drawBehind {
                val inset = 2.dp.toPx()
                drawRoundRect(
                    color = lit,
                    topLeft = Offset(inset, inset),
                    size = Size((size.width - 2 * inset) * fraction, size.height - 2 * inset),
                    cornerRadius = CornerRadius(inset),
                )
            },
    )
    Box(
        Modifier
            .offset(x = thumbOffset)
            .size(thumbSize + 4.dp)
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .spunKnob(metal)
            .bevel(CircleShape, metal),
    )
}
