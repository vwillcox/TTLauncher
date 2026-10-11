package app.ttlauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import app.ttlauncher.R
import app.ttlauncher.apps.IconRepo
import app.ttlauncher.apps.IconTheme
import app.ttlauncher.data.LauncherState
import app.ttlauncher.data.WidgetStyle

/** A complete look: colours, corner radius, typeface and how widgets are framed. */
@Immutable
data class Skin(
    val id: String,
    val name: String,
    val dark: Boolean,
    /** Tints the wallpaper behind everything. */
    val backdrop: Color,
    val surface: Color,
    val onSurface: Color,
    val accent: Color,
    val radius: Dp,
    val widgetStyle: WidgetStyle,
    val font: FontFamily = FontFamily.Default,
    /** Hides the wallpaper completely. On an OLED panel black pixels are switched off. */
    val opaque: Boolean = false,
    /** Draws panels as LCARS elbows and blocks, and sets words in capitals. */
    val lcars: Boolean = false,
) {
    fun scrim(dim: Float): Color = if (opaque) backdrop else backdrop.copy(alpha = dim)

    /** For the pages that are mostly text, which need more than the home page's light tint. */
    fun pageScrim(dim: Float): Color = if (opaque) backdrop else backdrop.copy(alpha = maxOf(dim, 0.86f))
}

const val DYNAMIC_SKIN = "dynamic"

/** The flat pastels LCARS panels are built from. */
val LcarsPalette = listOf(
    Color(0xFFFF9900), Color(0xFFCC99CC), Color(0xFF9999FF), Color(0xFFFFCC99), Color(0xFFCC6666), Color(0xFF99CCFF), Color(0xFFFFCC66),
)

/** The near-white an LCARS control turns while it is selected or wants attention. */
val LcarsHighlight = Color(0xFFFFF4E6)

/** Antonio, a condensed face close to the one LCARS displays use. One variable font file covers both weights. */
private val Antonio = FontFamily(
    Font(R.font.antonio, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.antonio, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

val Skins = listOf(
    Skin("aurora", "Aurora", true, Color(0xFF0E0B1F), Color(0xFF1D1836), Color(0xFFF1EEFF), Color(0xFF9B8CFF), 28.dp, WidgetStyle.GLASS),
    Skin("paper", "Paper", false, Color(0xFFF4F1EA), Color(0xFFFFFFFF), Color(0xFF1C1B1A), Color(0xFF2F6BFF), 20.dp, WidgetStyle.SOLID, FontFamily.Serif),
    Skin("midnight", "Midnight", true, Color.Black, Color(0xFF0D0D0D), Color(0xFFEDEDED), Color(0xFF00E5A8), 18.dp, WidgetStyle.OUTLINE, opaque = true),
    Skin("sunset", "Sunset", true, Color(0xFF2A1220), Color(0xFF3B1B2B), Color(0xFFFFEFE6), Color(0xFFFF8A5B), 32.dp, WidgetStyle.GLASS),
    Skin("terminal", "Terminal", true, Color(0xFF020A04), Color(0xFF07140A), Color(0xFFB8FFC9), Color(0xFF39FF6A), 4.dp, WidgetStyle.OUTLINE, FontFamily.Monospace, opaque = true),
    Skin("lcars", "LCARS", true, Color.Black, Color(0xFF15131C), Color(0xFFFFCC99), Color(0xFFFF9900), 22.dp, WidgetStyle.SOLID, Antonio, opaque = true, lcars = true),
    // Placeholder colours for the picker; the real ones come from the wallpaper.
    Skin(DYNAMIC_SKIN, "Material You", true, Color(0xFF121416), Color(0xFF1E2226), Color(0xFFE3E6EA), Color(0xFF8AB4F8), 24.dp, WidgetStyle.SOLID),
)

val AccentChoices = listOf(0xFF9B8CFF, 0xFF2F6BFF, 0xFF00E5A8, 0xFFFF8A5B, 0xFFFF5C8A, 0xFFFFC857, 0xFF4DD0E1, 0xFFE0E0E0)

@Composable
fun resolveSkin(state: LauncherState): Skin {
    val base = Skins.firstOrNull { it.id == state.skinId } ?: Skins.first()
    val skin = if (base.id == DYNAMIC_SKIN) {
        val context = LocalContext.current
        val dark = isSystemInDarkTheme()
        val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        base.copy(dark = dark, backdrop = scheme.background, surface = scheme.surfaceContainer, onSurface = scheme.onSurface, accent = scheme.primary)
    } else base
    return skin.copy(
        accent = state.accent?.let { Color(it) } ?: skin.accent,
        widgetStyle = state.widgetStyle ?: skin.widgetStyle,
    )
}

fun Skin.colorScheme(): ColorScheme {
    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF111111) else Color.White
    return (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = lerp(surface, accent, 0.3f),
        onPrimaryContainer = onSurface,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = lerp(surface, accent, 0.25f),
        onSecondaryContainer = onSurface,
        tertiary = accent,
        background = backdrop,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = lerp(surface, onSurface, 0.1f),
        onSurfaceVariant = onSurface.copy(alpha = 0.72f),
        surfaceContainerLowest = backdrop,
        surfaceContainerLow = surface,
        surfaceContainer = lerp(surface, onSurface, 0.04f),
        surfaceContainerHigh = lerp(surface, onSurface, 0.08f),
        surfaceContainerHighest = lerp(surface, onSurface, 0.12f),
        outline = onSurface.copy(alpha = 0.4f),
        outlineVariant = onSurface.copy(alpha = 0.16f),
    )
}

fun Skin.typography(): Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = font),
        displayMedium = displayMedium.copy(fontFamily = font),
        displaySmall = displaySmall.copy(fontFamily = font),
        headlineLarge = headlineLarge.copy(fontFamily = font),
        headlineMedium = headlineMedium.copy(fontFamily = font),
        headlineSmall = headlineSmall.copy(fontFamily = font),
        titleLarge = titleLarge.copy(fontFamily = font),
        titleMedium = titleMedium.copy(fontFamily = font),
        titleSmall = titleSmall.copy(fontFamily = font),
        bodyLarge = bodyLarge.copy(fontFamily = font),
        bodyMedium = bodyMedium.copy(fontFamily = font),
        bodySmall = bodySmall.copy(fontFamily = font),
        labelLarge = labelLarge.copy(fontFamily = font),
        labelMedium = labelMedium.copy(fontFamily = font),
        labelSmall = labelSmall.copy(fontFamily = font),
    )
}

val LocalSkin = staticCompositionLocalOf { Skins.first() }

/** Words as the skin sets them: capitals on LCARS, as written everywhere else. */
@Composable
@ReadOnlyComposable
fun String.cased(): String = if (LocalSkin.current.lcars) uppercase() else this
val LocalIcons = staticCompositionLocalOf<IconRepo> { error("No icon repository") }
val LocalIconTheme = staticCompositionLocalOf<IconTheme> { error("No icon theme") }

/**
 * A value that follows a system broadcast while the launcher is on screen and stops listening when
 * it isn't. The clock runs off the system's own minute tick this way, with no timer of its own.
 */
@Composable
fun <T> rememberBroadcast(initial: T, vararg actions: String, read: (Intent?) -> T?): State<T> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(initial) }
    LifecycleStartEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                read(intent)?.let { state.value = it }
            }
        }
        val filter = IntentFilter().apply { actions.forEach(::addAction) }
        val sticky = ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        read(sticky)?.let { state.value = it }
        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
    return state
}
