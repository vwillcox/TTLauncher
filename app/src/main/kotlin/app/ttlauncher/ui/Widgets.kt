@file:OptIn(ExperimentalFoundationApi::class)

package app.ttlauncher.ui

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.ttlauncher.data.GRID_TIGHT_GAP_DP
import app.ttlauncher.data.WidgetConfig
import app.ttlauncher.data.WidgetFont
import app.ttlauncher.data.WidgetStyle
import app.ttlauncher.data.WidgetType
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

val WidgetFont.family: FontFamily
    get() = when (this) {
        WidgetFont.SANS -> FontFamily.SansSerif
        WidgetFont.SERIF -> FontFamily.Serif
        WidgetFont.MONO -> FontFamily.Monospace
        WidgetFont.CURSIVE -> FontFamily.Cursive
    }

/** One widget's own accent and emphasis, after its overrides of the skin are applied. */
@Immutable
private class WidgetLook(val accent: Color, val bold: Boolean)

private val LocalWidgetLook = compositionLocalOf { WidgetLook(Color.White, false) }

/** What pressing and holding the bars of an LCARS frame does. Either can be null. */
class BarActions(val onTap: (() -> Unit)?, val onHold: (() -> Unit)?)

/** Set where frame bars are buttons, which is the home page only. */
val LocalBarActions = compositionLocalOf<BarActions?> { null }

/** The skinnable frame every widget sits in. */
@Composable
fun WidgetFrame(
    style: WidgetStyle,
    modifier: Modifier = Modifier,
    contentColor: Color = LocalSkin.current.onSurface,
    accent: Color = LocalSkin.current.accent,
    /** Shown in the frame itself on skins whose frames carry a title. */
    label: String? = null,
    /** Small print for the next segment of the same bar, such as how old a story is. */
    note: String? = null,
    /** On skins whose frames have a direction, sweeps down and across the bottom instead of up and across the top. */
    down: Boolean = false,
    /** Keeps the frame as thin as it goes, for content that can't adapt to what the frame leaves it. */
    slim: Boolean = false,
    /** Another frame's bar runs into the corner this one sweeps round, so the two meet square, as a three-way junction. */
    junction: Boolean = false,
    /** The corner radius of content that is a rounded card of its own, which a frame with bars then curves them round. */
    hug: Dp? = null,
    content: @Composable () -> Unit,
) {
    val skin = LocalSkin.current
    if (skin.lcars && style != WidgetStyle.BARE) {
        LcarsFrame(modifier, label, note, contentColor, down, slim, junction, hug, content)
        return
    }
    val shape = RoundedCornerShape(skin.radius)
    val framed = when (style) {
        // Translucent rather than blurred: a live blur redraws the wallpaper on the GPU every frame.
        WidgetStyle.GLASS -> Modifier.background(skin.surface.copy(alpha = 0.42f), shape).border(1.dp, skin.onSurface.copy(alpha = 0.14f), shape)
        WidgetStyle.SOLID -> Modifier.background(skin.surface, shape)
        WidgetStyle.OUTLINE -> Modifier.background(skin.backdrop.copy(alpha = 0.25f), shape).border(1.5.dp, accent.copy(alpha = 0.8f), shape)
        WidgetStyle.BARE -> Modifier
    }
    Box(modifier.clip(shape).then(framed)) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}

/** How wide the bar down the side of every LCARS panel is. */
val LcarsBarWidth = 20.dp

/** The black line between an LCARS panel's bars and what's inside it. */
val LcarsContentGap = 2.dp

/** The colour of the bar down the side of the LCARS panel with this title. */
fun lcarsBarColor(label: String?): Color = LcarsPalette[(label ?: "").hashCode().mod(LcarsPalette.size)]

/**
 * An LCARS panel: a bar on the left that sweeps round the corner into a segmented bar across the
 * top, or with [down] across the bottom, with the panel's title set in the bar. Colours are picked
 * from the title, so each panel keeps its own.
 */
@Composable
private fun LcarsFrame(modifier: Modifier, label: String?, note: String?, contentColor: Color, down: Boolean, slim: Boolean, junction: Boolean, hug: Dp?, content: @Composable () -> Unit) {
    val skin = LocalSkin.current
    val first = lcarsBarColor(label)
    val seed = LcarsPalette.indexOf(first)
    val second = LcarsPalette[(seed + 2) % LcarsPalette.size]
    val third = LcarsPalette[(seed + 4) % LcarsPalette.size]
    BoxWithConstraints(modifier, propagateMinConstraints = true) {
    // The bar across and the sweep of the corner are as heavy as the panel has room for. A panel that takes
    // its height from what's inside it is judged on its width alone, and never gets the heaviest. The
    // bar down the side is the same width on every panel, so they line up into one column.
    val tall = if (constraints.hasFixedHeight) maxHeight else 140.dp
    val weight = if (slim) 0 else if (maxWidth >= 250.dp && tall >= 200.dp) 2 else if (maxWidth >= 150.dp && tall >= 130.dp) 1 else 0
    val side = LcarsBarWidth
    val top = listOf(16.dp, 18.dp, 22.dp)[weight]
    // Round a card the inside of the corner runs alongside the card's own, the gap between them the same all the way.
    val bend = hug?.plus(LcarsContentGap) ?: listOf(9.dp, 12.dp, 18.dp)[weight]
    val corner = if (hug != null) bend + top else listOf(22.dp, 34.dp, 52.dp)[weight]
    Box(
        // At a junction the side bar carries straight on into the next panel's, with the bar across branching off it.
        Modifier.clip(if (junction) RectangleShape else if (down) RoundedCornerShape(bottomStart = corner) else RoundedCornerShape(topStart = corner)).background(Color.Black).drawBehind {
            // Drawn as the upward elbow. The downward one is the same shape turned over.
            scale(1f, if (down) -1f else 1f) {
            val s = side.toPx()
            val t = top.toPx()
            val gap = GRID_TIGHT_GAP_DP.dp.toPx()
            val foot = (size.height * 0.3f).coerceAtMost(40.dp.toPx())
            val barBottom = size.height - foot - gap
            val outer = minOf(corner.toPx(), size.height / 2)
            val inner = bend.toPx().coerceAtMost(size.height - t - foot - gap).coerceAtLeast(0f)
            val firstEnd = size.width * 0.58f
            val secondEnd = size.width * 0.8f
            drawPath(
                Path().apply {
                    moveTo(0f, barBottom)
                    if (junction) lineTo(0f, 0f)
                    else {
                        lineTo(0f, outer)
                        arcTo(Rect(0f, 0f, outer * 2, outer * 2), 180f, 90f, false)
                    }
                    lineTo(firstEnd, 0f)
                    lineTo(firstEnd, t)
                    lineTo(s + inner, t)
                    arcTo(Rect(s, t, s + inner * 2, t + inner * 2), 270f, -90f, false)
                    lineTo(s, barBottom)
                    close()
                },
                first,
            )
            drawRect(second, Offset(firstEnd + gap, 0f), Size(secondEnd - firstEnd - gap, t))
            // The last segment ends in a half-round cap.
            val capStart = secondEnd + gap
            drawRoundRect(third, Offset(capStart, 0f), Size(size.width - capStart, t), CornerRadius(t / 2))
            drawRect(third, Offset(capStart, 0f), Size((size.width - capStart) / 2, t))
            drawRect(second, Offset(0f, barBottom + gap), Size(s, foot))
            }
        },
    ) {
        if (label != null) Box(
            Modifier.align(if (down) Alignment.BottomStart else Alignment.TopStart).fillMaxWidth(0.58f).height(top).padding(start = corner, end = 6.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                label.uppercase(), color = Color.Black, fontFamily = skin.font, fontWeight = FontWeight.Bold, maxLines = 1,
                fontSize = with(LocalDensity.current) { (top - 5.dp).toSp() }, lineHeight = with(LocalDensity.current) { (top - 4.dp).toSp() },
            )
        }
        // In the bar's second segment, which starts where the title's ends.
        if (note != null) Row(Modifier.align(if (down) Alignment.BottomStart else Alignment.TopStart).fillMaxWidth().height(top)) {
            Spacer(Modifier.weight(0.58f))
            Box(Modifier.weight(0.22f).fillMaxHeight().padding(horizontal = 5.dp), contentAlignment = Alignment.CenterEnd) {
                Text(
                    note.uppercase(), color = Color.Black, fontFamily = skin.font, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                    fontSize = with(LocalDensity.current) { (top - 7.dp).toSp() }, lineHeight = with(LocalDensity.current) { (top - 4.dp).toSp() },
                )
            }
            Spacer(Modifier.weight(0.2f))
        }
        Box(Modifier.padding(start = side + LcarsContentGap, top = if (down) 0.dp else top + LcarsContentGap, bottom = if (down) top + LcarsContentGap else 0.dp)) {
            CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
        }
        // The bars themselves are buttons where the page says so.
        val actions = LocalBarActions.current?.takeIf { it.onTap != null || it.onHold != null }
        if (actions != null) {
            val press = Modifier.combinedClickable(onClickLabel = "Bar", onLongClick = actions.onHold, onClick = actions.onTap ?: {})
            // Sized from the frame, never the other way round: left to themselves they would stretch it.
            Box(Modifier.matchParentSize()) {
                Box(Modifier.align(if (down) Alignment.BottomStart else Alignment.TopStart).fillMaxWidth().height(top).then(press))
                Box(Modifier.width(side).fillMaxHeight().then(press))
            }
        }
    }
    }
}

/** What an LCARS panel calls each widget, with a number to tell two of the same kind apart. */
private fun WidgetConfig.panelLabel(): String {
    val name = when (type) {
        WidgetType.CLOCK -> "Chrono"
        WidgetType.DATE -> "Date"
        WidgetType.BATTERY -> "Power"
        WidgetType.CALENDAR -> "Month"
        WidgetType.SEARCH -> "Query"
        WidgetType.SYSTEM -> "Aux"
        WidgetType.CASCADE -> "Data"
        WidgetType.MACRO -> "Macro"
        WidgetType.WEATHER -> "Atmos"
        WidgetType.MEDIA -> "Audio"
    }
    return "$name ${(id.hashCode().mod(90) + 10)}"
}

/**
 * A widget at whatever size its grid cell gives it. With [onSearch] null it is a still preview,
 * as shown in the widget picker.
 */
@Composable
fun WidgetItem(widget: WidgetConfig, modifier: Modifier, host: WidgetHostController?, onSearch: (() -> Unit)? = null, down: Boolean = false, junction: Boolean = false, onEdit: () -> Unit = {}) {
    val context = LocalContext.current
    val skin = LocalSkin.current
    val style = widget.style ?: skin.widgetStyle
    val accent = widget.accentColor?.let { Color(it) } ?: skin.accent
    val textColor = widget.textColor?.let { Color(it) } ?: skin.onSurface
    if (widget.type == WidgetType.SYSTEM) {
        val lcars = skin.lcars && style != WidgetStyle.BARE
        // Most widgets are cards rounded as the system says to round them. On LCARS the bars are drawn to fit one.
        val radius = with(LocalDensity.current) { context.resources.getDimension(android.R.dimen.system_app_widget_background_radius).toDp() }.coerceIn(8.dp, 28.dp)
        // Named for the app it comes from, where a built-in widget is named for what it shows.
        val label = host?.appName(widget)?.take(16) ?: widget.panelLabel()
        WidgetFrame(style, modifier, textColor, accent, label, down = down, slim = true, junction = junction, hug = radius) {
            // Cut to the same curve, so a widget with square corners doesn't lie over the bars' own.
            val corner = if (!lcars) RectangleShape else if (down) RoundedCornerShape(bottomStart = radius) else RoundedCornerShape(topStart = radius)
            if (host != null) HostedWidget(widget, host, Modifier.clip(corner), flush = lcars)
        }
        return
    }
    val interactive = if (onSearch == null) Modifier else Modifier.combinedClickable(
        onLongClick = onEdit,
        onClick = when (widget.type) {
            WidgetType.CLOCK -> { -> context.open(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
            WidgetType.DATE, WidgetType.CALENDAR -> { -> context.open(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)) }
            WidgetType.BATTERY -> { -> context.open(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }
            WidgetType.CASCADE, WidgetType.MACRO, WidgetType.MEDIA -> { -> }
            // Until it has a place, a press goes to where one is chosen.
            WidgetType.WEATHER -> { -> if (widget.place == null) onEdit() }
            else -> onSearch
        },
    )
    WidgetFrame(style, modifier.then(interactive), textColor, accent, widget.panelLabel(), down = down, junction = junction) {
        val text = LocalTextStyle.current.copy(
            fontFamily = widget.font?.family ?: skin.font,
            fontWeight = if (widget.bold) FontWeight.Bold else null,
        )
        CompositionLocalProvider(LocalWidgetLook provides WidgetLook(accent, widget.bold)) {
            ProvideTextStyle(text) {
                // Everything inside sizes itself from the room it has, so it fills any cell without overflowing.
                // An LCARS frame has already spent the left edge and the top or bottom one on its bars.
                val padding = if (skin.lcars && style != WidgetStyle.BARE) PaddingValues(start = 8.dp, top = if (down) 6.dp else 2.dp, end = 12.dp, bottom = if (down) 2.dp else 6.dp) else PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                BoxWithConstraints(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    when (widget.type) {
                        WidgetType.CLOCK -> ClockWidget(widget, maxWidth, maxHeight)
                        WidgetType.DATE -> DateWidget(widget, maxWidth, maxHeight)
                        WidgetType.BATTERY -> BatteryWidget(widget, maxWidth, maxHeight)
                        WidgetType.CALENDAR -> MonthWidget(widget, maxHeight)
                        WidgetType.CASCADE -> DataCascadeWidget(widget, accent)
                        WidgetType.MACRO -> MacroPadWidget(widget, accent, maxWidth, maxHeight, live = onSearch != null, onEdit)
                        WidgetType.WEATHER -> WeatherWidget(widget, accent, maxWidth, maxHeight, live = onSearch != null)
                        WidgetType.MEDIA -> MediaWidget(widget, accent, maxWidth, maxHeight, live = onSearch != null, onEdit)
                        else -> SearchWidget(widget)
                    }
                }
            }
        }
    }
}

private fun Context.open(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** A text size that takes up a fixed amount of screen, whatever the system font scale is. */
@Composable
private fun Dp.sp(): TextUnit = with(LocalDensity.current) { toSp() }

@Composable
private fun rememberNow() = rememberBroadcast(
    LocalDateTime.now(), Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
) { LocalDateTime.now() }

@Composable
private fun ClockWidget(widget: WidgetConfig, width: Dp, height: Dp) {
    val now by rememberNow()
    val look = LocalWidgetLook.current
    val twentyFour = widget.option("h24") ?: DateFormat.is24HourFormat(LocalContext.current)
    val showDate = widget.option("date") == true
    val showAmPm = !twentyFour && widget.option("ampm") == true
    val hour = now.format(DateTimeFormatter.ofPattern(if (twentyFour) "HH" else "h"))
    val minute = now.format(DateTimeFormatter.ofPattern("mm"))
    // The date line, when shown, takes a share of the height from the time.
    val room = if (showDate) height * 0.76f else height
    val faint = LocalContentColor.current.copy(alpha = 0.75f)

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when (widget.variant) {
            1 -> {
                val size = min(room / 2.3f, width / 1.5f).coerceIn(34.dp, 130.dp)
                Text(hour, fontSize = size.sp(), lineHeight = size.sp(), fontWeight = FontWeight.Black)
                Text(minute, fontSize = size.sp(), lineHeight = size.sp(), fontWeight = FontWeight.Black, color = look.accent)
            }
            2 -> {
                val color = LocalContentColor.current
                Canvas(Modifier.size(min(width, room))) {
                    val radius = size.minDimension / 2
                    drawCircle(color.copy(alpha = 0.25f), radius - 2.dp.toPx(), style = Stroke(2.dp.toPx()))
                    for (tick in 0 until 12) {
                        val angle = Math.toRadians(tick * 30.0)
                        val direction = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
                        drawLine(color.copy(alpha = 0.6f), center + direction * (radius * 0.84f), center + direction * (radius * 0.92f), 2.dp.toPx(), StrokeCap.Round)
                    }
                    fun hand(turn: Float, length: Float, thickness: Float, tint: Color) {
                        val angle = Math.toRadians(turn * 360.0)
                        drawLine(tint, center, center + Offset(sin(angle).toFloat(), -cos(angle).toFloat()) * (radius * length), thickness, StrokeCap.Round)
                    }
                    hand((now.hour % 12 + now.minute / 60f) / 12f, 0.5f, radius * 0.07f, color)
                    hand(now.minute / 60f, 0.76f, radius * 0.045f, look.accent)
                    drawCircle(look.accent, radius * 0.06f)
                }
            }
            else -> {
                val size = min(room * 0.8f, width / if (showAmPm) 3.9f else 3.1f).coerceIn(26.dp, 130.dp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$hour:$minute", fontSize = size.sp(), lineHeight = size.sp(), fontWeight = if (look.bold) FontWeight.Bold else FontWeight.Light, maxLines = 1)
                    if (showAmPm) Text(
                        now.format(DateTimeFormatter.ofPattern("a")),
                        Modifier.padding(start = size * 0.08f, bottom = size * 0.16f), color = look.accent, fontSize = (size * 0.26f).sp(), maxLines = 1,
                    )
                }
            }
        }
        if (showDate) Text(
            now.format(DateTimeFormatter.ofPattern("EEE d MMM")).cased(), color = faint, maxLines = 1,
            fontSize = (height * 0.14f).coerceIn(12.dp, 22.dp).sp(),
        )
    }
}

@Composable
private fun DateWidget(widget: WidgetConfig, width: Dp, height: Dp) {
    val now by rememberNow()
    val look = LocalWidgetLook.current
    val short = widget.option("short") == true
    val year = widget.option("year") == true
    val weekday = now.format(DateTimeFormatter.ofPattern(if (short) "EEE" else "EEEE")).cased()
    val month = now.format(DateTimeFormatter.ofPattern(if (short) "MMM" else "MMMM")).cased()
    if (widget.variant == 1) {
        val number = min(height * 0.9f, width * 0.3f).coerceIn(28.dp, 110.dp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(number * 0.25f)) {
            Text(now.dayOfMonth.toString(), fontSize = number.sp(), lineHeight = number.sp(), fontWeight = FontWeight.Bold, color = look.accent, maxLines = 1)
            Column {
                val title = (number * 0.38f).coerceIn(14.dp, 30.dp)
                Text(weekday, fontSize = title.sp(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (year) "$month ${now.year}" else month, fontSize = (title * 0.74f).sp(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
            }
        }
    } else {
        val line = "$weekday, ${now.dayOfMonth} $month" + if (year) " ${now.year}" else ""
        // Sized so the whole line fits the width it has.
        val size = min(height * 0.6f, width / (line.length * 0.54f)).coerceIn(11.dp, 34.dp)
        Text(line, fontSize = size.sp(), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

private class Battery(val percent: Int, val charging: Boolean)

@Composable
private fun BatteryWidget(widget: WidgetConfig, width: Dp, height: Dp) {
    val battery by rememberBroadcast<Battery?>(null, Intent.ACTION_BATTERY_CHANGED) { intent ->
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (level < 0 || scale <= 0) null
        else Battery(level * 100 / scale, intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0)
    }
    val look = LocalWidgetLook.current
    val percent = battery?.percent ?: 0
    val charging = battery?.charging == true
    val text = battery?.let { "${it.percent}%" } ?: "–"
    val track = LocalContentColor.current.copy(alpha = 0.16f)
    val fill = if (battery != null && percent <= 15 && !charging) Color(0xFFFF5C5C) else look.accent
    val faint = LocalContentColor.current.copy(alpha = 0.7f)
    val showStatus = widget.option("status") == true
    val status = (if (charging) "Charging" else "Battery").cased()
    if (widget.variant == 1) {
        val ring = min(width, height)
        Box(Modifier.size(ring), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = size.minDimension * 0.08f
                val inset = Offset(stroke / 2, stroke / 2)
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(track, -90f, 360f, false, inset, arc, style = Stroke(stroke))
                drawArc(fill, -90f, 3.6f * percent, false, inset, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text, fontSize = (ring * 0.2f).sp(), fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (showStatus) Text(status, fontSize = (ring * 0.085f).coerceAtLeast(10.dp).sp(), color = faint, maxLines = 1)
            }
        }
    } else {
        val big = (height * 0.42f).coerceIn(18.dp, 40.dp)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy((height * 0.12f).coerceIn(4.dp, 14.dp))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, fontSize = big.sp(), lineHeight = big.sp(), fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                if (charging) Icon(Icons.Rounded.Bolt, null, Modifier.size(big * 0.7f), tint = look.accent)
                Spacer(Modifier.weight(1f))
                if (showStatus) Text(status, fontSize = (big * 0.5f).coerceIn(12.dp, 16.dp).sp(), color = faint, maxLines = 1)
            }
            Box(Modifier.fillMaxWidth().height((height * 0.14f).coerceIn(6.dp, 14.dp)).clip(CircleShape).background(track)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(percent / 100f).background(fill, CircleShape))
            }
        }
    }
}

@Composable
private fun MonthWidget(widget: WidgetConfig, height: Dp) {
    val now by rememberNow()
    val look = LocalWidgetLook.current
    val today = now.toLocalDate()
    val firstDay = when (widget.option("monday")) {
        true -> DayOfWeek.MONDAY
        false -> DayOfWeek.SUNDAY
        null -> WeekFields.of(Locale.getDefault()).firstDayOfWeek
    }
    val showTitle = widget.option("title") == true
    val month = YearMonth.from(today)
    // Blank cells before the 1st, so it lands under its weekday.
    val lead = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val weeks = (lead + month.lengthOfMonth() + 6) / 7
    val titleHeight = if (showTitle) 26.dp else 0.dp
    val cell = ((height - titleHeight) / (weeks + 1)).coerceIn(18.dp, 56.dp)
    val digits = (cell * 0.46f).coerceIn(10.dp, 18.dp).sp()
    val faint = LocalContentColor.current.copy(alpha = 0.6f)
    val onAccent = if (look.accent.red * 0.3f + look.accent.green * 0.59f + look.accent.blue * 0.11f > 0.5f) Color(0xFF111111) else Color.White
    Column(Modifier.fillMaxWidth()) {
        if (showTitle) Text(
            today.format(DateTimeFormatter.ofPattern("MMMM yyyy")).cased(), Modifier.height(titleHeight), fontSize = 15.dp.sp(), fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
        Row(Modifier.fillMaxWidth().height(cell), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until 7) {
                val day = DayOfWeek.of((firstDay.value - 1 + i) % 7 + 1)
                Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault()), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = digits, color = faint)
            }
        }
        for (week in 0 until weeks) {
            Row(Modifier.fillMaxWidth().height(cell)) {
                for (slot in 0 until 7) {
                    val day = week * 7 + slot - lead + 1
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (day in 1..month.lengthOfMonth()) {
                            val isToday = day == today.dayOfMonth
                            if (isToday) Box(Modifier.size(cell * 0.92f).background(look.accent, CircleShape))
                            Text(
                                day.toString(), fontSize = digits, maxLines = 1,
                                fontWeight = if (isToday) FontWeight.Bold else null,
                                color = if (isToday) onAccent else LocalContentColor.current,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchWidget(widget: WidgetConfig) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.Search, null, tint = LocalWidgetLook.current.accent)
        Text(widget.text.ifBlank { "Search apps" }.cased(), fontSize = 16.dp.sp(), color = LocalContentColor.current.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
