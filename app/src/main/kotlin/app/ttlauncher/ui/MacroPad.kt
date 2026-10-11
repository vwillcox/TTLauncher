@file:OptIn(ExperimentalFoundationApi::class)

package app.ttlauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import app.ttlauncher.data.MacroButton
import app.ttlauncher.data.WidgetConfig
import kotlin.math.abs
import kotlin.math.ceil

/** Carries out a macro button. Set by the launcher itself, which knows the apps and its own pages. */
val LocalMacroRunner = staticCompositionLocalOf<(MacroButton) -> Unit> { {} }

/** Shown until the widget has buttons of its own, so the picker and a new widget aren't blank. */
private val SampleButtons = listOf("Alpha", "Beta", "Gamma", "Delta").map { MacroButton(it, it) }

/**
 * A pad of buttons, each opening an app or a link or doing something in the launcher. They share
 * the widget's room equally, in whichever number of columns leaves them closest to button-shaped.
 */
@Composable
fun MacroPadWidget(widget: WidgetConfig, accent: Color, width: Dp, height: Dp, live: Boolean, onEdit: () -> Unit) {
    val skin = LocalSkin.current
    val run = LocalMacroRunner.current
    val samples = widget.buttons.isEmpty()
    val buttons = if (samples) SampleButtons else widget.buttons
    val gap = if (skin.lcars) 3.dp else 6.dp
    val columns = (1..buttons.size).minBy { c -> abs((width / c) / (height / ceil(buttons.size / c.toFloat())) - 2.4f) }
    val rows = buttons.chunked(columns)
    val cellHeight = (height - gap * (rows.size - 1)) / rows.size
    val textSize = with(LocalDensity.current) { (cellHeight * 0.3f).coerceIn(10.dp, 15.dp).toSp() }
    Column(Modifier.fillMaxSize().alpha(if (samples) 0.5f else 1f), verticalArrangement = Arrangement.spacedBy(gap)) {
        rows.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEachIndexed { columnIndex, button ->
                    val index = rowIndex * columns + columnIndex
                    val press = if (!live) Modifier else Modifier.combinedClickable(
                        onClickLabel = button.label, onLongClick = onEdit,
                        // With nothing set up yet, a press goes to where buttons are added.
                        onClick = { if (samples) onEdit() else run(button) },
                    )
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .clip(RoundedCornerShape(if (skin.lcars) 50 else 22))
                            .background(if (skin.lcars) LcarsPalette[index % LcarsPalette.size] else accent.copy(alpha = 0.24f))
                            .then(press),
                    ) {
                        // LCARS buttons carry their name in the bottom corner, in black.
                        Text(
                            if (skin.lcars) button.label.uppercase() else button.label,
                            Modifier.align(if (skin.lcars) Alignment.BottomEnd else Alignment.Center)
                                .padding(start = 8.dp, end = if (skin.lcars) cellHeight * 0.32f else 8.dp, bottom = if (skin.lcars) 3.dp else 0.dp),
                            color = if (skin.lcars) Color.Black else LocalContentColor.current,
                            fontSize = textSize, lineHeight = textSize, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // The last row keeps its buttons the same width as the rest.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
