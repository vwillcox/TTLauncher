package app.ttlauncher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.ttlauncher.apps.AppEntry
import app.ttlauncher.data.CellSize
import app.ttlauncher.data.DockFolder
import app.ttlauncher.data.GRID_GAP_DP
import app.ttlauncher.data.GRID_TIGHT_GAP_DP
import app.ttlauncher.data.GridSpec
import app.ttlauncher.data.ScreenLayout
import app.ttlauncher.data.WidgetPage
import app.ttlauncher.data.WidgetStyle
import app.ttlauncher.data.WidgetType
import app.ttlauncher.data.packGrid
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** What sits in the dock: an app, or a folder of them. */
sealed interface DockEntry {
    class App(val app: AppEntry) : DockEntry
    class Folder(val folder: DockFolder, val apps: List<AppEntry>) : DockEntry
}

/** Whether the home page is being rearranged, and which widget has the handles. */
@Stable
class HomeEditState {
    var editing by mutableStateOf(false)
        private set
    var selected by mutableStateOf<String?>(null)
        private set

    fun start(widgetId: String?) {
        editing = true
        selected = widgetId
    }

    fun select(widgetId: String?) {
        selected = widgetId
    }

    fun stop() {
        editing = false
        selected = null
    }
}

/** What the edit bar and the gestures on a home page can ask for. */
class HomeEditActions(
    val onReorder: (from: Int, to: Int) -> Unit,
    val onResize: (id: String, size: CellSize) -> Unit,
    val onAdd: () -> Unit,
    /** Null once this screen has as many pages as it may. */
    val onAddPage: (() -> Unit)?,
    val onGrid: () -> Unit,
    val onCustomise: (id: String) -> Unit,
    val onRemove: (id: String) -> Unit,
)

@Composable
fun HomePage(
    layout: ScreenLayout,
    page: WidgetPage,
    dim: Float,
    host: WidgetHostController,
    edit: HomeEditState,
    actions: HomeEditActions,
    /** Room to leave at the bottom for the dock and the page dots, which are drawn over every page. */
    bottomReserve: Dp,
    /** The dock is showing right under this page's widgets. */
    dockBelow: Boolean,
    onHomeMenu: () -> Unit,
    onSearch: () -> Unit,
    /** Set when the app drawer opens with a swipe up, rather than being the page to the right. */
    onOpenDrawer: (() -> Unit)?,
    /** Set when pulling down opens the notification shade. */
    onPullDown: (() -> Unit)?,
) {
    val skin = LocalSkin.current
    val editing = edit.editing
    Column(
        Modifier.fillMaxSize().background(skin.scrim(dim))
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp),
    ) {
        WidgetGrid(
            layout, page, host, edit, actions, dockBelow, onSearch, onHomeMenu,
            Modifier.weight(1f).fillMaxWidth()
                .overscrollTrigger(upward = true, onOpenDrawer.takeIf { !editing })
                .overscrollTrigger(upward = false, onPullDown.takeIf { !editing }),
        )
        if (editing) EditBar(edit, actions)
        Spacer(Modifier.height(bottomReserve))
    }
}

/**
 * The dock and the page dots. Drawn once, over the pages, so neither moves as the pages are swiped.
 * [dock] is null when the dock is switched off or the home page is being rearranged.
 */
@Composable
fun HomeBar(
    dock: List<DockEntry>?,
    iconSizeDp: Int,
    pager: PagerState,
    /** Which of the pager's pages are home pages. The dock belongs to those and leaves with the last of them. */
    homePages: IntRange,
    /** Leaves by fading where it stands, for the page transitions that do the same, instead of sliding off. */
    fadeAway: Boolean,
    pagingEnabled: Boolean,
    modifier: Modifier,
    onDockHeight: (Dp) -> Unit,
    onDotsHeight: (Dp) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onDockFolderMenu: (DockFolder) -> Unit,
    onOpenDrawer: (() -> Unit)?,
    onPullDown: (() -> Unit)?,
) {
    val density = LocalDensity.current
    val entries = dock.orEmpty()
    val onHome = pager.currentPage in homePages
    // The dock folder that is fanned out, by id.
    var openFolder by remember { mutableStateOf<String?>(null) }
    if (dock == null || !onHome) openFolder = null
    val open = entries.firstNotNullOfOrNull { entry -> (entry as? DockEntry.Folder)?.takeIf { it.folder.id == openFolder } }
    // Kept while the fly-out animates shut, after the folder is no longer open.
    var lastOpen by remember { mutableStateOf<DockEntry.Folder?>(null) }
    if (open != null) lastOpen = open
    Box(modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp)) {
        // A touch anywhere else puts the folder away.
        if (open != null) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { openFolder = null } })
        // The widgets above scroll, so a swipe up there opens the drawer only once they reach the end.
        // Down here it always does. Sideways swipes still turn the page, though the bar itself stays put.
        // The gestures are set on the dock and on the dots, each in turn, and not on the column that holds them:
        // the dock keeps its room here after it has left with the home pages, and a touch on that empty room
        // has to reach the page underneath, or the news couldn't be scrolled from the bottom of the screen.
        val gestures = Modifier.pagerDrag(pager, pagingEnabled)
            .dragTrigger(onUp = onOpenDrawer.takeIf { onHome && pagingEnabled }, onDown = onPullDown.takeIf { onHome && pagingEnabled })
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (dock != null) Column(
                Modifier.fillMaxWidth().graphicsLayer {
                    // Goes off with the first or last home page when the news or the apps page comes in beside it.
                    val at = pager.currentPage + pager.currentPageOffsetFraction
                    val away = if (at < homePages.first) at - homePages.first else if (at > homePages.last) at - homePages.last else 0f
                    val gone = -away * (pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing)
                    if (!fadeAway) translationX = gone
                    else {
                        alpha = (1f - abs(away) / 0.6f).coerceIn(0f, 1f)
                        // Out of reach once it can't be seen.
                        if (alpha == 0f) translationX = gone
                    }
                }.then(gestures),
            ) {
                val index = entries.indexOfFirst { (it as? DockEntry.Folder)?.folder?.id == lastOpen?.folder?.id }
                // Springs up out of the folder's own place in the dock.
                val origin = TransformOrigin(if (index < 0) 0.5f else (index + 1f) / (entries.size + 1f), 1f)
                AnimatedVisibility(
                    visible = open != null,
                    modifier = Modifier.padding(bottom = 10.dp),
                    enter = scaleIn(spring(dampingRatio = 0.72f, stiffness = 420f), initialScale = 0.15f, transformOrigin = origin) + fadeIn(tween(120)),
                    exit = scaleOut(tween(150), targetScale = 0.15f, transformOrigin = origin) + fadeOut(tween(150)),
                ) {
                    lastOpen?.let { entry ->
                        DockFlyout(entry, iconSizeDp, onLaunch = { openFolder = null; onLaunch(it) }, onAppMenu = { openFolder = null; onAppMenu(it) })
                    }
                }
                Box(Modifier.onSizeChanged { onDockHeight(with(density) { it.height.toDp() }) }) {
                    Dock(
                        entries, iconSizeDp, swipeUp = onOpenDrawer != null, openFolder, onLaunch, onAppMenu,
                        onFolder = { openFolder = if (openFolder == it.id) null else it.id },
                        onFolderMenu = { openFolder = null; onDockFolderMenu(it) },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().then(gestures).onSizeChanged { onDotsHeight(with(density) { it.height.toDp() }) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                PageDots(selected = pager.currentPage, count = pager.pageCount)
            }
        }
    }
}

@Composable
private fun WidgetGrid(
    layout: ScreenLayout,
    page: WidgetPage,
    host: WidgetHostController,
    edit: HomeEditState,
    actions: HomeEditActions,
    dockBelow: Boolean,
    onSearch: () -> Unit,
    onHomeMenu: () -> Unit,
    modifier: Modifier,
) {
    val skin = LocalSkin.current
    val density = LocalDensity.current
    val gap = GRID_GAP_DP.dp
    BoxWithConstraints(modifier) {
        // On LCARS the frames sit against each other. What the gap gives up goes to the rows, so every widget stays where it was.
        val tight = if (skin.lcars) GRID_TIGHT_GAP_DP else GRID_GAP_DP
        val spec = GridSpec(layout.columns, layout.rowHeightDp + GRID_GAP_DP - tight, maxWidth.value, tight)
        val between = spec.gapDp.dp
        val widgets = page.widgets
        // A stored size is only a request: it is held to what the widget's content can bear on this grid.
        val sizes = widgets.map { widget -> host.limits(widget, density.density).let { spec.clamp(widget.size ?: spec.defaultSize(it), it) } }
        val placed = remember(layout.columns, sizes) { packGrid(layout.columns, sizes) }
        val rows = placed.indices.maxOfOrNull { placed[it].row + sizes[it].rows } ?: 0
        // Where each widget sits, in pixels, for hit-testing a drag against.
        val slots = widgets.indices.map { i ->
            with(density) {
                Rect(
                    Offset((placed[i].column * (spec.cellDp + spec.gapDp)).dp.toPx(), (placed[i].row * (spec.rowDp + spec.gapDp)).dp.toPx() + gap.toPx()),
                    Size(spec.widthOf(sizes[i].cols).dp.toPx(), spec.heightOf(sizes[i].rows).dp.toPx()),
                )
            }
        }
        // On LCARS the side bar of the lowest widget on the left edge carries on down to the dock's, as one column.
        // That widget's frame sweeps across its top, whatever its turn, so its bar runs straight out of the bottom.
        val joined = if (skin.lcars && dockBelow && !edit.editing) widgets.indices.filter { placed[it].column == 0 && (widgets[it].style ?: skin.widgetStyle) != WidgetStyle.BARE }.maxByOrNull { slots[it].bottom } else null
        val viewport = maxHeight
        fun framed(index: Int) = (widgets[index].style ?: skin.widgetStyle) != WidgetStyle.BARE
        fun turnedDown(index: Int) = index % 2 == 1 && index != joined
        // Whether another panel sits right against the corner this one's bars sweep round, on the same left edge.
        fun meetsAnother(index: Int) = widgets.indices.any { other ->
            other != index && framed(other) && placed[other].column == placed[index].column &&
                if (turnedDown(index)) placed[other].row == placed[index].row + sizes[index].rows else placed[other].row + sizes[other].rows == placed[index].row
        }
        val currentSlots by rememberUpdatedState(slots)
        val currentWidgets by rememberUpdatedState(widgets)
        val currentSizes by rememberUpdatedState(sizes)
        val currentSpec by rememberUpdatedState(spec)
        var dragging by remember { mutableStateOf<String?>(null) }
        var dragAt by remember { mutableStateOf(Offset.Zero) }

        Box(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).pointerInput(edit.editing) {
                detectTapGestures(onTap = { if (edit.editing) edit.select(null) }, onLongPress = { if (!edit.editing) onHomeMenu() })
            },
        ) {
            val gridLines = skin.onSurface.copy(alpha = 0.1f)
            Box(
                // At least as tall as the page, so the bar down to the dock has the whole way to run.
                Modifier.fillMaxWidth().height(maxOf(gap * 2 + spec.heightOf(maxOf(rows, 1) + if (edit.editing) 2 else 0).dp, if (joined != null) viewport else 0.dp)).drawBehind {
                    if (joined != null) {
                        val from = slots[joined].bottom + between.toPx()
                        val to = size.height - between.toPx()
                        if (to > from) drawRect(lcarsBarColor("Dock"), Offset(0f, from), Size(LcarsBarWidth.toPx(), to - from))
                    }
                    // The cells themselves, while rearranging, so sizes and gaps can be judged by eye.
                    if (!edit.editing) return@drawBehind
                    val cell = Size(spec.cellDp.dp.toPx(), spec.rowDp.dp.toPx())
                    for (row in 0 until rows + 2) for (column in 0 until layout.columns) {
                        drawRoundRect(
                            gridLines, Offset(column * (cell.width + between.toPx()), gap.toPx() + row * (cell.height + between.toPx())), cell,
                            CornerRadius(10.dp.toPx()),
                        )
                    }
                },
            ) {
                if (widgets.isEmpty()) Text(
                    if (edit.editing) "Tap Widget to put one on this page, or Page to add another." else "An empty page. Hold anywhere to add widgets.",
                    Modifier.fillMaxWidth().padding(top = 48.dp), textAlign = TextAlign.Center, color = skin.onSurface, style = MaterialTheme.typography.bodyMedium,
                )
                widgets.forEachIndexed { index, widget ->
                    key(widget.id) {
                        val slot = slots[index]
                        val isDragged = dragging == widget.id
                        val settled by animateIntOffsetAsState(slot.topLeft.round(), label = "slot")
                        val at = if (isDragged) dragAt.round() else settled
                        val size = with(density) { Modifier.size(slot.width.toDp(), slot.height.toDp()) }
                        // Another app's widget keeps its touches, so holding one to rearrange has to be caught on the way in.
                        val hold = if (widget.type == WidgetType.SYSTEM && !edit.editing) Modifier.holdOver { edit.start(widget.id) } else Modifier
                        Box(Modifier.offset { at }.then(size).zIndex(if (isDragged) 1f else 0f).then(hold)) {
                            // Every other panel is turned over, for skins whose frames have a direction.
                            WidgetItem(widget, Modifier.fillMaxSize(), host, onSearch, down = turnedDown(index), junction = skin.lcars && meetsAnother(index)) { edit.start(widget.id) }
                            if (edit.editing) {
                                val selected = edit.selected == widget.id
                                val shape = RoundedCornerShape(skin.radius)
                                // Covers the widget so touches rearrange it instead of using it.
                                Box(
                                    Modifier.fillMaxSize().clip(shape)
                                        .then(if (selected) Modifier.border(2.dp, skin.accent, shape) else Modifier)
                                        .pointerInput(widget.id) { detectTapGestures { edit.select(widget.id) } }
                                        .pointerInput(widget.id) {
                                            // Dragging onto another widget takes its place in the order; the rest flow around.
                                            var lastTarget: String? = null
                                            detectDragGestures(
                                                onDragStart = {
                                                    edit.select(widget.id)
                                                    val me = currentWidgets.indexOfFirst { it.id == widget.id }
                                                    dragAt = currentSlots[me].topLeft
                                                    dragging = widget.id
                                                    lastTarget = null
                                                },
                                                onDragEnd = { dragging = null },
                                                onDragCancel = { dragging = null },
                                            ) { change, amount ->
                                                change.consume()
                                                dragAt += amount
                                                val me = currentWidgets.indexOfFirst { it.id == widget.id }
                                                if (me < 0) return@detectDragGestures
                                                val centre = dragAt + Offset(currentSlots[me].width / 2, currentSlots[me].height / 2)
                                                val over = currentSlots.indices.firstOrNull { it != me && currentSlots[it].contains(centre) }
                                                val overId = over?.let { currentWidgets[it].id }
                                                // After a swap the displaced widget can still be under the finger; it has to be left before it can swap back.
                                                if (overId == null) lastTarget = null
                                                else if (overId != lastTarget) {
                                                    lastTarget = overId
                                                    actions.onReorder(me, over)
                                                }
                                            }
                                        },
                                )
                                if (selected) Box(
                                    Modifier.align(Alignment.BottomEnd).padding(4.dp).size(34.dp).background(skin.accent, CircleShape)
                                        .pointerInput(widget.id) {
                                            var start = CellSize(1, 1)
                                            var pulled = Offset.Zero
                                            detectDragGestures(onDragStart = {
                                                start = currentSizes[currentWidgets.indexOfFirst { it.id == widget.id }]
                                                pulled = Offset.Zero
                                            }) { change, amount ->
                                                change.consume()
                                                pulled += amount
                                                val grid = currentSpec
                                                // Snaps to whole cells, and stops at the smallest and largest this widget allows.
                                                val wanted = CellSize(
                                                    start.cols + (pulled.x.toDp().value / (grid.cellDp + grid.gapDp)).roundToInt(),
                                                    start.rows + (pulled.y.toDp().value / (grid.rowDp + grid.gapDp)).roundToInt(),
                                                )
                                                val allowed = grid.clamp(wanted, host.limits(widget, density.density))
                                                val me = currentWidgets.indexOfFirst { it.id == widget.id }
                                                if (me >= 0 && allowed != currentSizes[me]) actions.onResize(widget.id, allowed)
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.OpenInFull, "Drag to resize", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditBar(edit: HomeEditState, actions: HomeEditActions) {
    val selected = edit.selected
    WidgetFrame(WidgetStyle.SOLID, Modifier.fillMaxWidth(), label = "Modify", down = true) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            BarButton(Icons.Rounded.Add, "Widget", onClick = actions.onAdd)
            BarButton(Icons.Rounded.AddToHomeScreen, "Page", enabled = actions.onAddPage != null) { actions.onAddPage?.invoke() }
            BarButton(Icons.Rounded.GridView, "Grid", onClick = actions.onGrid)
            BarButton(Icons.Rounded.Tune, "Customise", enabled = selected != null) { selected?.let(actions.onCustomise) }
            BarButton(Icons.Rounded.Delete, "Remove", enabled = selected != null) { selected?.let(actions.onRemove) }
            BarButton(Icons.Rounded.Check, "Done", onClick = edit::stop)
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val tint = if (enabled) skin.onSurface else skin.onSurface.copy(alpha = 0.3f)
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClickLabel = label, onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (enabled && label == "Done") skin.accent else tint)
        Text(label.cased(), style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

@Composable
private fun Dock(
    entries: List<DockEntry>,
    iconSizeDp: Int,
    swipeUp: Boolean,
    openFolder: String?,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onFolder: (DockFolder) -> Unit,
    onFolderMenu: (DockFolder) -> Unit,
) {
    WidgetFrame(WidgetStyle.GLASS, Modifier.fillMaxWidth(), label = "Dock", down = true) {
        if (entries.isEmpty()) {
            Text(
                (if (swipeUp) "Swipe up" else "Swipe left") + " for your apps. Hold one to pin it here.",
                Modifier.fillMaxWidth().padding(20.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
            )
        } else BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            // As many as fit: the inner screen shows more of the same dock than the cover screen.
            val fits = (maxWidth / (iconSizeDp + 18).dp).toInt().coerceAtLeast(1)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                entries.take(fits).forEach { entry ->
                    when (entry) {
                        is DockEntry.App -> AppTile(entry.app, iconSizeDp.dp, showLabel = false, onClick = { onLaunch(entry.app) }, onLongClick = { onAppMenu(entry.app) })
                        is DockEntry.Folder -> FolderTile(entry, iconSizeDp.dp, open = entry.folder.id == openFolder, onClick = { onFolder(entry.folder) }, onLongClick = { onFolderMenu(entry.folder) })
                    }
                }
            }
        }
    }
}

/** A dock folder: a plate showing the first four apps inside it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTile(entry: DockEntry.Folder, size: Dp, open: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(size * 0.28f)
    val badges = LocalBadges.current
    val unread = badges.unread(entry.apps)
    // Keeps flashing for as long as one of its apps has something unread, though only while the launcher is in front.
    val flash = remember { Animatable(0f) }
    val waiting = unread > 0
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(waiting, lifecycle) {
        if (!waiting) flash.animateTo(0f, tween(200))
        else lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                while (true) {
                    flash.animateTo(1f, tween(180))
                    flash.animateTo(0f, tween(420))
                    delay(500)
                }
            } finally {
                // Never left lit when the launcher steps back mid-flash.
                withContext(NonCancellable) { flash.snapTo(0f) }
            }
        }
    }
    val (badgeFill, _) = badgeColors()
    if (skin.lcars) {
        // An LCARS panel button: a flat square block with how many apps it holds up top and its name in the bottom corner.
        val block = LcarsPalette[entry.folder.id.hashCode().mod(LcarsPalette.size)]
        Box(
            Modifier.padding(vertical = 8.dp, horizontal = 2.dp).size(size + 16.dp, size)
                .drawBehind { drawRect(if (open) LcarsHighlight else lerp(block, LcarsHighlight, flash.value)) }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onClickLabel = "Open ${entry.folder.name}"),
        ) {
            // Unread notifications inside, as a black plate cut into the block.
            if (unread > 0) Text(
                if (unread > 99) "99+" else unread.toString(),
                Modifier.align(Alignment.TopStart).padding(3.dp).background(Color.Black, CircleShape).padding(horizontal = 6.dp).semantics { contentDescription = "$unread unread" },
                color = badgeFill, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1,
            )
            Text(
                "%02d".format(entry.apps.size), Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 7.dp),
                color = Color.Black.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, maxLines = 1,
            )
            Text(
                entry.folder.name.uppercase(), Modifier.align(Alignment.BottomEnd).padding(start = 4.dp, end = 7.dp, bottom = 3.dp),
                color = Color.Black, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    Box(
        Modifier.clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick, onClickLabel = "Open ${entry.folder.name}").padding(vertical = 8.dp, horizontal = 2.dp),
    ) {
        val plate = skin.surface.copy(alpha = 0.7f)
        Column(
            Modifier.size(size).graphicsLayer { scaleX = 1f + 0.06f * flash.value; scaleY = scaleX }
                .drawBehind { drawOutline(shape.createOutline(this.size, layoutDirection, this), lerp(plate, badgeFill, flash.value * 0.75f)) }
                .border(1.5.dp, if (open) skin.accent else skin.onSurface.copy(alpha = 0.18f), shape).padding(size * 0.12f),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            val mini = size * 0.34f
            // The apps with something unread come first, so they are the four on show.
            entry.apps.sortedByDescending { (badges[it.badgeKey] ?: 0) > 0 }.take(4).chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    pair.forEach { app ->
                        Box {
                            AppIcon(app, mini)
                            if ((badges[app.badgeKey] ?: 0) > 0) BadgeDot(mini * 0.42f, Modifier.align(Alignment.TopEnd).offset(x = mini * 0.14f, y = -mini * 0.14f))
                        }
                    }
                }
            }
        }
        if (unread > 0) CountBadge(unread, Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-5).dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DockFlyout(entry: DockEntry.Folder, iconSizeDp: Int, onLaunch: (AppEntry) -> Unit, onAppMenu: (AppEntry) -> Unit) {
    WidgetFrame(WidgetStyle.SOLID, Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { } }, label = entry.folder.name) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.folder.name, style = MaterialTheme.typography.titleSmall, color = LocalSkin.current.accent, maxLines = 1)
            if (entry.apps.isEmpty()) Text(
                "Empty. Hold any app and choose this folder to add it.", Modifier.padding(12.dp),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
            ) else FlowRow(horizontalArrangement = Arrangement.Center) {
                entry.apps.forEach { app ->
                    AppTile(app, iconSizeDp.dp, showLabel = true, onClick = { onLaunch(app) }, onLongClick = { onAppMenu(app) }, modifier = Modifier.width((iconSizeDp + 24).dp))
                }
            }
        }
    }
}

/** One dot per swipeable page, with the feed, home pages and drawer told apart only by position. */
@Composable
fun PageDots(selected: Int, count: Int, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // On LCARS, a strip of bar segments with the current page's the long one.
        if (skin.lcars) repeat(count) { page ->
            Box(Modifier.size(if (page == selected) 26.dp else 12.dp, 6.dp).background(if (page == selected) Color(0xFFFFF4E6) else LcarsPalette[page % LcarsPalette.size], CircleShape))
        } else repeat(count) { page ->
            Box(Modifier.size(6.dp).background(if (page == selected) skin.accent else skin.onSurface.copy(alpha = 0.35f), CircleShape))
        }
    }
}
