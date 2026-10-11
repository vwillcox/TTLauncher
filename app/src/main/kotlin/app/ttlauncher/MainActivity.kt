package app.ttlauncher

import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ttlauncher.apps.FolderSorter
import app.ttlauncher.apps.AppEntry
import app.ttlauncher.apps.IconTheme
import app.ttlauncher.apps.NotificationWatcher
import app.ttlauncher.data.Article
import app.ttlauncher.data.BarAction
import app.ttlauncher.data.DOCK_FOLDER_PREFIX
import app.ttlauncher.data.DrawerMode
import app.ttlauncher.data.MAX_HOME_PAGES
import app.ttlauncher.data.MacroButton
import app.ttlauncher.data.PageTransition
import app.ttlauncher.data.RailSide
import app.ttlauncher.data.ScreenLayout
import app.ttlauncher.data.WidgetConfig
import app.ttlauncher.data.WidgetPage
import app.ttlauncher.data.WidgetType
import app.ttlauncher.data.layout
import app.ttlauncher.data.moveWidget
import app.ttlauncher.data.pageOf
import app.ttlauncher.data.widget
import app.ttlauncher.data.withLayout
import app.ttlauncher.data.withPage
import app.ttlauncher.data.withWidget
import app.ttlauncher.data.withoutWidget
import app.ttlauncher.ui.AddWidgetSheet
import app.ttlauncher.ui.DockEntry
import app.ttlauncher.ui.DockFolderSheet
import app.ttlauncher.ui.GridSheet
import app.ttlauncher.ui.HomeEditActions
import app.ttlauncher.ui.HomeEditState
import app.ttlauncher.ui.HomeMenuSheet
import app.ttlauncher.ui.HomeBar
import app.ttlauncher.ui.WidgetOptionsSheet
import app.ttlauncher.feed.TopicFilter
import app.ttlauncher.ui.AppMenuSheet
import app.ttlauncher.ui.BarActions
import app.ttlauncher.ui.LocalBadges
import app.ttlauncher.ui.LocalBarActions
import app.ttlauncher.ui.ArticleMenuSheet
import app.ttlauncher.ui.DrawerPage
import app.ttlauncher.ui.FeedPage
import app.ttlauncher.ui.HomePage
import app.ttlauncher.ui.LocalIconTheme
import app.ttlauncher.ui.LocalMacroRunner
import app.ttlauncher.ui.LocalIcons
import app.ttlauncher.ui.LocalSkin
import app.ttlauncher.ui.SettingsSheet
import app.ttlauncher.ui.SettingsTab
import app.ttlauncher.ui.WidgetHostController
import app.ttlauncher.ui.colorScheme
import app.ttlauncher.ui.expandNotificationShade
import app.ttlauncher.ui.rememberWallpaperActions
import app.ttlauncher.ui.slantedPagerDrag
import app.ttlauncher.ui.LocalWeather
import app.ttlauncher.ui.inPlace
import app.ttlauncher.ui.pageTransition
import app.ttlauncher.ui.resolveSkin
import app.ttlauncher.ui.transporterBeam
import app.ttlauncher.ui.typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FEED_PAGE = 0
/** The first home page. More follow it, then the drawer. */
private const val HOME_PAGE = 1

class MainActivity : ComponentActivity() {
    private val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private lateinit var widgetHost: WidgetHostController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(AndroidColor.TRANSPARENT), SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        super.onCreate(savedInstanceState)
        widgetHost = WidgetHostController(this)
        setContent { Launcher(application as LauncherApp, widgetHost, homePressed) }
    }

    // Pressing home while already home returns to the middle page.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePressed.tryEmit(Unit)
    }

    override fun onStart() {
        super.onStart()
        widgetHost.start()
    }

    override fun onStop() {
        widgetHost.stop()
        super.onStop()
    }

    @Deprecated("AppWidgetHost only reports a widget's setup result this way")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        if (!widgetHost.onActivityResult(requestCode, resultCode)) super.onActivityResult(requestCode, resultCode, data)
    }
}

private sealed interface Sheet {
    class Settings(val tab: Int) : Sheet
    class AppMenu(val app: AppEntry) : Sheet
    class ArticleMenu(val article: Article) : Sheet
    class WidgetOptions(val id: String) : Sheet
    class DockFolderMenu(val id: String) : Sheet
    data object HomeMenu : Sheet
    data object AddWidget : Sheet
    data object Grid : Sheet
}

@Composable
private fun Launcher(app: LauncherApp, widgetHost: WidgetHostController, homePressed: Flow<Unit>) {
    val ready by app.store.ready.collectAsStateWithLifecycle()
    if (!ready) return
    val state by app.store.state.collectAsStateWithLifecycle()
    val installed by app.apps.apps.collectAsStateWithLifecycle()
    val unread by NotificationWatcher.counts.collectAsStateWithLifecycle()
    // The Fold's cover screen is about 370dp wide and its inner screen about 700dp. Each has its own home pages.
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val wallpaperSet = if (wide) state.innerWallpaper else state.coverWallpaper
    // A skin with a solid background covers the system wallpaper, but not a picture chosen for this screen.
    val skin = resolveSkin(state).let { if (wallpaperSet != 0L) it.copy(opaque = false) else it }

    val view = LocalView.current
    SideEffect {
        (view.context as? ComponentActivity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !skin.dark
                isAppearanceLightNavigationBars = !skin.dark
            }
        }
    }

    val apps = remember(installed, state.hidden, state.categoryOverrides, state.folders) {
        val sorter = FolderSorter(state.folders)
        installed.filter { it.key !in state.hidden }.map { entry ->
            entry.copy(folder = sorter.folderFor(entry.category, entry.component.packageName, entry.label, state.categoryOverrides[entry.key]))
        }
    }
    val dock = remember(apps, state.dock, state.dockFolders) {
        val byKey = apps.associateBy { it.key }
        // An app in a dock folder is shown there only, never beside it as well.
        val inFolders = state.dockFolders.filter { DOCK_FOLDER_PREFIX + it.id in state.dock }.flatMap { it.apps }.toSet()
        state.dock.mapNotNull { entry ->
            if (!entry.startsWith(DOCK_FOLDER_PREFIX)) byKey[entry]?.takeIf { entry !in inFolders }?.let { DockEntry.App(it) }
            else state.dockFolders.firstOrNull { it.id == entry.removePrefix(DOCK_FOLDER_PREFIX) }?.let { folder -> DockEntry.Folder(folder, folder.apps.mapNotNull(byKey::get)) }
        }
    }
    val iconTheme = remember(state.iconShape, state.iconStyle, state.iconPack, skin) {
        // Themed icons become flat colour blocks with black glyphs on LCARS.
        if (skin.lcars) IconTheme(state.iconShape, state.iconStyle, state.iconPack, skin.accent.toArgb(), AndroidColor.BLACK)
        else IconTheme(state.iconShape, state.iconStyle, state.iconPack, lerp(skin.surface, skin.accent, 0.22f).toArgb(), skin.accent.toArgb())
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val layout = state.layout(wide)
    val homePages = layout.pages.size
    val swipeUp = state.drawerMode == DrawerMode.SWIPE_UP
    // The feed, then the home pages, then the drawer unless it slides up instead.
    val pageCount = 1 + homePages + if (swipeUp) 0 else 1
    val drawerPage = 1 + homePages
    val pager = rememberPagerState(initialPage = HOME_PAGE) { pageCount }
    val currentHome = (pager.currentPage - HOME_PAGE).coerceIn(0, homePages - 1)
    val edit = remember { HomeEditState() }
    // Only used when the drawer slides up over the home page.
    var drawerOpen by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var focusSearch by remember { mutableIntStateOf(0) }
    // A page to scroll to once it exists, for one that was only just added.
    var pendingPage by remember { mutableIntStateOf(-1) }
    // How tall the dock and the page dots turned out, so each page can keep clear of them.
    var dockHeight by remember { mutableStateOf(0.dp) }
    var dotsHeight by remember { mutableStateOf(0.dp) }
    val dockShowing = state.showDock && !edit.editing
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        homePressed.collect {
            sheet = null
            drawerOpen = false
            edit.stop()
            pager.animateScrollToPage(HOME_PAGE)
        }
    }
    LaunchedEffect(pendingPage, pageCount) {
        if (pendingPage in 0 until pageCount) {
            pager.animateScrollToPage(pendingPage)
            pendingPage = -1
        }
    }
    // Back never leaves the launcher; it steps back towards the first home page.
    BackHandler {
        when {
            edit.editing -> edit.stop()
            drawerOpen -> drawerOpen = false
            else -> scope.launch { pager.animateScrollToPage(HOME_PAGE) }
        }
    }
    val drawerShowing = if (swipeUp) drawerOpen else pager.settledPage == drawerPage
    LaunchedEffect(drawerShowing) { if (!drawerShowing) focusManager.clearFocus() }
    // Folding the phone mid-edit swaps to the other screen's layout, where the selection means nothing.
    LaunchedEffect(wide) { edit.stop() }

    val wallpaper = rememberWallpaperActions()
    val pullDown: (() -> Unit)? = if (state.pullDownShade) ({ expandNotificationShade(context) }) else null

    // How far the whole launcher is beamed out: 0 when it's all here, 1 when an app has taken its place.
    val beam = remember { Animatable(0f) }
    val beaming = state.pageTransition == PageTransition.TRANSPORTER && state.beamApps
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Android draws an app's own way out, and the switch from one app to another, itself. What a launcher
    // can do is come back in its own way underneath: beamed out while it's away, and in again on return.
    LifecycleStartEffect(beaming) {
        if (beaming && beam.value > 0f) scope.launch { beam.animateTo(0f, tween(600, easing = LinearEasing)) }
        onStopOrDispose { scope.launch { beam.snapTo(if (beaming) 1f else 0f) } }
    }

    val launch: (AppEntry) -> Unit = { entry ->
        focusManager.clearFocus()
        if (!beaming) app.apps.launch(entry)
        else {
            // The app fades in over the launcher as the launcher dissolves into the beam beneath it.
            app.apps.launch(entry, ActivityOptions.makeCustomAnimation(context, R.anim.beam_in, R.anim.beam_hold).toBundle())
            scope.launch {
                beam.animateTo(1f, tween(480, easing = LinearEasing))
                // An app that never took the screen, or only part of it, mustn't leave the launcher empty.
                delay(1200)
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) beam.animateTo(0f, tween(600, easing = LinearEasing))
            }
        }
        drawerOpen = false
    }

    fun updateLayout(change: (ScreenLayout) -> ScreenLayout) = app.store.update { it.withLayout(wide, change) }

    fun addWidget(widget: WidgetConfig) {
        updateLayout { it.withPage(currentHome) { page -> page.copy(widgets = page.widgets + widget) } }
        sheet = null
        // Straight into rearranging, with the new widget selected, so it can be sized and placed at once.
        edit.start(widget.id)
    }

    fun removeWidget(id: String) {
        layout.widget(id)?.takeIf { it.type == WidgetType.SYSTEM }?.let { widgetHost.remove(it.appWidgetId) }
        updateLayout { it.withoutWidget(id) }
        edit.select(null)
    }

    fun addPage() {
        updateLayout { it.copy(pages = it.pages + WidgetPage("p" + System.currentTimeMillis())) }
        pendingPage = HOME_PAGE + homePages
        sheet = null
    }

    fun openDrawer() {
        if (swipeUp) drawerOpen = true else scope.launch { pager.animateScrollToPage(drawerPage) }
    }

    fun barAction(action: BarAction): (() -> Unit)? = when (action) {
        BarAction.NONE -> null
        BarAction.DRAWER -> ::openDrawer
        BarAction.SEARCH -> ({ focusSearch++; openDrawer() })
        BarAction.NOTIFICATIONS -> ({ expandNotificationShade(context) })
        BarAction.NEWS -> ({ scope.launch { pager.animateScrollToPage(FEED_PAGE) } })
        BarAction.EDIT -> ({ edit.start(null) })
        BarAction.SETTINGS -> ({ sheet = Sheet.Settings(SettingsTab.SKIN) })
    }
    val runMacro: (MacroButton) -> Unit = { button ->
        when {
            button.app != null -> installed.firstOrNull { it.key == button.app }?.let(launch)
            button.link.isNotBlank() -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(button.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { Toast.makeText(context, "Nothing on this phone opens that link", Toast.LENGTH_SHORT).show() }
            else -> barAction(button.action)?.invoke()
        }
    }
    // Not while rearranging, when touches on a widget move it.
    val barActions = if (edit.editing) null else BarActions(barAction(state.barTap), barAction(state.barHold))

    val drawer: @Composable ((() -> Unit)?) -> Unit = { onClose ->
        DrawerPage(
            apps, state.folders, wide, state.iconSizeDp, state.showLabels, state.wallpaperDim, state.categorySwipe,
            railOnRight = state.railSide == RailSide.RIGHT, focusSearch, launch, onAppMenu = { sheet = Sheet.AppMenu(it) }, onClose = onClose, onPullDown = pullDown,
            bottomReserve = dotsHeight,
        )
    }

    CompositionLocalProvider(LocalSkin provides skin, LocalIcons provides app.icons, LocalIconTheme provides iconTheme, LocalMacroRunner provides runMacro, LocalWeather provides app.weather, LocalBadges provides if (state.badges) unread else emptyMap()) {
        MaterialTheme(colorScheme = skin.colorScheme(), typography = skin.typography()) {
            Box(Modifier.fillMaxSize()) {
                // This screen's own wallpaper, if it has one, drawn over the system's.
                val ownWallpaper by produceState<ImageBitmap?>(null, wide, wallpaperSet) {
                    value = if (wallpaperSet == 0L) null
                    else withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(WallpaperActivity.file(context, wide).path)?.asImageBitmap() }.getOrNull() }
                }
                ownWallpaper?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                // The home page fades out under the drawer rather than showing through it.
                Box(Modifier.fillMaxSize().transporterBeam(seed = -1) { beam.value }) {
                val pagesAlpha by animateFloatAsState(if (swipeUp && drawerOpen) 0f else 1f, tween(180), label = "pages")
                HorizontalPager(
                    state = pager,
                    // The news scrolls up and down, and would keep any swipe across it that wasn't dead level.
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = pagesAlpha }.slantedPagerDrag(pager) { pager.currentPage == FEED_PAGE },
                    beyondViewportPageCount = 1,
                    // Sideways drags move widgets while rearranging, not pages. At rest on the news the pager's own dragging
                    // is off too, the swipe above being the way off that page: left on, it would take swipes towards the
                    // page that isn't there on the news' other side, and hold them against scrolling.
                    userScrollEnabled = !edit.editing && pager.settledPage != FEED_PAGE,
                    // The transporter is given time to be seen; the rest settle as briskly as ever.
                    flingBehavior = if (state.pageTransition == PageTransition.TRANSPORTER) PagerDefaults.flingBehavior(pager, snapAnimationSpec = tween(650, easing = LinearEasing)) else PagerDefaults.flingBehavior(pager),
                    key = { page -> if (page == FEED_PAGE) "feed" else layout.pages.getOrNull(page - HOME_PAGE)?.id ?: "drawer" },
                ) { page ->
                    val home = layout.pages.getOrNull(page - HOME_PAGE)
                    CompositionLocalProvider(LocalBarActions provides barActions) {
                    Box(Modifier.fillMaxSize().pageTransition(pager, page, state.pageTransition)) {
                    when {
                        page == FEED_PAGE -> FeedPageHost(app, onFeed = pager.settledPage == FEED_PAGE, bottomReserve = dotsHeight, onArticleMenu = { sheet = Sheet.ArticleMenu(it) }, onSettings = { sheet = Sheet.Settings(SettingsTab.FEED) })
                        home != null -> HomePage(
                            layout, home, state.wallpaperDim, widgetHost, edit,
                            actions = HomeEditActions(
                                onReorder = { from, to ->
                                    updateLayout { l ->
                                        l.withPage(page - HOME_PAGE) { p ->
                                            if (from !in p.widgets.indices || to !in p.widgets.indices) p
                                            else p.copy(widgets = p.widgets.toMutableList().apply { add(to, removeAt(from)) })
                                        }
                                    }
                                },
                                onResize = { id, size -> updateLayout { it.withWidget(id) { widget -> widget.copy(size = size) } } },
                                onAdd = { sheet = Sheet.AddWidget },
                                onAddPage = (::addPage).takeIf { homePages < MAX_HOME_PAGES },
                                onGrid = { sheet = Sheet.Grid },
                                onCustomise = { sheet = Sheet.WidgetOptions(it) },
                                onRemove = ::removeWidget,
                            ),
                            bottomReserve = dotsHeight + if (dockShowing) dockHeight else 0.dp,
                            dockBelow = dockShowing,
                            onHomeMenu = { sheet = Sheet.HomeMenu },
                            onSearch = { focusSearch++; openDrawer() },
                            onOpenDrawer = if (swipeUp) ({ drawerOpen = true }) else null,
                            onPullDown = pullDown,
                        )
                        else -> drawer(null)
                    }
                    }
                    }
                }
                CompositionLocalProvider(LocalBarActions provides barActions) {
                HomeBar(
                    dock.takeIf { dockShowing }, state.iconSizeDp, pager, homePages = HOME_PAGE until HOME_PAGE + homePages,
                    fadeAway = state.pageTransition.inPlace,
                    pagingEnabled = !edit.editing,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = pagesAlpha },
                    onDockHeight = { dockHeight = it },
                    onDotsHeight = { dotsHeight = it },
                    onLaunch = launch,
                    onAppMenu = { sheet = Sheet.AppMenu(it) },
                    onDockFolderMenu = { sheet = Sheet.DockFolderMenu(it.id) },
                    onOpenDrawer = if (swipeUp) ({ drawerOpen = true }) else null,
                    onPullDown = pullDown,
                )
                }
                AnimatedVisibility(
                    visible = swipeUp && drawerOpen,
                    enter = slideInVertically(tween(220)) { it / 3 } + fadeIn(tween(160)),
                    exit = slideOutVertically(tween(180)) { it / 3 } + fadeOut(tween(140)),
                ) { drawer { drawerOpen = false } }
                }
            }
            DefaultHomePrompt(skip = state.skipDefaultPrompt, onNeverAsk = { app.store.update { it.copy(skipDefaultPrompt = true) } })
            when (val open = sheet) {
                is Sheet.Settings -> SettingsSheet(
                    open.tab, state, app.store::update,
                    hiddenApps = remember(installed, state.hidden) { installed.filter { it.key in state.hidden } },
                    iconPacks = remember { app.icons.installedPacks() },
                    wallpaper = wallpaper,
                    homePages = homePages,
                    onAddPage = (::addPage).takeIf { homePages < MAX_HOME_PAGES },
                    readStories = app.feed.read.collectAsStateWithLifecycle().value.size,
                    onForgetRead = app.feed::clearRead,
                    onDismiss = { sheet = null },
                )
                is Sheet.AppMenu -> AppMenuSheet(
                    // Looked up again so the sheet follows the app when it's re-filed.
                    apps.firstOrNull { it.key == open.app.key } ?: open.app, state, app.store::update,
                    onInfo = { app.apps.showInfo(open.app) }, onUninstall = { app.apps.uninstall(open.app) }, onDismiss = { sheet = null },
                )
                is Sheet.ArticleMenu -> ArticleMenuSheet(open.article, state, app.store::update, onDismiss = { sheet = null })
                is Sheet.DockFolderMenu -> {
                    val entry = dock.firstNotNullOfOrNull { (it as? DockEntry.Folder)?.takeIf { f -> f.folder.id == open.id } }
                    if (entry == null) LaunchedEffect(open) { sheet = null }
                    else DockFolderSheet(entry.folder, entry.apps, app.store::update, onDismiss = { sheet = null })
                }
                Sheet.HomeMenu -> HomeMenuSheet(
                    wallpaper,
                    onAddWidget = { sheet = Sheet.AddWidget },
                    onEdit = { sheet = null; edit.start(null) },
                    onAddPage = (::addPage).takeIf { homePages < MAX_HOME_PAGES },
                    onSettings = { sheet = Sheet.Settings(SettingsTab.SKIN) },
                    onDismiss = { sheet = null },
                )
                Sheet.AddWidget -> AddWidgetSheet(
                    widgetHost,
                    onAdd = ::addWidget,
                    onAddFromApp = { provider ->
                        sheet = null
                        widgetHost.add(provider) { id -> addWidget(WidgetConfig("w" + System.currentTimeMillis(), WidgetType.SYSTEM, appWidgetId = id)) }
                    },
                    onDismiss = { sheet = null },
                )
                is Sheet.WidgetOptions -> {
                    val widget = layout.widget(open.id)
                    if (widget == null) LaunchedEffect(open) { sheet = null }
                    else WidgetOptionsSheet(
                        widget,
                        name = remember(widget.type, widget.appWidgetId) {
                            if (widget.type != WidgetType.SYSTEM) widget.type.label
                            else runCatching { widgetHost.manager.getAppWidgetInfo(widget.appWidgetId)?.loadLabel(context.packageManager) }.getOrNull() ?: widget.type.label
                        },
                        pageCount = homePages,
                        currentPage = layout.pageOf(open.id),
                        apps = installed,
                        onChange = { change -> updateLayout { it.withWidget(open.id, change) } },
                        onMoveToPage = { target ->
                            updateLayout { it.moveWidget(open.id, target) }
                            scope.launch { pager.animateScrollToPage(HOME_PAGE + target) }
                        },
                        onDismiss = { sheet = null },
                    )
                }
                Sheet.Grid -> GridSheet(
                    layout, wide, currentHome,
                    onChange = ::updateLayout,
                    onAddPage = ::addPage,
                    onRemovePage = {
                        layout.pages.getOrNull(currentHome)?.widgets?.filter { it.type == WidgetType.SYSTEM }?.forEach { widgetHost.remove(it.appWidgetId) }
                        updateLayout { it.copy(pages = it.pages.filterIndexed { index, _ -> index != currentHome }) }
                        edit.select(null)
                        sheet = null
                    },
                    onDismiss = { sheet = null },
                )
                null -> Unit
            }
        }
    }
}

/** Offers, once each time the launcher starts, to make it the home app while something else still is. */
@Composable
private fun DefaultHomePrompt(skip: Boolean, onNeverAsk: () -> Unit) {
    val context = LocalContext.current
    val roles = remember { context.getSystemService(RoleManager::class.java) }
    fun isDefault() = runCatching { roles.isRoleHeld(RoleManager.ROLE_HOME) }.getOrDefault(true)
    var isDefault by remember { mutableStateOf(isDefault()) }
    var answered by rememberSaveable { mutableStateOf(false) }
    // Checked again on return, in case it was changed in the system settings meanwhile.
    LifecycleResumeEffect(Unit) {
        isDefault = isDefault()
        onPauseOrDispose { }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = isDefault() }
    if (isDefault || skip || answered) return
    AlertDialog(
        onDismissRequest = { answered = true },
        title = { Text("Make TT Launcher your home screen?") },
        text = { Text("The home button and gesture will open it. You can change back at any time in Settings > Apps > Default apps > Home app.") },
        confirmButton = {
            Button(onClick = {
                answered = true
                // Android's own chooser; where a phone has none, its home app settings instead.
                runCatching { request.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
                    .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } }
            }) { Text("Set as default") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { answered = true; onNeverAsk() }) { Text("Don't ask again") }
                TextButton(onClick = { answered = true }) { Text("Not now") }
            }
        },
    )
}

@Composable
private fun FeedPageHost(app: LauncherApp, onFeed: Boolean, bottomReserve: Dp, onArticleMenu: (Article) -> Unit, onSettings: () -> Unit) {
    val state by app.store.state.collectAsStateWithLifecycle()
    val all by app.feed.articles.collectAsStateWithLifecycle()
    val refreshing by app.feed.refreshing.collectAsStateWithLifecycle()
    val error by app.feed.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Nothing is fetched until the page is actually looked at, and then only if the last fetch is stale.
    LaunchedEffect(onFeed, state.feeds) { if (onFeed) app.feed.refresh(state.feeds, force = false) }

    val fromFeeds = remember(all, state.feeds) { val urls = state.feeds.map { it.url }.toSet(); all.filter { it.feedUrl in urls } }
    val read by app.feed.read.collectAsStateWithLifecycle()
    val unmuted = remember(fromFeeds, state.mutedTopics, state.mutedPacks) {
        val filter = TopicFilter.of(state.mutedTopics, state.mutedPacks)
        fromFeeds.filterNot(filter::hides)
    }
    val shown = remember(unmuted, read, state.hideRead) { if (state.hideRead) unmuted.filterNot { it.link in read } else unmuted }
    FeedPage(
        shown, hiddenCount = fromFeeds.size - unmuted.size, readCount = unmuted.size - shown.size, hasSources = state.feeds.isNotEmpty(), refreshing = refreshing, error = error,
        showImages = state.feedImages, dim = state.wallpaperDim, bottomReserve = bottomReserve,
        onRefresh = { scope.launch { app.feed.refresh(state.feeds, force = true) } },
        onRead = { app.feed.markRead(it.link) },
        onArticleMenu = onArticleMenu, onSettings = onSettings,
    )
}
