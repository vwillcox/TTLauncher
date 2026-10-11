package app.ttlauncher.ui

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.ttlauncher.data.SizeLimits
import app.ttlauncher.data.WidgetConfig
import app.ttlauncher.data.WidgetType
import app.ttlauncher.data.limits

/** Hosts ordinary Android widgets from other apps alongside the built-in ones. */
class WidgetHostController(private val activity: ComponentActivity) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    val host: AppWidgetHost = object : AppWidgetHost(activity.applicationContext, HOST_ID) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView = CardHostView(context)
    }

    private var pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var onAdded: ((Int) -> Unit)? = null

    // The system asks the user once whether this launcher may place widgets.
    private val bind = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) configure() else cancel()
    }

    fun providers(): List<AppWidgetProviderInfo> = runCatching { manager.installedProviders }.getOrDefault(emptyList())

    fun add(provider: AppWidgetProviderInfo, onAdded: (Int) -> Unit) {
        cancel()
        this.onAdded = onAdded
        pendingId = host.allocateAppWidgetId()
        if (manager.bindAppWidgetIdIfAllowed(pendingId, provider.profile, provider.provider, null)) configure()
        else bind.launch(
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile),
        )
    }

    private fun configure() {
        val needsSetup = manager.getAppWidgetInfo(pendingId)?.configure != null
        // Has to go through the host: a widget's setup screen is usually not open to other apps.
        if (needsSetup && runCatching { host.startAppWidgetConfigureActivityForResult(activity, pendingId, 0, REQUEST_CONFIGURE, null) }.isSuccess) return
        finish()
    }

    /** Returns true when the result was for a widget's setup screen. */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != REQUEST_CONFIGURE) return false
        if (resultCode == Activity.RESULT_OK) finish() else cancel()
        return true
    }

    private fun finish() {
        val id = pendingId
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) onAdded?.invoke(id)
        onAdded = null
    }

    private fun cancel() {
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) remove(pendingId)
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        onAdded = null
    }

    fun remove(appWidgetId: Int) {
        runCatching { host.deleteAppWidgetId(appWidgetId) }
    }

    private val names = HashMap<Int, String?>()

    /** The name of the app a hosted widget comes from, or null if it can't be had. */
    fun appName(widget: WidgetConfig): String? = names.getOrPut(widget.appWidgetId) {
        runCatching {
            val packages = activity.packageManager
            val owner = manager.getAppWidgetInfo(widget.appWidgetId).provider.packageName
            packages.getApplicationLabel(packages.getApplicationInfo(owner, 0)).toString()
        }.getOrNull()
    }

    private val limits = HashMap<Int, SizeLimits>()

    /** A hosted widget's smallest size comes from its own app rather than from this launcher. */
    fun limits(widget: WidgetConfig, density: Float): SizeLimits {
        if (widget.type != WidgetType.SYSTEM) return widget.limits()
        return limits.getOrPut(widget.appWidgetId) {
            val info = runCatching { manager.getAppWidgetInfo(widget.appWidgetId) }.getOrNull() ?: return widget.limits()
            val minWidth = (info.minWidth / density).coerceIn(60f, 300f)
            val minHeight = (info.minHeight / density).coerceIn(52f, 400f)
            SizeLimits(minWidth, minHeight, maxOf(minWidth, 330f), maxOf(minHeight, 150f), 640f)
        }
    }

    // Widgets only receive updates while the home screen is visible.
    fun start() {
        runCatching { host.startListening() }
    }

    fun stop() {
        runCatching { host.stopListening() }
    }

    private companion object {
        const val HOST_ID = 0x7474
        const val REQUEST_CONFIGURE = 0x7475
    }
}

/**
 * A widget's view that works out where the card inside it is. Many widgets leave a margin of their
 * own round the card they draw, each a different one, which shows as an uneven gap between the card
 * and the bars of a frame that is meant to fit round it.
 */
private class CardHostView(context: Context) : AppWidgetHostView(context) {
    /** Told how far in from each edge the card sits, whenever that changes. */
    var onCardInsets: ((Rect) -> Unit)? = null
    private var insets = Rect()

    /** Set where the widget is framed by bars it should lie against. */
    var flush = false
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    /** The picture this has set to fill its place, so it can be put back as it was. */
    private var filled: ImageView? = null

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        fillPicture()
        val found = cardInsets()
        if (found != insets) {
            insets = found
            onCardInsets?.invoke(found)
        }
    }

    /**
     * Follows the views that do nothing but hold one other down to the first that draws a background,
     * which is the card. Only a small margin is believed: more than that and it is the widget's design.
     */
    private fun cardInsets(): Rect {
        val limit = (MAX_CARD_MARGIN_DP * resources.displayMetrics.density).toInt()
        val at = Rect(0, 0, width, height)
        var view: View = this
        while (view === this || (view.background == null && view.id != android.R.id.background)) {
            val child = (view as? ViewGroup)?.takeIf { it.childCount == 1 }?.getChildAt(0) ?: return Rect()
            at.set(at.left + child.left, at.top + child.top, at.left + child.right, at.top + child.bottom)
            view = child
        }
        val found = Rect(at.left, at.top, width - at.right, height - at.bottom)
        return if (listOf(found.left, found.top, found.right, found.bottom).all { it in 0..limit }) found else Rect()
    }

    /**
     * A widget that is nothing but a picture shows it whole, which leaves bands of empty frame down two
     * sides wherever its place isn't the picture's own shape. Where filling the place instead would lose
     * only a little off the other two edges, the picture is made to fill it.
     */
    private fun fillPicture() {
        var view: View = this
        while (view is ViewGroup) view = view.takeIf { it.childCount == 1 }?.getChildAt(0) ?: return
        val picture = view as? ImageView ?: return
        val drawable = picture.drawable ?: return
        val ours = picture === filled && picture.scaleType == ImageView.ScaleType.CENTER_CROP
        if (!ours && picture.scaleType != ImageView.ScaleType.FIT_CENTER) return
        val across = picture.width.toFloat() / drawable.intrinsicWidth
        val down = picture.height.toFloat() / drawable.intrinsicHeight
        // Only a picture that has the whole widget to itself, and has a size of its own to go by.
        val fills = flush && across > 0f && down > 0f && picture.width >= width * 0.9f && picture.height >= height * 0.9f &&
            maxOf(across, down) / minOf(across, down) <= MAX_PICTURE_CROP
        if (fills == ours) return
        picture.scaleType = if (fills) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        filled = picture.takeIf { fills }
    }

    private companion object {
        const val MAX_CARD_MARGIN_DP = 16

        /** Filling may enlarge a picture by this much at most, which loses a fifth of it off two edges. */
        const val MAX_PICTURE_CROP = 1.25f
    }
}

/** With [flush], a card that sits in from the edges of its widget is brought out to them, to lie against whatever frames it. */
@Composable
fun HostedWidget(widget: WidgetConfig, controller: WidgetHostController, modifier: Modifier = Modifier, flush: Boolean = false) {
    val info = remember(widget.appWidgetId) { runCatching { controller.manager.getAppWidgetInfo(widget.appWidgetId) }.getOrNull() }
    if (info == null) {
        Text("This widget's app is no longer installed.", Modifier.padding(20.dp))
        return
    }
    val density = LocalDensity.current
    var margin by remember(widget.appWidgetId) { mutableStateOf(Rect()) }
    AndroidView(
        factory = { context ->
            controller.host.createView(context, widget.appWidgetId, info).apply {
                setPadding(0, 0, 0, 0)
                (this as? CardHostView)?.onCardInsets = { margin = it }
            }
        },
        update = { (it as? CardHostView)?.flush = flush },
        modifier = modifier.fillMaxSize().layout { measurable, constraints ->
            // The view is made larger than its place by the card's margins, which then fall outside and are cut off.
            val trim = if (flush && constraints.hasBoundedWidth && constraints.hasBoundedHeight) margin else Rect()
            val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth + trim.left + trim.right, constraints.maxHeight + trim.top + trim.bottom))
            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-trim.left, -trim.top) }
        }.onSizeChanged { size ->
            // Tells the widget how much room it has, so it can pick its matching layout.
            val dp = with(density) { SizeF(size.width.toDp().value, size.height.toDp().value) }
            runCatching { controller.manager.updateAppWidgetOptions(widget.appWidgetId, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, dp.width.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, dp.width.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, dp.height.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, dp.height.toInt())
                putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(dp))
            }) }
        },
    )
}
