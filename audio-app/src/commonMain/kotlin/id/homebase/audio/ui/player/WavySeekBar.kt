package id.homebase.audio.ui.player

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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.ui.unit.dp

/**
 * Seek bar drawn with the expressive wavy indicator. The wave only moves while [animated], so a
 * paused track reads as paused. [onDrag] reports the fraction under the finger; [onSeek] fires once
 * when the finger lifts or taps.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavySeekBar(
    fraction: Float,
    animated: Boolean,
    enabled: Boolean,
    onDrag: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
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
        LinearWavyProgressIndicator(
            progress = { currentFraction },
            modifier = Modifier.fillMaxWidth(),
            amplitude = { if (animated && !dragging) 1f else 0f },
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
