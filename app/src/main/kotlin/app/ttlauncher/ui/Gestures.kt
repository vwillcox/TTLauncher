package app.ttlauncher.ui

import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private val TRIGGER_DISTANCE = 56.dp

/**
 * Fires when a drag carries on past the end of the scrolling content inside: up past the bottom,
 * or down past the top. A null [onTrigger] turns it off.
 */
@Composable
fun Modifier.overscrollTrigger(upward: Boolean, onTrigger: (() -> Unit)?): Modifier {
    if (onTrigger == null) return this
    val threshold = with(LocalDensity.current) { TRIGGER_DISTANCE.toPx() }
    val action by rememberUpdatedState(onTrigger)
    val connection = remember(upward, threshold) {
        object : NestedScrollConnection {
            var pulled = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val past = if (upward) -available.y else available.y
                if (source == NestedScrollSource.UserInput && past > 0f) {
                    pulled += past
                    if (pulled >= threshold) {
                        pulled = 0f
                        action()
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }
    return nestedScroll(connection)
}

/** The same gestures for content that doesn't scroll. Either can be null. */
@Composable
fun Modifier.dragTrigger(onUp: (() -> Unit)?, onDown: (() -> Unit)?): Modifier {
    if (onUp == null && onDown == null) return this
    val threshold = with(LocalDensity.current) { TRIGGER_DISTANCE.toPx() }
    val up by rememberUpdatedState(onUp)
    val down by rememberUpdatedState(onDown)
    return pointerInput(threshold) {
        var dragged = 0f
        detectVerticalDragGestures(onDragStart = { dragged = 0f }) { _, amount ->
            dragged += amount
            if (dragged <= -threshold) {
                dragged = 0f
                up?.invoke()
            } else if (dragged >= threshold) {
                dragged = 0f
                down?.invoke()
            }
        }
    }
}

/** Turns [pager]'s pages from somewhere outside it, such as the dock drawn over it. */
@Composable
fun Modifier.pagerDrag(pager: PagerState, enabled: Boolean): Modifier {
    if (!enabled) return this
    val scope = rememberCoroutineScope()
    return pointerInput(pager) {
        val tracker = VelocityTracker()
        val fling = 400.dp.toPx()
        fun settle() {
            val at = pager.currentPage + pager.currentPageOffsetFraction
            val speed = tracker.calculateVelocity().x
            // A flick goes on to the next page in its direction; a slow drag settles on the nearer one.
            val target = if (speed < -fling) ceil(at) else if (speed > fling) floor(at) else at.roundToInt().toFloat()
            scope.launch { pager.animateScrollToPage(target.toInt().coerceIn(0, pager.pageCount - 1)) }
        }
        detectHorizontalDragGestures(onDragStart = { tracker.resetTracking() }, onDragEnd = ::settle, onDragCancel = ::settle) { change, amount ->
            tracker.addPosition(change.uptimeMillis, change.position)
            change.consume()
            pager.dispatchRawDelta(-amount)
        }
    }
}

/** How far up or down a swipe may wander for each step it goes sideways and still turn the page. */
private const val SLANT = 1.4f

/** Which page a drag that carried [pager] by hand should come to rest on, having set out from [from] and been let go at [speed]. */
fun restingPage(at: Float, from: Int, speed: Float, flick: Float): Int = when {
    // A flick goes on to the next page in its direction, and so does a slower drag that got a third of the way.
    speed <= -flick || (speed < flick && at - from > 0.33f) -> ceil(at).toInt()
    speed >= flick || from - at > 0.33f -> floor(at).toInt()
    else -> from
}

/**
 * Turns [pager]'s pages for swipes that are only mostly sideways, over a page that scrolls up and
 * down. Left to itself such a page takes any swipe that sets off a little up or down, as the arc of
 * a thumb does, and the page never turns. So the swipe is judged here first, on its way down to the
 * page, by its slant and not by which way it happened to move first, and one that turns sideways
 * within its first stretch is still taken. Set on the pager itself, which stays put while its pages
 * move; [active] says whether the page on show wants this.
 */
@Composable
fun Modifier.slantedPagerDrag(pager: PagerState, active: () -> Boolean): Modifier {
    val scope = rememberCoroutineScope()
    val wanted by rememberUpdatedState(active)
    return pointerInput(pager) {
        val tracker = VelocityTracker()
        val flick = 250.dp.toPx()
        // A swipe that has come this far without turning sideways is a scroll, and is left as one.
        val decidedBy = 56.dp.toPx()
        var settling: Job? = null
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!wanted()) return@awaitEachGesture
            tracker.resetTracking()
            tracker.addPosition(down.uptimeMillis, down.position)
            var taken = false
            var from = 0
            var last = down.position
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!taken) {
                        if (!change.pressed || change.isConsumed) break
                        val moved = change.position - down.position
                        // Only towards a page that is there: a swipe at the last page's far edge is left to scroll what's under it.
                        val leads = if (moved.x < 0f) pager.canScrollForward else pager.canScrollBackward
                        if (leads && abs(moved.x) >= viewConfiguration.touchSlop && abs(moved.x) * SLANT >= abs(moved.y)) {
                            taken = true
                            settling?.cancel()
                            from = pager.currentPage
                            last = change.position
                        } else if (moved.getDistance() > decidedBy) break
                    }
                    if (taken) {
                        pager.dispatchRawDelta(last.x - change.position.x)
                        last = change.position
                        // Kept from the page, which would scroll with it or take the release as a tap.
                        event.changes.forEach { it.consume() }
                        if (!change.pressed) break
                    }
                }
            } finally {
                if (taken) {
                    val target = restingPage(pager.currentPage + pager.currentPageOffsetFraction, from, tracker.calculateVelocity().x, flick)
                    settling = scope.launch { pager.animateScrollToPage(target.coerceIn(0, pager.pageCount - 1)) }
                }
            }
        }
    }
}

/**
 * Opens the notification shade. Android has no public call for this, so it goes through the same
 * hidden StatusBarManager method other launchers use; if a future release removes it, nothing happens.
 */
@SuppressLint("WrongConstant", "PrivateApi")
fun expandNotificationShade(context: Context) {
    runCatching {
        val statusBar = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(statusBar)
    }
}

/**
 * A long press caught on its way down to content that keeps its touches to itself, such as another
 * app's widget, which would never pass one on.
 */
@Composable
fun Modifier.holdOver(onHold: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val hold by rememberUpdatedState(onHold)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Held if the time runs out before the finger lifts or wanders off.
            val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                }
            } == null
            if (!held) return@awaitEachGesture
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            hold()
            // The rest of the touch is kept from what's underneath, which would take the release as a tap.
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}
