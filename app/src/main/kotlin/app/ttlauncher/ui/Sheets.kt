@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.ttlauncher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ttlauncher.apps.AppCategory
import app.ttlauncher.apps.AppEntry
import app.ttlauncher.apps.IconPack
import app.ttlauncher.data.Article
import app.ttlauncher.data.DOCK_FOLDER_PREFIX
import app.ttlauncher.data.DefaultFolders
import app.ttlauncher.data.DockFolder
import app.ttlauncher.data.BarAction
import app.ttlauncher.data.DrawerMode
import app.ttlauncher.data.Folder
import app.ttlauncher.data.RailSide
import app.ttlauncher.data.FeedSource
import app.ttlauncher.data.IconShape
import app.ttlauncher.data.IconStyle
import app.ttlauncher.data.LauncherState
import app.ttlauncher.data.WidgetConfig
import app.ttlauncher.data.WidgetStyle
import app.ttlauncher.data.WidgetType
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.ttlauncher.apps.NotificationWatcher
import app.ttlauncher.data.PageTransition
import app.ttlauncher.data.MAX_HOME_PAGES
import app.ttlauncher.feed.TopicPacks
import app.ttlauncher.feed.normalizeFeedUrl

object SettingsTab {
    const val SKIN = 0
    const val ICONS = 1
    const val FEED = 2
    const val LAYOUT = 3
    const val FOLDERS = 4
}

typealias Update = ((LauncherState) -> LauncherState) -> Unit

@Composable
fun SettingsSheet(
    initialTab: Int,
    state: LauncherState,
    update: Update,
    hiddenApps: List<AppEntry>,
    iconPacks: List<IconPack>,
    wallpaper: WallpaperActions,
    /** On the screen in use. Adding is null once it has as many as it may. */
    homePages: Int,
    onAddPage: (() -> Unit)?,
    /** How many stories have been opened and so left the news page. */
    readStories: Int,
    onForgetRead: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        var tab by rememberSaveable { mutableIntStateOf(initialTab) }
        PrimaryScrollableTabRow(selectedTabIndex = tab, containerColor = Color.Transparent, edgePadding = 8.dp) {
            listOf("Skin", "Icons", "News", "Layout", "Folders").forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (tab) {
                SettingsTab.SKIN -> SkinTab(state, update, wallpaper)
                SettingsTab.ICONS -> IconsTab(state, update, iconPacks)
                SettingsTab.FEED -> FeedTab(state, update, readStories, onForgetRead)
                SettingsTab.LAYOUT -> LayoutTab(state, update, hiddenApps, homePages, onAddPage)
                else -> FoldersTab(state, update)
            }
        }
    }
}

@Composable
private fun SkinTab(state: LauncherState, update: Update, wallpaper: WallpaperActions) {
    val context = LocalContext.current
    SheetSection("Skin") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Skins.forEach { skin ->
                val selected = skin.id == state.skinId
                val shape = RoundedCornerShape(skin.radius.coerceAtMost(20.dp))
                Column(
                    // A new skin starts from its own accent and widget style.
                    Modifier.width(104.dp).clip(shape).clickable { update { it.copy(skinId = skin.id, accent = null, widgetStyle = null) } }
                        .background(skin.backdrop, shape)
                        .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else skin.onSurface.copy(alpha = 0.2f), shape)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(34.dp).background(skin.surface, shape).padding(8.dp)) {
                        Box(Modifier.size(18.dp).background(skin.accent, CircleShape))
                    }
                    Text(skin.name, color = skin.onSurface, fontFamily = skin.font, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
    SheetSection("Accent colour") {
        ColourRow(state.accent, AccentChoices) { colour -> update { it.copy(accent = colour) } }
    }
    SheetSection("Widget frame") {
        Choices(listOf<WidgetStyle?>(null) + WidgetStyle.entries, state.widgetStyle, { it?.label ?: "Skin's own" }) { style -> update { it.copy(widgetStyle = style) } }
    }
    SheetSection("Wallpaper dimming") {
        Slider(value = state.wallpaperDim, onValueChange = { dim -> update { it.copy(wallpaperDim = dim) } }, valueRange = 0f..0.9f)
    }
    SheetSection("Wallpaper") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = wallpaper.pickPhoto) { Text("Pick a photo") }
            wallpaper.fromImmich?.let { FilledTonalButton(onClick = it) { Text("From Immich") } }
            FilledTonalButton(onClick = wallpaper.systemPicker) { Text("Wallpaper apps") }
        }
        Text(
            "The cover and inner screens can each have their own picture, which shows on every skin. " +
                "Skins with a solid background, like Midnight and LCARS, cover the system wallpaper." +
                if (wallpaper.fromImmich != null) " In Immich, open a photo, tap Share and choose TT Launcher." else "",
            style = MaterialTheme.typography.bodySmall,
        )
        if (state.coverWallpaper != 0L || state.innerWallpaper != 0L) OutlinedButton(onClick = { update { it.copy(coverWallpaper = 0, innerWallpaper = 0) } }) {
            Text("Go back to the system wallpaper")
        }
    }
    FilledTonalButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } }) { Text("Set as default launcher") }
}

@Composable
private fun IconsTab(state: LauncherState, update: Update, packs: List<IconPack>) {
    SheetSection("Shape") { Choices(IconShape.entries, state.iconShape, { it.label }) { shape -> update { it.copy(iconShape = shape) } } }
    SheetSection("Style") {
        Choices(IconStyle.entries, state.iconStyle, { it.label }) { style -> update { it.copy(iconStyle = style) } }
        Text("Themed recolours every app that ships a monochrome icon to match the skin.", style = MaterialTheme.typography.bodySmall)
    }
    SheetSection("Icon pack") {
        Choices(listOf<String?>(null) + packs.map { it.packageName }, state.iconPack, { id -> packs.firstOrNull { it.packageName == id }?.label ?: "None" }) { pack -> update { it.copy(iconPack = pack) } }
        if (packs.isEmpty()) Text("No icon packs installed. Any pack made for Nova or ADW works.", style = MaterialTheme.typography.bodySmall)
    }
    SheetSection("Size") {
        Slider(value = state.iconSizeDp.toFloat(), onValueChange = { size -> update { it.copy(iconSizeDp = size.toInt()) } }, valueRange = 40f..72f, steps = 7)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Show app names", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = state.showLabels, onCheckedChange = { on -> update { it.copy(showLabels = on) } })
    }
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(NotificationWatcher.hasAccess(context)) }
    // Checked again on the way back from the system's settings.
    LifecycleResumeEffect(Unit) {
        allowed = NotificationWatcher.hasAccess(context)
        onPauseOrDispose { }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Notification badges", style = MaterialTheme.typography.bodyLarge)
            Text(
                "An unread count on each app's icon. A dock folder shows the total for the apps inside it, marks which they are, and keeps flashing until those notifications are cleared.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = state.badges, onCheckedChange = { on -> update { it.copy(badges = on) } })
    }
    if (state.badges && !allowed) {
        Text(
            "Badges need notification access, which only Android can give. TT Launcher counts notifications and reads nothing else from them.",
            style = MaterialTheme.typography.bodySmall,
        )
        FilledTonalButton(onClick = { NotificationWatcher.openAccessSettings(context) }) { Text("Allow notification access") }
    }
}

@Composable
private fun LayoutTab(state: LauncherState, update: Update, hiddenApps: List<AppEntry>, homePages: Int, onAddPage: (() -> Unit)?) {
    SheetSection("Home pages on this screen: $homePages") {
        FilledTonalButton(onClick = { onAddPage?.invoke() }, enabled = onAddPage != null) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Text("Add a home page", Modifier.padding(start = 6.dp))
        }
        Text(
            "Up to $MAX_HOME_PAGES on each screen. Hold any widget to rearrange a page, where Page adds one and Grid removes the one you're on.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    SheetSection("Open the app drawer with") {
        Choices(DrawerMode.entries, state.drawerMode, { it.label }) { mode -> update { it.copy(drawerMode = mode) } }
        Text(
            if (state.drawerMode == DrawerMode.SWIPE_UP) "Swipe up on the dock or the page dots, or past the last widget. Pull down or press back to close."
            else "The drawer is the page to the right of the home page.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Swipe between categories", style = MaterialTheme.typography.bodyLarge)
            Text("Swipe left or right in the drawer to change category.", style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = state.categorySwipe, onCheckedChange = { on -> update { it.copy(categorySwipe = on) } })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Pull down for notifications", style = MaterialTheme.typography.bodyLarge)
            Text(
                "On the home page and the apps page. The news page has no pull gestures: refresh it with its button. " +
                    "In the swipe-up drawer, pulling down closes the drawer.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = state.pullDownShade, onCheckedChange = { on -> update { it.copy(pullDownShade = on) } })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Show the dock", style = MaterialTheme.typography.bodyLarge)
            Text("Pinned apps and folders along the bottom of every home page.", style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = state.showDock, onCheckedChange = { on -> update { it.copy(showDock = on) } })
    }
    if (state.skinId == "lcars") {
        SheetSection("Tap an LCARS bar") { Choices(BarAction.entries, state.barTap, { it.label }) { action -> update { it.copy(barTap = action) } } }
        SheetSection("Hold an LCARS bar") {
            Choices(BarAction.entries, state.barHold, { it.label }) { action -> update { it.copy(barHold = action) } }
            Text("The coloured bars round each home page widget and the dock.", style = MaterialTheme.typography.bodySmall)
        }
    }
    SheetSection("Swiping between pages") {
        Choices(PageTransition.entries, state.pageTransition, { it.label }) { transition -> update { it.copy(pageTransition = transition) } }
        Text(
            if (state.pageTransition == PageTransition.TRANSPORTER) "Each page is beamed out and the next beamed in. Swipe slowly to watch it happen."
            else "How the news, home and apps pages follow one another.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (state.pageTransition == PageTransition.TRANSPORTER) Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Beam apps in and out", style = MaterialTheme.typography.bodyLarge)
            Text(
                "An app opened from here fades in through the beam, and the home screen is beamed back when you return to it. " +
                    "How an app leaves, and the switch from one app to another, are drawn by Android and can't be changed by a launcher.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = state.beamApps, onCheckedChange = { on -> update { it.copy(beamApps = on) } })
    }
    SheetSection("Category bar") { Choices(RailSide.entries, state.railSide, { it.label }) { side -> update { it.copy(railSide = side) } } }
    if (hiddenApps.isNotEmpty()) SheetSection("Hidden apps") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            hiddenApps.forEach { app ->
                InputChip(
                    selected = false, onClick = { update { it.copy(hidden = it.hidden - app.key) } }, label = { Text(app.label) },
                    trailingIcon = { Icon(Icons.Rounded.Close, "Show ${app.label} again", Modifier.size(16.dp)) },
                )
            }
        }
    }
}

@Composable
private fun FoldersTab(state: LauncherState, update: Update) {
    fun change(id: String, change: (Folder) -> Folder) = update { s -> s.copy(folders = s.folders.map { if (it.id == id) change(it) else it }) }
    fun move(index: Int, by: Int) = update { s ->
        val list = s.folders.toMutableList()
        if (index + by in list.indices) list.add(index + by, list.removeAt(index))
        s.copy(folders = list)
    }
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    Text(
        "Each folder is a page of the app drawer. Apps are sorted in by topic, or by words you add. " +
            "A topic belongs to one folder at a time, so giving it to a folder takes it from the one that had it.",
        style = MaterialTheme.typography.bodySmall,
    )
    state.folders.forEachIndexed { index, folder ->
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable { open = if (open == folder.id) null else folder.id }, verticalAlignment = Alignment.CenterVertically) {
                Icon(folderIcon(folder.icon), null, Modifier.padding(end = 12.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(folder.name.ifBlank { "Unnamed" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val collects = AppCategory.entries.filter { it.name in folder.topics }.map { it.label } + folder.keywords.map { "\"$it\"" }
                    Text(
                        if (collects.isEmpty()) "Only apps you move here" else collects.joinToString(),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { move(index, -1) }, enabled = index > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move ${folder.name} up") }
                IconButton(onClick = { move(index, 1) }, enabled = index < state.folders.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Move ${folder.name} down") }
            }
            if (open == folder.id) {
                OutlinedTextField(
                    value = folder.name, onValueChange = { name -> change(folder.id) { it.copy(name = name.take(24)) } },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FolderIcons.forEach { (key, icon) ->
                        val selected = key == folder.icon
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable(onClickLabel = "Use the $key icon") { change(folder.id) { it.copy(icon = key) } },
                            contentAlignment = Alignment.Center,
                        ) { Icon(icon, key, tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                    }
                }
                Text("Sorts in automatically", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppCategory.entries.forEach { topic ->
                        val mine = topic.name in folder.topics
                        FilterChip(
                            selected = mine, label = { Text(topic.label) },
                            onClick = {
                                update { s ->
                                    s.copy(folders = s.folders.map { other ->
                                        when {
                                            other.id == folder.id -> other.copy(topics = if (mine) other.topics - topic.name else other.topics + topic.name)
                                            else -> other.copy(topics = other.topics - topic.name)
                                        }
                                    })
                                }
                            },
                        )
                    }
                }
                if (folder.keywords.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    folder.keywords.forEach { word ->
                        InputChip(
                            selected = true, onClick = { change(folder.id) { it.copy(keywords = it.keywords - word) } }, label = { Text(word) },
                            trailingIcon = { Icon(Icons.Rounded.Close, "Remove $word", Modifier.size(16.dp)) },
                        )
                    }
                }
                AddField("Also apps with this word in their name") { input ->
                    val word = input.trim().lowercase()
                    change(folder.id) { if (word in it.keywords) it else it.copy(keywords = it.keywords + word) }
                    true
                }
                OutlinedButton(onClick = {
                    open = null
                    // Its apps go back to the auto-sorter.
                    update { s -> s.copy(folders = s.folders.filter { it.id != folder.id }, categoryOverrides = s.categoryOverrides.filterValues { it != folder.id }) }
                }) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete folder")
                }
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = {
            val id = "f" + System.currentTimeMillis()
            update { it.copy(folders = it.folders + Folder(id, "New folder", "folder")) }
            open = id
        }) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add folder")
        }
        OutlinedButton(onClick = { open = null; update { it.copy(folders = DefaultFolders) } }) { Text("Reset to defaults") }
    }
}

@Composable
private fun AddField(label: String, keyboard: KeyboardType = KeyboardType.Text, onAdd: (String) -> Boolean) {
    var text by rememberSaveable(label) { mutableStateOf("") }
    val submit = { if (text.isNotBlank() && onAdd(text)) text = "" }
    OutlinedTextField(
        value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        trailingIcon = { IconButton(onClick = submit) { Icon(Icons.Rounded.Add, "Add") } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
    )
}

@Composable
private fun FeedTab(state: LauncherState, update: Update, readStories: Int, onForgetRead: () -> Unit) {
    SheetSection("Muted topics") {
        Text("Stories that mention any of these are hidden.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TopicPacks.forEach { pack ->
                val on = pack.id in state.mutedPacks
                FilterChip(selected = on, onClick = { update { it.copy(mutedPacks = if (on) it.mutedPacks - pack.id else it.mutedPacks + pack.id) } }, label = { Text(pack.label) })
            }
        }
        if (state.mutedTopics.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.mutedTopics.forEach { topic ->
                InputChip(
                    selected = true, onClick = { update { it.copy(mutedTopics = it.mutedTopics - topic) } }, label = { Text(topic) },
                    trailingIcon = { Icon(Icons.Rounded.Close, "Stop hiding $topic", Modifier.size(16.dp)) },
                )
            }
        }
        AddField("Hide a word or phrase") { input ->
            val topic = input.trim().lowercase()
            update { if (topic in it.mutedTopics) it else it.copy(mutedTopics = it.mutedTopics + topic) }
            true
        }
    }
    SheetSection("Feeds") {
        state.feeds.forEach { feed ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (feed.title.isNotEmpty()) Text(feed.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(feed.url, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { update { it.copy(feeds = it.feeds - feed) } }) { Icon(Icons.Rounded.Delete, "Remove ${feed.title.ifEmpty { feed.url }}") }
            }
        }
        var invalid by remember { mutableStateOf(false) }
        AddField("Add an RSS or Atom feed URL", KeyboardType.Uri) { input ->
            val url = normalizeFeedUrl(input)
            invalid = url == null
            if (url != null) update { s -> if (s.feeds.any { it.url == url }) s else s.copy(feeds = s.feeds + FeedSource(url)) }
            url != null
        }
        if (invalid) Text("That doesn't look like a web address.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Show pictures", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = state.feedImages, onCheckedChange = { on -> update { it.copy(feedImages = on) } })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Hide stories I've read", style = MaterialTheme.typography.bodyLarge)
            Text("A story leaves the news page once you've opened it.", style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = state.hideRead, onCheckedChange = { on -> update { it.copy(hideRead = on) } })
    }
    if (readStories > 0) OutlinedButton(onClick = onForgetRead) { Text("Show read stories again") }
}

@Composable
fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    )
}

@Composable
fun AppMenuSheet(app: AppEntry, state: LauncherState, update: Update, onInfo: () -> Unit, onUninstall: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppIcon(app, 48.dp)
                Text(app.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            val pinned = app.key in state.dock
            SheetAction(Icons.Rounded.PushPin, if (pinned) "Remove from dock" else "Pin to dock") {
                // An app is in the dock once: pinning it on its own takes it out of any dock folder.
                update { s ->
                    if (pinned) s.copy(dock = s.dock - app.key)
                    else s.copy(dock = s.dock + app.key, dockFolders = s.dockFolders.map { it.copy(apps = it.apps - app.key) })
                }
                onDismiss()
            }
            // Dock folders this app can go into, or come out of.
            FlowRow(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.dockFolders.forEach { folder ->
                    val inside = app.key in folder.apps
                    FilterChip(
                        selected = inside, label = { Text(folder.name) }, leadingIcon = { Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp)) },
                        onClick = {
                            update { s ->
                                if (inside) s.copy(dockFolders = s.dockFolders.map { if (it.id == folder.id) it.copy(apps = it.apps - app.key) else it })
                                // Going into a folder takes it off the dock itself, and out of any other folder.
                                else s.copy(
                                    dock = s.dock - app.key,
                                    dockFolders = s.dockFolders.map { if (it.id == folder.id) it.copy(apps = it.apps + app.key) else it.copy(apps = it.apps - app.key) },
                                )
                            }
                            onDismiss()
                        },
                    )
                }
                FilterChip(
                    selected = false, label = { Text("New dock folder") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) },
                    onClick = {
                        val id = "d" + System.currentTimeMillis()
                        update { s ->
                            // The folder takes over the app's own place in the dock, if it had one.
                            val entry = DOCK_FOLDER_PREFIX + id
                            s.copy(
                                dock = if (app.key in s.dock) s.dock.map { if (it == app.key) entry else it } else s.dock + entry,
                                dockFolders = s.dockFolders.map { it.copy(apps = it.apps - app.key) } + DockFolder(id, "Folder", listOf(app.key)),
                            )
                        }
                        onDismiss()
                    },
                )
            }
            SheetAction(Icons.Rounded.Info, "App info") { onInfo(); onDismiss() }
            SheetAction(Icons.Rounded.VisibilityOff, "Hide from the launcher") {
                update { it.copy(hidden = it.hidden + app.key, dock = it.dock - app.key) }
                onDismiss()
            }
            SheetAction(Icons.Rounded.Delete, "Uninstall") { onUninstall(); onDismiss() }
            Text("Move to", Modifier.padding(top = 12.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.folders.forEach { folder ->
                    FilterChip(
                        selected = folder.id == app.folder,
                        onClick = { update { it.copy(categoryOverrides = it.categoryOverrides + (app.key to folder.id)) }; onDismiss() },
                        label = { Text(folder.name) },
                        leadingIcon = { Icon(folderIcon(folder.icon), null, Modifier.size(18.dp)) },
                    )
                }
                // Hands the app back to the auto-sorter.
                if (app.key in state.categoryOverrides) FilterChip(
                    selected = false,
                    onClick = { update { it.copy(categoryOverrides = it.categoryOverrides - app.key) }; onDismiss() },
                    label = { Text("Sort automatically") },
                )
            }
        }
    }
}

/** Opened by holding a folder in the dock. */
@Composable
fun DockFolderSheet(folder: DockFolder, apps: List<AppEntry>, update: Update, onDismiss: () -> Unit) {
    fun change(change: (DockFolder) -> DockFolder) = update { s -> s.copy(dockFolders = s.dockFolders.map { if (it.id == folder.id) change(it) else it }) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = folder.name, onValueChange = { name -> change { it.copy(name = name.take(24)) } },
                modifier = Modifier.fillMaxWidth(), label = { Text("Folder name") }, singleLine = true,
            )
            if (apps.isEmpty()) Text("Empty. Hold any app and choose this folder to add it.", style = MaterialTheme.typography.bodyMedium)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                apps.forEach { app ->
                    InputChip(
                        selected = false, onClick = { change { it.copy(apps = it.apps - app.key) } }, label = { Text(app.label) },
                        avatar = { AppIcon(app, 24.dp) },
                        trailingIcon = { Icon(Icons.Rounded.Close, "Take ${app.label} out", Modifier.size(16.dp)) },
                    )
                }
            }
            OutlinedButton(onClick = {
                update { s -> s.copy(dockFolders = s.dockFolders.filter { it.id != folder.id }, dock = s.dock - (DOCK_FOLDER_PREFIX + folder.id)) }
                onDismiss()
            }) {
                Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Remove folder from dock")
            }
        }
    }
}

@Composable
fun ArticleMenuSheet(article: Article, state: LauncherState, update: Update, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Hide stories like this", style = MaterialTheme.typography.titleLarge)
            Text(article.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // The feed's own tags for this story are the likeliest things to mute.
            val tags = remember(article) { article.categories.map { it.lowercase() }.distinct().filter { it.length in 2..40 }.take(12) }
            if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    val muted = tag in state.mutedTopics
                    FilterChip(selected = muted, onClick = { update { it.copy(mutedTopics = if (muted) it.mutedTopics - tag else it.mutedTopics + tag) } }, label = { Text(tag) })
                }
            }
            AddField("Hide a word or phrase") { input ->
                val topic = input.trim().lowercase()
                update { if (topic in it.mutedTopics) it else it.copy(mutedTopics = it.mutedTopics + topic) }
                true
            }
        }
    }
}
