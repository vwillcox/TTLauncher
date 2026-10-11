package app.ttlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.ttlauncher.data.WidgetConfig
import kotlinx.coroutines.delay
import kotlin.random.Random

/** How many steps a finished table stays up before the next one starts. */
private const val HOLD_STEPS = 16
private const val STEP_MILLIS = 160L

/**
 * Columns of figures that fill in a row at a time, then start again with new ones. Decoration only:
 * the figures mean nothing. Each step redraws the canvas without recomposing anything, and the
 * steps stop whenever the launcher isn't in front.
 */
@Composable
fun DataCascadeWidget(widget: WidgetConfig, accent: Color) {
    val skin = LocalSkin.current
    val animate = widget.option("animate") != false
    val content = LocalContentColor.current
    val size = with(LocalDensity.current) { 11.dp.toSp() }
    val style = LocalTextStyle.current.copy(fontSize = size, lineHeight = size, fontWeight = FontWeight.Bold)
    // Room for a whole table, so a step lays out no text it has drawn before.
    val measurer = rememberTextMeasurer(cacheSize = 200)
    var tick by remember { mutableIntStateOf(0) }
    if (animate) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(STEP_MILLIS)
                    tick++
                }
            }
        }
    }
    val seed = widget.id.hashCode()
    Canvas(Modifier.fillMaxSize()) {
        val rowHeight = 14.dp.toPx()
        val rows = (this.size.height / rowHeight).toInt().coerceAtLeast(1)
        val columns = (this.size.width / 40.dp.toPx()).toInt().coerceAtLeast(1)
        val cell = this.size.width / columns
        val top = (this.size.height - rows * rowHeight) / 2
        val cycle = rows + HOLD_STEPS
        // The row being written now. Standing still, every row is already there.
        val lead = if (animate) tick % cycle else rows
        val table = if (animate) tick / cycle else 0
        val figures = Random(seed + table)
        for (row in 0 until rows) {
            val colour = when {
                row == lead -> if (skin.lcars) Color(0xFFFFF4E6) else content
                skin.lcars -> LcarsPalette[(row * 3 + table) % LcarsPalette.size]
                row % 3 == 0 -> accent
                else -> content.copy(alpha = 0.55f)
            }
            for (column in 0 until columns) {
                // Drawn or not, every figure is taken, so the rows already up don't change as more arrive.
                val digits = figures.nextInt(1, 7)
                val figure = figures.nextInt(1_000_000).toString().take(digits)
                if (row > lead || figures.nextInt(8) == 0) continue
                val laid = measurer.measure(figure, style, maxLines = 1)
                drawText(laid, colour, Offset((column + 1) * cell - laid.size.width - 5.dp.toPx(), top + row * rowHeight + (rowHeight - laid.size.height) / 2))
            }
        }
    }
}
