package org.opensapien.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.opensapien.app.ui.theme.LocalReduceMotion
import kotlin.math.roundToInt

/**
 * Scrolling microphone level meter — the app's signature element.
 *
 * Each tick is one real amplitude sample from [level], newest on the right. Nothing here is
 * decorative: if the flow stops, the meter stops. When the user has switched animations off in
 * system settings this collapses to a single static bar showing the current level instead.
 *
 * @param level current loudness in `0f..1f`.
 * @param active false when idle, which drains the meter to silence.
 */
@Composable
fun LevelMeter(
    level: Float,
    active: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
    barColor: Color = MaterialTheme.colorScheme.primary,
    troughColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val reduceMotion = LocalReduceMotion.current
    val label = "Microphone level ${(level * 100).roundToInt()} percent"

    if (reduceMotion) {
        StaticLevelBar(level, active, modifier, height, barColor, troughColor, label)
        return
    }

    val history = remember { ArrayDeque<Float>(HISTORY) }
    var revision by remember { mutableStateOf(0) }

    // Sample on a fixed cadence so the meter scrolls at a constant speed regardless of
    // how often the audio flow happens to emit.
    LaunchedEffect(active) {
        if (!active) {
            history.clear()
            revision++
            return@LaunchedEffect
        }
        while (true) {
            if (history.size >= HISTORY) history.removeFirst()
            history.addLast(level)
            revision++
            kotlinx.coroutines.delay(FRAME_MS)
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") revision // redraw when a sample lands

            val slot = size.width / HISTORY
            val barWidth = (slot * 0.55f).coerceAtLeast(1f)
            val midY = size.height / 2f
            val maxHalf = size.height / 2f

            // Baseline: a hairline so silence still reads as "listening", not "broken".
            drawLine(
                color = troughColor,
                start = Offset(0f, midY),
                end = Offset(size.width, midY),
                strokeWidth = 1f,
            )

            history.forEachIndexed { i, v ->
                // Oldest samples fade out, giving the scroll a direction without motion blur.
                val age = i.toFloat() / HISTORY
                val half = (v.coerceIn(0f, 1f) * maxHalf).coerceAtLeast(1f)
                val x = i * slot + slot / 2f
                drawLine(
                    color = barColor.copy(alpha = 0.25f + 0.75f * age),
                    start = Offset(x, midY - half),
                    end = Offset(x, midY + half),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun StaticLevelBar(
    level: Float,
    active: Boolean,
    modifier: Modifier,
    height: Dp,
    barColor: Color,
    troughColor: Color,
    label: String,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val midY = size.height / 2f
            val barH = size.height * 0.4f
            drawLine(
                color = troughColor,
                start = Offset(0f, midY),
                end = Offset(size.width, midY),
                strokeWidth = barH,
                cap = StrokeCap.Round,
            )
            if (active) {
                drawLine(
                    color = barColor,
                    start = Offset(0f, midY),
                    end = Offset(size.width * level.coerceIn(0f, 1f), midY),
                    strokeWidth = barH,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private const val HISTORY = 64
private const val FRAME_MS = 60L
