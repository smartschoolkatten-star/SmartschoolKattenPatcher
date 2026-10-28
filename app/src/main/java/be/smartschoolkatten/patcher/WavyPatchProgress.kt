package be.smartschoolkatten.patcher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun WavyPatchProgress(progress: Float, modifier: Modifier = Modifier) {
    val fraction = progress.coerceIn(0f, 1f)
    val displayed by animateFloatAsState(fraction, tween(350), label = "Patchvoortgang")
    val transition = rememberInfiniteTransition(label = "Golvende patchbalk")
    val phase by transition.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "Golfbeweging"
    )
    val active = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.secondaryContainer
    Column(modifier) {
        Text("${(fraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
        Canvas(Modifier.fillMaxWidth().height(24.dp).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        }) {
            val thickness = 4.dp.toPx()
            val amplitude = 3.dp.toPx()
            val wavelength = 24.dp.toPx()
            val start = thickness / 2
            val end = size.width - start
            val filled = start + (end - start) * displayed
            val gap = 6.dp.toPx()
            if (filled + gap < end) drawLine(track, Offset(filled + gap, size.height / 2),
                Offset(end, size.height / 2), thickness, StrokeCap.Round)
            if (displayed > 0f) {
                val wave = Path()
                var x = start
                while (x < filled) {
                    val y = size.height / 2 + amplitude * sin((x / wavelength * 2 * PI).toFloat() - phase)
                    if (x == start) wave.moveTo(x, y) else wave.lineTo(x, y)
                    x += 1.dp.toPx()
                }
                wave.lineTo(filled, size.height / 2 + amplitude * sin((filled / wavelength * 2 * PI).toFloat() - phase))
                drawPath(wave, active, style = Stroke(thickness, cap = StrokeCap.Round))
            }
            drawCircle(active, thickness / 2, Offset(end, size.height / 2))
        }
    }
}
