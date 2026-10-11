package app.ttlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.ttlauncher.data.WidgetConfig
import app.ttlauncher.weather.DayForecast
import app.ttlauncher.weather.Weather
import app.ttlauncher.weather.WeatherRepo
import app.ttlauncher.weather.conditionOf
import app.ttlauncher.weather.key
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Where weather widgets get their reports. Null where there is nothing to fetch with, as in a preview. */
val LocalWeather = staticCompositionLocalOf<WeatherRepo?> { null }

/** Shown in the picker, and dimmed on a new widget until it has a place of its own. */
private val SampleWeather = Weather(
    0, 14.0, 12.0, 71, 18.0, 2,
    days = listOf(DayForecast("2026-01-05", 2, 15.0, 8.0), DayForecast("2026-01-06", 61, 12.0, 7.0), DayForecast("2026-01-07", 3, 11.0, 5.0), DayForecast("2026-01-08", 0, 13.0, 4.0), DayForecast("2026-01-09", 80, 12.0, 6.0)),
)

private val FahrenheitCountries = setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")
private val MphCountries = setOf("US", "GB", "LR", "MM")

/** Whether a weather option that follows the phone is on where the phone is set to. */
fun weatherDefault(key: String): Boolean = Locale.getDefault().country in if (key == "mph") MphCountries else FahrenheitCountries

private fun conditionIcon(code: Int, day: Boolean): ImageVector = when (code) {
    0, 1 -> if (day) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    2 -> Icons.Rounded.WbCloudy
    3 -> Icons.Rounded.Cloud
    45, 48 -> Icons.Rounded.Dehaze
    in 71..77, 85, 86 -> Icons.Rounded.AcUnit
    in 95..99 -> Icons.Rounded.FlashOn
    in 51..82 -> Icons.Rounded.Grain
    else -> Icons.Rounded.Cloud
}

/**
 * The weather where the widget is set to: the temperature and what the sky is doing, a column of
 * readouts where there is the width for them, and the days ahead where there is the height.
 */
@Composable
fun WeatherWidget(widget: WidgetConfig, accent: Color, width: Dp, height: Dp, live: Boolean) {
    val skin = LocalSkin.current
    val place = widget.place
    val repo = LocalWeather.current.takeIf { live }
    val report = if (repo == null || place == null) null else {
        val reports by repo.reports.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        // Looked at again each time the launcher comes back, and fetched only if what's here has gone stale.
        LaunchedEffect(place, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { repo.refresh(place) } }
        reports[place.key()]
    }
    val sample = repo == null || place == null
    val weather = report ?: if (sample) SampleWeather else {
        Text("Awaiting ${place?.name}".cased(), color = LocalContentColor.current.copy(alpha = 0.7f), maxLines = 2, textAlign = TextAlign.Center)
        return
    }

    val fahrenheit = widget.option("fahrenheit") ?: weatherDefault("fahrenheit")
    val mph = widget.option("mph") ?: weatherDefault("mph")
    fun degrees(celsius: Double) = "${(if (fahrenheit) celsius * 9 / 5 + 32 else celsius).roundToInt()}°"
    val wind = if (mph) "${(weather.wind * 0.621371).roundToInt()} MPH" else "${weather.wind.roundToInt()} KM/H"
    val faint = LocalContentColor.current.copy(alpha = 0.7f)
    val gap = if (skin.lcars) 3.dp else 6.dp

    val days = weather.days.take((width / 66.dp).toInt().coerceIn(2, 5))
    val showDays = widget.option("forecast") != false && height >= 132.dp && days.size >= 2
    val top = if (showDays) height * 0.58f - gap else height
    val readouts = listOf("Feels" to degrees(weather.feelsLike), "Wind" to wind, "Humid" to "${weather.humidity}%").take(if (top >= 62.dp) 3 else 2)

    Column(Modifier.fillMaxSize().alpha(if (sample && live) 0.5f else 1f), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.fillMaxWidth().height(top), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val big = min(top * 0.5f, width * 0.2f).coerceIn(24.dp, 76.dp)
                val small = (top * 0.15f).coerceIn(11.dp, 17.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(big * 0.18f)) {
                    Text(degrees(weather.temperature), color = accent, fontSize = big.sp(), lineHeight = big.sp(), fontWeight = FontWeight.Bold, maxLines = 1)
                    Icon(conditionIcon(weather.code, weather.day), null, Modifier.size(big * 0.62f), tint = LocalContentColor.current)
                }
                Text(conditionOf(weather.code).cased(), fontSize = small.sp(), lineHeight = (small * 1.15f).sp(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (top >= 84.dp) Text(
                    (if (live && place == null) "Hold to set a place" else place?.name ?: "Starbase").cased(),
                    color = faint, fontSize = (small * 0.86f).sp(), lineHeight = small.sp(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (width >= 210.dp) Column(Modifier.width((width * 0.4f).coerceIn(92.dp, 150.dp)).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                readouts.forEachIndexed { index, (name, value) -> Readout(name, value, index, accent, Modifier.weight(1f)) }
            }
        }
        if (showDays) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            days.forEachIndexed { index, day -> Day(day, index, accent, ::degrees, Modifier.weight(1f)) }
        }
    }
}

/** One figure with its name, as an LCARS block capped on the left, or a tinted plate on other skins. */
@Composable
private fun Readout(name: String, value: String, index: Int, accent: Color, modifier: Modifier) {
    val skin = LocalSkin.current
    val ink = if (skin.lcars) Color.Black else LocalContentColor.current
    Box(
        modifier.fillMaxWidth().background(
            if (skin.lcars) LcarsPalette[(index * 2 + 1) % LcarsPalette.size] else accent.copy(alpha = 0.2f),
            if (skin.lcars) RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50) else RoundedCornerShape(10.dp),
        ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = 12.dp, end = 7.dp)) {
            val size = (maxHeight * 0.62f).coerceIn(9.dp, 15.dp).sp()
            Text(name.cased(), Modifier.align(Alignment.CenterStart), color = ink.copy(alpha = 0.65f), fontSize = size, lineHeight = size, maxLines = 1)
            Text(value, Modifier.align(Alignment.CenterEnd), color = ink, fontSize = size, lineHeight = size, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** One day ahead: its name on a bar of its own, then the high and the low. */
@Composable
private fun Day(day: DayForecast, index: Int, accent: Color, degrees: (Double) -> String, modifier: Modifier) {
    val skin = LocalSkin.current
    val name = if (index == 0) "Today" else runCatching { LocalDate.parse(day.date).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }.getOrDefault("")
    val size = 12.dp.sp()
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier.fillMaxWidth().height(16.dp).background(if (skin.lcars) LcarsPalette[(index + 2) % LcarsPalette.size] else accent.copy(alpha = 0.2f), RoundedCornerShape(if (skin.lcars) 0.dp else 8.dp)),
            contentAlignment = if (skin.lcars) Alignment.CenterEnd else Alignment.Center,
        ) {
            Text(
                name.cased(), Modifier.padding(horizontal = 5.dp), color = if (skin.lcars) Color.Black else LocalContentColor.current,
                fontSize = 11.dp.sp(), lineHeight = 12.dp.sp(), fontWeight = FontWeight.Bold, maxLines = 1,
            )
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(conditionIcon(day.code, true), conditionOf(day.code), Modifier.size(14.dp), tint = LocalContentColor.current.copy(alpha = 0.8f))
            Text(degrees(day.high), fontSize = size, lineHeight = size, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(degrees(day.low), fontSize = size, lineHeight = size, color = LocalContentColor.current.copy(alpha = 0.6f), maxLines = 1)
        }
    }
}

@Composable
private fun Dp.sp() = with(LocalDensity.current) { toSp() }
