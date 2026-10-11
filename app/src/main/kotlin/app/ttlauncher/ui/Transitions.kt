package app.ttlauncher.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.ttlauncher.data.PageTransition
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** How far [page] is from the middle of the screen, in pages: above 0 once it has gone off to the left. */
fun PagerState.offsetOf(page: Int): Float = (currentPage - page) + currentPageOffsetFraction

/** Whether pages change where they stand instead of sliding past, so whatever is drawn over them should fade too. */
val PageTransition.inPlace: Boolean get() = this == PageTransition.FADE || this == PageTransition.TRANSPORTER

/**
 * A page is only held in place while it is at least this far from being a whole page away. The pager
 * can come to rest a fraction of a pixel short, and a neighbouring page held over the one on show at
 * that moment, though faded to nothing, would take the touches meant for it.
 */
private const val PINNED_WITHIN = 0.99f

/** One glint of a transporter beam, placed as fractions of the page. */
private class Sparkle(val x: Float, val y: Float, val drift: Float, val lengthDp: Float, val widthDp: Float, val phase: Float, val color: Color)

private val BeamColors = listOf(Color.White, Color(0xFFBFE6FF), Color(0xFF8FD0FF), Color(0xFFFFF1C2))

/**
 * How one page of [pager] comes and goes. Everything follows the pager's own position and is read
 * while drawing, so a swipe costs no recomposition and nothing moves once the pages are at rest.
 */
@Composable
fun Modifier.pageTransition(pager: PagerState, page: Int, transition: PageTransition): Modifier = when (transition) {
    PageTransition.SLIDE -> this
    PageTransition.FADE -> graphicsLayer {
        val offset = pager.offsetOf(page)
        if (abs(offset) >= PINNED_WITHIN) return@graphicsLayer
        // Held where it is, against the pager's own slide.
        translationX = offset * size.width
        alpha = 1f - abs(offset)
    }
    PageTransition.ZOOM -> graphicsLayer {
        val away = abs(pager.offsetOf(page)).coerceAtMost(1f)
        scaleX = 1f - 0.25f * away
        scaleY = scaleX
        alpha = 1f - 0.7f * away
    }
    PageTransition.CUBE -> graphicsLayer {
        val offset = pager.offsetOf(page).coerceIn(-1f, 1f)
        // Two faces of a box turning about the edge they share.
        transformOrigin = TransformOrigin(if (offset > 0f) 1f else 0f, 0.5f)
        rotationY = -90f * offset
        cameraDistance = 20f * density
    }
    PageTransition.TRANSPORTER -> graphicsLayer {
        val offset = pager.offsetOf(page)
        if (abs(offset) < PINNED_WITHIN) translationX = offset * size.width
    }.transporterBeam(page) { abs(pager.offsetOf(page)) }
}

/**
 * Beams whatever this is set on out and back in. [away] is read while drawing: 0 leaves it as it is,
 * 1 is gone altogether, and in between it thins out behind a column of glints.
 */
@Composable
fun Modifier.transporterBeam(seed: Int, away: () -> Float): Modifier {
    val sparkles = remember(seed) {
        val random = Random(seed * 7919 + 17)
        List(260) {
            Sparkle(
                random.nextFloat(), random.nextFloat(), drift = random.nextFloat() * 1.2f - 0.6f,
                lengthDp = 4f + random.nextFloat() * 22f, widthDp = 1.2f + random.nextFloat() * 2f,
                phase = random.nextFloat() * 2f * PI.toFloat(), color = BeamColors[random.nextInt(BeamColors.size)],
            )
        }
    }
    return drawWithContent {
        drawContent()
        val gone = away()
        if (gone < 0.002f || gone >= 1f) return@drawWithContent
        // Brightest half way, when what was here has gone and what's next has yet to arrive.
        val strength = sin(gone * PI.toFloat())
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, BeamColors[2].copy(alpha = 0.2f * strength), Color.Transparent)),
            blendMode = BlendMode.Plus,
        )
        sparkles.forEach { sparkle ->
            // Each glint flickers and drifts up or down the beam as it goes on.
            val glow = strength * (0.5f + 0.5f * sin(sparkle.phase + gone * 46f))
            if (glow < 0.06f) return@forEach
            val x = sparkle.x * size.width
            val y = (sparkle.y + sparkle.drift * gone).mod(1f) * size.height
            val half = sparkle.lengthDp.dp.toPx() * (0.3f + strength) / 2
            drawLine(
                sparkle.color.copy(alpha = glow), Offset(x, y - half), Offset(x, y + half),
                strokeWidth = sparkle.widthDp.dp.toPx(), cap = StrokeCap.Round, blendMode = BlendMode.Plus,
            )
        }
    }.graphicsLayer {
        // Gone a little past half way, leaving only the beam.
        alpha = (1f - away() / 0.6f).coerceIn(0f, 1f)
    }
}
