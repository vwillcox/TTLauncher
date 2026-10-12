@file:OptIn(ExperimentalFoundationApi::class)

package app.ttlauncher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.ttlauncher.data.WidgetConfig
import app.ttlauncher.health.HealthAccess
import app.ttlauncher.health.HealthRepo
import app.ttlauncher.health.Vitals
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/** Where health widgets get their figures. Null where there is nothing to read with, as in a preview. */
val LocalHealth = staticCompositionLocalOf<HealthRepo?> { null }

/** Shown in the picker. */
private val SampleVitals = Vitals(steps = 6482, pulse = 72, pulseLow = 54, pulseHigh = 128, sleepMinutes = 432, active = 412.0, burned = 2105.0, metres = 4900.0)

/** The steps the bar under the count fills up to. */
private const val STEP_GOAL = 10_000

/**
 * Today's steps, large, with a bar towards the day's goal, and beside them the pulse, last night's
 * sleep, energy burned and distance covered, as many as there is room for, and along the bottom of
 * a taller one a heart monitor's trace. Until it is allowed to
 * read them, a press asks.
 */
@Composable
fun HealthWidget(widget: WidgetConfig, accent: Color, width: Dp, height: Dp, live: Boolean, onEdit: () -> Unit) {
    val skin = LocalSkin.current
    val repo = LocalHealth.current.takeIf { live }
    val faint = LocalContentColor.current.copy(alpha = 0.7f)
    val vitals: Vitals
    var press: Modifier = Modifier
    var more = false
    if (repo == null) vitals = SampleVitals
    else {
        val scope = rememberCoroutineScope()
        val access by repo.access.collectAsStateWithLifecycle()
        val read by repo.vitals.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        // Read on the way back to the launcher, and now and then while it stays in front.
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    repo.refresh()
                    delay(5 * 60 * 1000L)
                }
            }
        }
        val ask = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { scope.launch { repo.refresh(force = true) } }
        press = Modifier.combinedClickable(
            onClickLabel = if (access == HealthAccess.NOT_ALLOWED) "Allow health data" else "Read again", onLongClick = onEdit,
            onClick = { if (access == HealthAccess.NOT_ALLOWED) runCatching { ask.launch(repo.permissions) } else scope.launch { repo.refresh(force = true) } },
        )
        val note = when (access) {
            HealthAccess.UNAVAILABLE -> "Health Connect isn't set up on this phone"
            HealthAccess.NOT_ALLOWED -> "Tap to allow health data"
            else -> if (read == null) "Reading" else null
        }
        if (note != null) {
            Text(note.cased(), Modifier.fillMaxSize().then(press).padding(8.dp), color = faint, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            return
        }
        vitals = read ?: return
        more = repo.partial.collectAsStateWithLifecycle().value
    }

    val miles = widget.option("miles") ?: weatherDefault("mph")
    val gap = if (skin.lcars) 3.dp else 6.dp
    val all = listOfNotNull(
        vitals.pulse?.let { "Pulse" to "$it BPM" },
        vitals.sleepMinutes?.let { "Sleep" to "${it / 60}H ${"%02d".format(it % 60)}M" },
        vitals.active?.let { "Active" to "${it.roundToInt()} KCAL" },
        vitals.metres?.let { "Range" to if (miles) String.format(Locale.getDefault(), "%.1f MI", it / 1609.344) else String.format(Locale.getDefault(), "%.1f KM", it / 1000) },
        // The rest are for a widget with the room, in rough order of how often they change.
        vitals.burned?.let { "Burned" to String.format(Locale.getDefault(), "%,d KCAL", it.roundToInt()) },
        vitals.exerciseMinutes?.let { "Workout" to if (it >= 60) "${it / 60}H ${"%02d".format(it % 60)}M" else "$it MIN" },
        vitals.floors?.let { "Floors" to it.roundToInt().toString() },
        vitals.pulseAverage?.let { "Average" to "$it BPM" },
        vitals.pulseResting?.let { "Resting" to "$it BPM" },
        vitals.oxygen?.let { "Oxygen" to "${it.roundToInt()}%" },
        vitals.water?.let { "Water" to String.format(Locale.getDefault(), "%.1f L", it) },
        vitals.kilograms?.let { "Mass" to if (Locale.getDefault().country == "US") "${(it * 2.20462).roundToInt()} LB" else String.format(Locale.getDefault(), "%.1f KG", it) },
    )
    val steps = String.format(Locale.getDefault(), "%,d", vitals.steps ?: 0)
    val done = ((vitals.steps ?: 0).toFloat() / STEP_GOAL).coerceIn(0f, 1f)
    val range = vitals.pulseLow?.let { low -> vitals.pulseHigh?.let { high -> "Pulse $low to $high" } }
    val traced = widget.option("trace") != false
    val animate = live && widget.option("animate") != false

    when {
        // At its smallest, the one figure that matters most, with the pulse beside it if that fits.
        height < 46.dp -> Row(Modifier.fillMaxSize().then(press), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val big = (height * 0.8f).coerceIn(14.dp, 30.dp)
            Text(steps, color = accent, fontSize = big.sp(), lineHeight = big.sp(), fontWeight = FontWeight.Bold, maxLines = 1)
            if (width >= 96.dp) Text("Steps".cased(), Modifier.weight(1f), color = faint, fontSize = (big * 0.5f).coerceAtLeast(10.dp).sp(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            vitals.pulse?.takeIf { width >= 170.dp }?.let { Text("$it BPM", fontSize = (big * 0.6f).coerceAtLeast(11.dp).sp(), fontWeight = FontWeight.Bold, maxLines = 1) }
        }
        // With a page's worth of height, everything, one thing under another, and the trace given all that's left.
        height >= 250.dp -> Column(Modifier.fillMaxSize().then(press), verticalArrangement = Arrangement.spacedBy(gap * 2)) {
            val big = min(height * 0.15f, width * 0.22f).coerceIn(40.dp, 128.dp)
            val small = (big * 0.24f).coerceIn(13.dp, 24.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(big * 0.2f), verticalAlignment = Alignment.CenterVertically) {
                Text(steps, color = accent, fontSize = big.sp(), lineHeight = big.sp(), fontWeight = FontWeight.Bold, maxLines = 1)
                Column {
                    Text("Steps today".cased(), fontSize = small.sp(), lineHeight = (small * 1.15f).sp(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${(done * 100).roundToInt()}% of ${String.format(Locale.getDefault(), "%,d", STEP_GOAL)}".cased(), color = faint, fontSize = (small * 0.86f).sp(), lineHeight = small.sp(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (range != null) Text(range.cased(), color = faint, fontSize = (small * 0.86f).sp(), lineHeight = small.sp(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            StepBar(done, accent, Modifier.fillMaxWidth().height(if (skin.lcars) 12.dp else 6.dp))
            // Two to a row where the width allows, each no taller than reads well.
            val across = if (width >= 480.dp) 3 else if (width >= 230.dp) 2 else 1
            val block = (height * 0.075f).coerceIn(28.dp, 54.dp)
            // As many rows of them as leave the trace a strip worth having.
            val spare = height - big * 1.2f - 40.dp - if (traced) 96.dp else 0.dp
            val rows = (spare / (block + gap * 2)).toInt().coerceAtLeast(1)
            all.take(rows * across).chunked(across).forEachIndexed { row, pair ->
                Row(Modifier.fillMaxWidth().height(block), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    pair.forEachIndexed { column, (name, value) -> ReadoutBlock(name, value, row * across + column, accent, Modifier.weight(1f)) }
                    repeat(across - pair.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (traced) HeartTrace(vitals.pulse, accent, animate, Modifier.fillMaxWidth().weight(1f))
            if (more) Text("More readings can be allowed under Customise".cased(), color = faint, fontSize = 11.dp.sp(), lineHeight = 12.dp.sp(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        else -> {
            // The trace runs along the bottom where there is the height for it.
            val showTrace = traced && height >= 128.dp
            val traceHeight = if (!showTrace) 0.dp else (height * 0.34f).coerceIn(40.dp, 84.dp)
            val top = if (!showTrace) height else height - traceHeight - gap
            val readouts = all.take((top / 22.dp).toInt().coerceIn(2, 4))
            Column(Modifier.fillMaxSize().then(press), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val big = min(top * 0.42f, width * 0.17f).coerceIn(22.dp, 64.dp)
                        val small = (top * 0.13f).coerceIn(11.dp, 16.dp)
                        Text(steps, color = accent, fontSize = big.sp(), lineHeight = big.sp(), fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("Steps today".cased(), fontSize = small.sp(), lineHeight = (small * 1.15f).sp(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // The whole range of the day's pulse, where there is the height to say it.
                        if (top >= 110.dp && range != null) Text(range.cased(), color = faint, fontSize = (small * 0.86f).sp(), lineHeight = small.sp(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (top >= 64.dp) StepBar(done, accent, Modifier.fillMaxWidth().padding(top = 5.dp).height(if (skin.lcars) 8.dp else 4.dp))
                    }
                    if (width >= 210.dp && readouts.isNotEmpty()) Column(Modifier.width((width * 0.44f).coerceIn(104.dp, 176.dp)).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                        readouts.forEachIndexed { index, (name, value) -> ReadoutBlock(name, value, index, accent, Modifier.weight(1f)) }
                    }
                }
                if (showTrace) HeartTrace(vitals.pulse, accent, animate, Modifier.fillMaxWidth().height(traceHeight))
            }
        }
    }
}

/** How far towards the day's goal, as a row of LCARS segments or a plain bar. */
@Composable
private fun StepBar(done: Float, accent: Color, modifier: Modifier) {
    val skin = LocalSkin.current
    val track = LocalContentColor.current.copy(alpha = 0.18f)
    Canvas(modifier) {
        if (!skin.lcars) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(track, size = size, cornerRadius = radius)
            drawRoundRect(accent, size = Size(size.width * done, size.height), cornerRadius = radius)
        } else {
            val count = (size.width / 9.dp.toPx()).toInt().coerceAtLeast(4)
            val each = size.width / count
            for (i in 0 until count) {
                drawRect(if ((i + 0.5f) / count <= done) LcarsPalette[(i / 4) % LcarsPalette.size] else track, Offset(i * each, 0f), Size(each - 2.dp.toPx(), size.height))
            }
        }
    }
}

/** One heartbeat as a monitor draws it, as points of (how far through the beat, how high): the small P wave, the spike, and the T wave after it. */
private val Beat = listOf(
    0f to 0f, 0.10f to 0f, 0.15f to 0.12f, 0.20f to 0f, 0.27f to 0f, 0.29f to -0.12f, 0.32f to 1f, 0.35f to -0.25f, 0.38f to 0f,
    0.50f to 0f, 0.57f to 0.22f, 0.65f to 0f, 1f to 0f,
)

/** How fast the trace is written across, a second. */
private val TraceSpeed = 44.dp
private const val TRACE_STEP_MILLIS = 50L

/** The rate the trace is drawn at when there is no pulse to draw it from: an ordinary heart at rest. */
private const val RESTING_PULSE = 64L

/**
 * A heart monitor's trace: beats written across a grid by a bright point that leaves them fading
 * behind it and starts again at the left. The beats are spaced as [pulse] spaces them, so a faster
 * heart crowds them together. It is drawn from the pulse figure and is no recording of the heart
 * itself, which Health Connect doesn't have. With no [pulse] at all the monitor still runs, at a
 * resting rate and in a duller line, and says "No data" where the figure would be, so that nobody
 * takes it for theirs. Each step redraws the canvas alone, and the steps stop
 * whenever the launcher isn't in front.
 */
@Composable
private fun HeartTrace(pulse: Long?, accent: Color, animate: Boolean, modifier: Modifier) {
    val skin = LocalSkin.current
    val rate = pulse ?: RESTING_PULSE
    @Suppress("NAME_SHADOWING") val accent = if (pulse != null) accent else LocalContentColor.current.copy(alpha = 0.55f)
    val grid = LocalContentColor.current.copy(alpha = 0.13f)
    var step by remember { mutableIntStateOf(0) }
    if (animate) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(TRACE_STEP_MILLIS)
                    step++
                }
            }
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (skin.lcars) 3.dp else 6.dp)) {
        // The figure it is drawn from, on a block that caps the strip.
        Box(
            Modifier.width(46.dp).fillMaxHeight().background(
                if (skin.lcars) LcarsPalette[4].copy(alpha = if (pulse != null) 1f else 0.6f) else accent.copy(alpha = 0.2f),
                if (skin.lcars) RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50) else RoundedCornerShape(10.dp),
            ),
            contentAlignment = Alignment.BottomEnd,
        ) {
            val size = if (pulse != null) 18.dp else 12.dp
            Text(
                pulse?.toString() ?: "No\ndata".cased(), Modifier.padding(end = 6.dp, bottom = 2.dp), color = if (skin.lcars) Color.Black else LocalContentColor.current,
                fontSize = size.sp(), lineHeight = (size + 1.dp).sp(), fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.End,
            )
        }
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            // A big strip is drawn larger all round, grid and beats alike, and not just given more of them.
            val scale = (size.height / 60.dp.toPx()).coerceIn(1f, 2.5f)
            val cell = 12.dp.toPx() * scale
            var x = 0f
            while (x <= size.width) {
                drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
                x += cell
            }
            var y = size.height / 2 % cell
            while (y <= size.height) {
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
                y += cell
            }
            // A beat takes as long as the pulse says, and so as much of the strip as is written in that time.
            val speed = TraceSpeed.toPx() * scale
            val beat = speed * 60f / rate.coerceIn(30, 220)
            val base = size.height * 0.62f
            val reach = size.height * 0.5f
            val line = Path()
            var start = 0f
            line.moveTo(0f, base)
            while (start < size.width) {
                Beat.forEach { (at, high) -> line.lineTo(start + at * beat, base - high * reach) }
                start += beat
            }
            val stroke = Stroke((size.height / 36f).coerceIn(2.dp.toPx(), 4.dp.toPx()), cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (!animate) {
                drawPath(line, accent, style = stroke)
                return@Canvas
            }
            val head = (step * TRACE_STEP_MILLIS / 1000f * speed) % size.width
            val blank = 18.dp.toPx()
            // What was written on the last pass, dim, up to the gap the point clears ahead of itself.
            clipRect(left = head + blank) { drawPath(line, accent.copy(alpha = 0.3f), style = stroke) }
            // What this pass has written, brightening towards the point.
            clipRect(right = head) {
                drawPath(line, Brush.horizontalGradient(listOf(accent.copy(alpha = 0.3f), accent), startX = (head - size.width * 0.5f).coerceAtLeast(0f), endX = head), style = stroke)
            }
            val through = (head % beat) / beat
            val after = Beat.indexOfFirst { it.first >= through }.coerceAtLeast(1)
            val (fromAt, fromHigh) = Beat[after - 1]
            val (toAt, toHigh) = Beat[after]
            val high = fromHigh + (toHigh - fromHigh) * ((through - fromAt) / (toAt - fromAt).coerceAtLeast(0.0001f))
            drawCircle(if (skin.lcars) LcarsHighlight else accent, stroke.width * 1.5f, Offset(head, base - high * reach))
        }
    }
}

@Composable
private fun Dp.sp() = with(LocalDensity.current) { toSp() }
