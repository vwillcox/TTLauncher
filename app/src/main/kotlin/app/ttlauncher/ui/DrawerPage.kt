package app.ttlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ttlauncher.apps.OTHER_FOLDER
import app.ttlauncher.data.Folder
import app.ttlauncher.apps.AppEntry

/** The icons a folder can have, by the key stored in [Folder.icon]. */
val FolderIcons: Map<String, ImageVector> = linkedMapOf(
    "forum" to Icons.Rounded.Forum, "public" to Icons.Rounded.Public, "play" to Icons.Rounded.PlayCircle,
    "music" to Icons.Rounded.MusicNote, "movie" to Icons.Rounded.Movie, "book" to Icons.Rounded.MenuBook,
    "games" to Icons.Rounded.SportsEsports, "camera" to Icons.Rounded.PhotoCamera, "work" to Icons.Rounded.Work,
    "bag" to Icons.Rounded.ShoppingBag, "money" to Icons.Rounded.Payments, "flight" to Icons.Rounded.Flight,
    "car" to Icons.Rounded.DirectionsCar, "build" to Icons.Rounded.Build, "tune" to Icons.Rounded.Tune,
    "code" to Icons.Rounded.Code, "school" to Icons.Rounded.School, "fitness" to Icons.Rounded.FitnessCenter,
    "food" to Icons.Rounded.Restaurant, "home" to Icons.Rounded.Home, "star" to Icons.Rounded.Star,
    "lock" to Icons.Rounded.Lock, "heart" to Icons.Rounded.Favorite, "pets" to Icons.Rounded.Pets, "folder" to Icons.Rounded.Folder,
)

fun folderIcon(key: String): ImageVector = FolderIcons[key] ?: Icons.Rounded.Folder

/** A page of the drawer: one of the user's folders, the leftovers, or everything. */
private class DrawerTab(val id: String, val label: String, val icon: ImageVector, val apps: List<AppEntry>)

@Composable
fun DrawerPage(
    apps: List<AppEntry>,
    folders: List<Folder>,
    wide: Boolean,
    iconSizeDp: Int,
    showLabels: Boolean,
    dim: Float,
    /** Swiping sideways moves between categories; otherwise only the category bar does. */
    categorySwipe: Boolean,
    railOnRight: Boolean,
    /** Counts up each time the home page's search widget is tapped. */
    focusSearch: Int,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    /** Set when the drawer slides up over the home page, where pulling down past the top closes it. */
    onClose: (() -> Unit)?,
    onPullDown: (() -> Unit)?,
    /** Room to leave at the bottom for the page dots, when the drawer is one of the swipeable pages. */
    bottomReserve: Dp = 0.dp,
) {
    val skin = LocalSkin.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    // One page per folder that has apps, then whatever no folder claimed, then everything.
    val pages = remember(apps, folders) {
        val byFolder = apps.groupBy { it.folder }
        buildList {
            folders.forEach { folder -> byFolder[folder.id]?.let { add(DrawerTab(folder.id, folder.name, folderIcon(folder.icon), it)) } }
            byFolder[OTHER_FOLDER]?.let { add(DrawerTab("other", "Other", Icons.Rounded.MoreHoriz, it)) }
            add(DrawerTab("all", "All", Icons.Rounded.Apps, apps))
        }
    }
    val pager = rememberPagerState { pages.size }
    val matches = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) emptyList()
        else apps.filter { it.label.contains(q, ignoreCase = true) }.sortedBy { !it.label.startsWith(q, ignoreCase = true) }
    }
    val searching = query.isNotBlank()
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch > 0) runCatching { focus.requestFocus() } }

    @Composable
    fun Grid(shown: List<AppEntry>, modifier: Modifier) = LazyVerticalGrid(
        columns = GridCells.Adaptive((iconSizeDp + 26).dp),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        items(shown, key = { it.key }) { app ->
            AppTile(app, iconSizeDp.dp, showLabels, onClick = { query = ""; onLaunch(app) }, onLongClick = { onAppMenu(app) })
        }
    }

    CompositionLocalProvider(LocalContentColor provides skin.onSurface) {
        Column(
            Modifier.fillMaxSize().background(skin.pageScrim(dim))
                // Stops touches reaching the home page when this is drawn over it.
                .pointerInput(Unit) {}
                .overscrollTrigger(upward = false, onClose ?: onPullDown)
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp),
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp).focusRequester(focus),
                placeholder = { Text("Search apps".cased()) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                // Enter opens the top match.
                keyboardActions = KeyboardActions(onGo = { matches.firstOrNull()?.let { query = ""; onLaunch(it) } }),
                colors = if (skin.lcars) {
                    // A solid colour block with black lettering, like every other LCARS control.
                    val block = LcarsPalette[3]
                    TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = block, unfocusedContainerColor = block,
                        focusedTextColor = Color.Black, unfocusedTextColor = Color.Black, cursorColor = Color.Black,
                        focusedPlaceholderColor = Color.Black.copy(alpha = 0.6f), unfocusedPlaceholderColor = Color.Black.copy(alpha = 0.6f),
                        focusedLeadingIconColor = Color.Black, unfocusedLeadingIconColor = Color.Black,
                        focusedTrailingIconColor = Color.Black, unfocusedTrailingIconColor = Color.Black,
                    )
                } else TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = skin.surface, unfocusedContainerColor = skin.surface,
                ),
            )
            when {
                apps.isEmpty() || (searching && matches.isEmpty()) -> Text(
                    if (apps.isEmpty()) "Loading apps…" else "No apps match \"${query.trim()}\"",
                    Modifier.fillMaxWidth().weight(1f).padding(32.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
                )
                searching -> Grid(matches, Modifier.fillMaxWidth().weight(1f))
                else -> Row(Modifier.weight(1f)) {
                    val rail = @Composable {
                        Column(
                            Modifier.fillMaxHeight().width(if (wide) 96.dp else if (skin.lcars) 76.dp else 60.dp).verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(if (skin.lcars) 2.dp else 4.dp),
                        ) {
                            val badges = LocalBadges.current
                            pages.forEachIndexed { index, tab ->
                                RailItem(index, tab.icon, tab.label, tab.apps.size, badges.unread(tab.apps), wide, index == pager.currentPage) {
                                    scope.launch { if (categorySwipe) pager.animateScrollToPage(index) else pager.scrollToPage(index) }
                                }
                            }
                        }
                    }
                    if (!railOnRight) rail()
                    HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxHeight(), userScrollEnabled = categorySwipe, key = { pages[it].id }) { page ->
                        Grid(pages[page].apps, Modifier.fillMaxSize())
                    }
                    if (railOnRight) rail()
                }
            }
            Spacer(Modifier.height(if (onClose != null) 4.dp else bottomReserve))
        }
    }
}

private val PanelHeights = listOf(42.dp, 58.dp, 42.dp, 66.dp, 48.dp)

@Composable
private fun RailItem(index: Int, icon: ImageVector, label: String, count: Int, unread: Int, showLabel: Boolean, selected: Boolean, onClick: () -> Unit) {
    val skin = LocalSkin.current
    if (skin.lcars) {
        // The LCARS sidebar: square panels of uneven height stacked with a black line between, each with
        // its name in the bottom corner. The taller ones have room for a panel number as well.
        val height = PanelHeights[index % PanelHeights.size]
        Box(
            Modifier.fillMaxWidth().height(height).clickable(onClickLabel = label, onClick = onClick)
                .background(if (selected) Color(0xFFFFF4E6) else LcarsPalette[index % LcarsPalette.size]),
        ) {
            // Something unread on this page.
            if (unread > 0) Box(Modifier.align(Alignment.TopStart).padding(5.dp).size(9.dp).background(Color.Black, CircleShape))
            if (height >= 54.dp) Text(
                "%02d-%03d".format(index + 1, count), Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 3.dp),
                color = Color.Black.copy(alpha = 0.55f), maxLines = 1, style = MaterialTheme.typography.labelSmall,
            )
            Text(
                label.uppercase(), Modifier.align(Alignment.BottomEnd).padding(start = 4.dp, end = 8.dp, bottom = 3.dp), color = Color.Black, maxLines = 1,
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            )
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(skin.radius.coerceAtMost(20.dp))).clickable(onClickLabel = label, onClick = onClick)
            .background(if (selected) skin.accent.copy(alpha = 0.22f) else Color.Transparent).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Icon(icon, if (showLabel) null else label, tint = if (selected) skin.accent else skin.onSurface.copy(alpha = 0.75f))
            if (unread > 0) BadgeDot(10.dp, Modifier.align(Alignment.TopEnd))
        }
        if (showLabel) Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) skin.accent else skin.onSurface.copy(alpha = 0.75f))
    }
}
