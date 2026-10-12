@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.ttlauncher.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.ttlauncher.apps.AppEntry
import app.ttlauncher.data.BarAction
import app.ttlauncher.data.MacroButton
import app.ttlauncher.data.ScreenLayout
import app.ttlauncher.data.WidgetConfig
import app.ttlauncher.data.WidgetFont
import app.ttlauncher.data.WidgetStyle
import app.ttlauncher.data.WidgetType
import app.ttlauncher.data.MAX_HOME_PAGES
import app.ttlauncher.data.columnChoices
import app.ttlauncher.data.limits
import app.ttlauncher.weather.PlaceMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

val ColourChoices = AccentChoices + listOf(0xFFFFFFFF, 0xFF111111)

/** A row of swatches, with "the skin's own" first. Null is the skin's own. */
@Composable
fun ColourRow(selected: Long?, choices: List<Long> = ColourChoices, onSelect: (Long?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Skin's own") })
        choices.forEach { argb ->
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color(argb))
                    .border(3.dp, if (selected == argb) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable(onClickLabel = "Use this colour") { onSelect(argb) },
            )
        }
    }
}

@Composable
fun SheetSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** What holding an empty part of the home page offers. */
@Composable
fun HomeMenuSheet(wallpaper: WallpaperActions, onAddWidget: () -> Unit, onEdit: () -> Unit, onAddPage: (() -> Unit)?, onSettings: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp).navigationBarsPadding()) {
            SheetAction(Icons.Rounded.Add, "Add a widget", onAddWidget)
            SheetAction(Icons.Rounded.Edit, "Rearrange this page", onEdit)
            // Null once this screen has as many pages as it may.
            onAddPage?.let { SheetAction(Icons.Rounded.AddToHomeScreen, "Add another home page", it) }
            SheetAction(Icons.Rounded.Wallpaper, "Wallpaper from a photo") { onDismiss(); wallpaper.pickPhoto() }
            wallpaper.fromImmich?.let { SheetAction(Icons.Rounded.PhotoLibrary, "Wallpaper from Immich") { onDismiss(); it() } }
            SheetAction(Icons.Rounded.Settings, "Launcher settings", onSettings)
        }
    }
}

/** The picker: every built-in widget drawn live in the current skin, then the widgets other apps offer. */
@Composable
fun AddWidgetSheet(host: WidgetHostController, onAdd: (WidgetConfig) -> Unit, onAddFromApp: (AppWidgetProviderInfo) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val fromApps = remember {
        host.providers().map { it to it.loadLabel(context.packageManager) }
            .groupBy { runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(it.first.provider.packageName, 0)).toString() }.getOrDefault(it.first.provider.packageName) }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Add a widget", style = MaterialTheme.typography.titleLarge)
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WidgetType.entries.filter { it != WidgetType.SYSTEM }.forEach { type ->
                        type.variants.forEachIndexed { variant, name ->
                            val sample = WidgetConfig("preview", type, variant)
                            val limits = sample.limits()
                            Column(
                                Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "Add ${type.label}") { onAdd(WidgetConfig("w" + System.currentTimeMillis(), type, variant)) }.padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                // Drawn at a size the widget can really be, so the preview is honest.
                                WidgetItem(
                                    // Narrow ones are trimmed a little so two sit side by side on the cover screen.
                                    sample, Modifier.size(if (limits.minWidth <= 150f) 136.dp else limits.minWidth.dp, limits.minHeight.coerceIn(64f, 190f).dp), host = null,
                                )
                                Text(if (type.variants.size > 1) "${type.label} · $name" else type.label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            if (fromApps.isNotEmpty()) item {
                Text("From your apps", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            fromApps.forEach { (app, widgets) ->
                item(key = "app:$app") { Text(app, style = MaterialTheme.typography.titleSmall) }
                items(widgets, key = { it.first.provider.flattenToShortString() }) { (provider, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onAddFromApp(provider) }
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ProviderPreview(provider)
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderPreview(provider: AppWidgetProviderInfo) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.densityDpi
    val image by produceState<ImageBitmap?>(null, provider) {
        value = withContext(Dispatchers.IO) {
            runCatching { (provider.loadPreviewImage(context, density) ?: provider.loadIcon(context, density))?.toBitmap()?.asImageBitmap() }.getOrNull()
        }
    }
    Box(Modifier.size(96.dp, 64.dp), contentAlignment = Alignment.Center) {
        image?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.size(96.dp, 64.dp)) }
    }
}

/** One widget's own settings: how it looks and what it shows. Nothing here affects any other widget. */
@Composable
fun WidgetOptionsSheet(
    widget: WidgetConfig,
    name: String,
    pageCount: Int,
    currentPage: Int,
    /** For the macro widget, whose buttons can open any of them. */
    apps: List<AppEntry>,
    onChange: ((WidgetConfig) -> WidgetConfig) -> Unit,
    onMoveToPage: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val type = widget.type
            if (type.variants.size > 1) SheetSection("Look") {
                // A different look has different size limits, so the size goes back to that look's own.
                Choices(type.variants.indices.toList(), widget.variant, { type.variants[it] }) { v -> onChange { it.copy(variant = v, size = null) } }
            }
            SheetSection("Frame") {
                Choices(listOf<WidgetStyle?>(null) + WidgetStyle.entries, widget.style, { it?.label ?: "Skin's own" }) { style -> onChange { it.copy(style = style) } }
            }
            if (type != WidgetType.SYSTEM) {
                SheetSection("Font") {
                    Choices(listOf<WidgetFont?>(null) + WidgetFont.entries, widget.font, { it?.label ?: "Skin's own" }) { font -> onChange { it.copy(font = font) } }
                    SwitchRow("Bold", widget.bold) { on -> onChange { it.copy(bold = on) } }
                }
                SheetSection("Text colour") { ColourRow(widget.textColor) { colour -> onChange { it.copy(textColor = colour) } } }
                SheetSection("Accent colour") { ColourRow(widget.accentColor) { colour -> onChange { it.copy(accentColor = colour) } } }
            }
            if (type.options.isNotEmpty()) SheetSection("Shows") {
                val phone24 = DateFormat.is24HourFormat(LocalContext.current)
                type.options.forEach { option ->
                    // Options that follow the phone show what the phone is set to until changed.
                    val phone = when (option.key) {
                        "h24" -> phone24
                        "fahrenheit", "mph" -> weatherDefault(option.key)
                        "miles" -> weatherDefault("mph")
                        else -> WeekFields.of(Locale.getDefault()).firstDayOfWeek == DayOfWeek.MONDAY
                    }
                    SwitchRow(option.label, widget.option(option.key) ?: phone) { on -> onChange { it.copy(options = it.options + (option.key to on)) } }
                }
            }
            if (type == WidgetType.WEATHER) WeatherPlace(widget, onChange)
            if (type == WidgetType.HEALTH) HealthAccessSection()
            if (type == WidgetType.MACRO) MacroButtons(widget, apps, onChange)
            if (type == WidgetType.SEARCH) OutlinedTextField(
                value = widget.text, onValueChange = { text -> onChange { it.copy(text = text.take(40)) } },
                modifier = Modifier.fillMaxWidth(), label = { Text("Hint text") }, placeholder = { Text("Search apps") }, singleLine = true,
            )
            if (pageCount > 1) SheetSection("Page") {
                Choices((0 until pageCount).toList(), currentPage, { "Page ${it + 1}" }) { page -> if (page != currentPage) onMoveToPage(page) }
            }
            Text("To resize, drag the handle on the widget's corner. To move it, drag the widget.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** What the health widget may read: asked for here when there is more to allow, and otherwise managed in Health Connect. */
@Composable
private fun HealthAccessSection() {
    val health = LocalHealth.current ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val partial by health.partial.collectAsStateWithLifecycle()
    val ask = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { scope.launch { health.refresh(force = true) } }
    SheetSection("Health data") {
        Text(
            "Steps, heart rate, sleep, calories, distance, floors, workouts, blood oxygen, water and weight, from Health Connect. " +
                "The widget shows whichever of them it is allowed to read and your health app records. A larger widget shows more of them.",
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (partial) FilledTonalButton(onClick = { runCatching { ask.launch(health.permissions) } }) { Text("Allow more readings") }
            OutlinedButton(onClick = { runCatching { context.startActivity(health.manageIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Text("Open Health Connect") }
        }
    }
}

/** Where the weather widget reports on: the place it has, and a search by name for another. */
@Composable
private fun WeatherPlace(widget: WidgetConfig, onChange: ((WidgetConfig) -> WidgetConfig) -> Unit) {
    val weather = LocalWeather.current ?: return
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf(emptyList<PlaceMatch>()) }
    var note by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    fun find() {
        if (searching || query.isBlank()) return
        searching = true
        scope.launch {
            val found = runCatching { weather.search(query) }
            matches = found.getOrDefault(emptyList())
            note = if (found.isFailure) "Couldn't search just now. Check the connection and try again." else if (matches.isEmpty()) "Nowhere found by that name." else null
            searching = false
        }
    }
    SheetSection("Place") {
        Text(widget.place?.let { "Showing ${it.name}." } ?: "None chosen yet.", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it.take(60) }, modifier = Modifier.weight(1f), singleLine = true,
                label = { Text("Town or city") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { find() }),
            )
            OutlinedButton(enabled = query.isNotBlank() && !searching, onClick = ::find) { Text(if (searching) "Finding…" else "Find") }
        }
        matches.forEach { match ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                    onChange { it.copy(place = match.place) }
                    matches = emptyList()
                    query = ""
                }.padding(vertical = 8.dp, horizontal = 4.dp),
            ) {
                Text(match.place.name, style = MaterialTheme.typography.bodyLarge)
                if (match.region.isNotEmpty()) Text(match.region, style = MaterialTheme.typography.bodySmall)
            }
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text("Weather comes from Open-Meteo. The place chosen here is all that is sent to it, and only while the widget is on screen.", style = MaterialTheme.typography.bodySmall)
    }
}

/** The macro widget's buttons: what each is called and does, and three ways to add another. */
@Composable
private fun MacroButtons(widget: WidgetConfig, apps: List<AppEntry>, onChange: ((WidgetConfig) -> WidgetConfig) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    fun add(label: String, app: String? = null, link: String = "", action: BarAction = BarAction.NONE) =
        onChange { it.copy(buttons = it.buttons + MacroButton("b" + System.currentTimeMillis(), label.take(18), app, link, action)) }
    SheetSection("Buttons") {
        if (widget.buttons.isEmpty()) Text("None yet. Add one below.", style = MaterialTheme.typography.bodySmall)
        widget.buttons.forEach { button ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = button.label, singleLine = true, modifier = Modifier.weight(1f),
                    onValueChange = { text -> onChange { w -> w.copy(buttons = w.buttons.map { if (it.id == button.id) it.copy(label = text.take(18)) else it }) } },
                    supportingText = {
                        Text(
                            when {
                                button.app != null -> "Opens " + (apps.firstOrNull { it.key == button.app }?.label ?: "an app that is no longer installed")
                                button.link.isNotBlank() -> "Opens " + button.link
                                else -> button.action.label
                            },
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                IconButton(onClick = { onChange { w -> w.copy(buttons = w.buttons.filter { it.id != button.id }) } }) { Icon(Icons.Rounded.Delete, "Remove ${button.label}") }
            }
        }
    }
    SheetSection("Add a button that") {
        OutlinedButton(onClick = { picking = true }) { Text("Opens an app…") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BarAction.entries.filter { it != BarAction.NONE }.forEach { action ->
                FilterChip(selected = false, onClick = { add(action.label, action = action) }, label = { Text(action.label) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = link, onValueChange = { link = it.trim() }, modifier = Modifier.weight(1f), singleLine = true,
                label = { Text("Opens a link") }, placeholder = { Text("https://…") },
            )
            OutlinedButton(enabled = link.contains(':'), onClick = { add("Link", link = link); link = "" }) { Text("Add") }
        }
        Text("A link can be a web page or any address another app answers to, such as a shortcut or automation app's.", style = MaterialTheme.typography.bodySmall)
    }
    if (picking) {
        var query by remember { mutableStateOf("") }
        val shown = remember(apps, query) { apps.filter { it.label.contains(query.trim(), ignoreCase = true) }.sortedBy { it.label.lowercase() } }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Choose an app") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search") })
                    LazyColumn(Modifier.height(340.dp)) {
                        items(shown, key = { it.key }) { app ->
                            Row(
                                Modifier.fillMaxWidth().clickable { add(app.label, app = app.key); picking = false }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AppIcon(app, 36.dp)
                                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}

/** The grid and pages of the screen being looked at. The other screen keeps its own. */
@Composable
fun GridSheet(
    layout: ScreenLayout,
    inner: Boolean,
    currentPage: Int,
    onChange: ((ScreenLayout) -> ScreenLayout) -> Unit,
    onAddPage: () -> Unit,
    onRemovePage: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column {
                Text(if (inner) "Inner screen grid" else "Cover screen grid", style = MaterialTheme.typography.titleLarge)
                Text(
                    "These apply to this screen only. " + (if (inner) "Fold" else "Unfold") + " the phone to set up the other one.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val columns = columnChoices(inner)
            SheetSection("Columns: ${layout.columns}") {
                Slider(
                    value = layout.columns.toFloat(), onValueChange = { v -> onChange { it.copy(columns = v.toInt()) } },
                    valueRange = columns.first.toFloat()..columns.last.toFloat(), steps = columns.last - columns.first - 1,
                )
            }
            SheetSection("Row height") {
                Slider(value = layout.rowHeightDp.toFloat(), onValueChange = { v -> onChange { it.copy(rowHeightDp = v.toInt()) } }, valueRange = 48f..120f)
                Text("Widgets keep to the sizes their content needs, so some will take more cells on a finer grid.", style = MaterialTheme.typography.bodySmall)
            }
            SheetSection("Pages: ${layout.pages.size} of up to $MAX_HOME_PAGES") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onAddPage, enabled = layout.pages.size < MAX_HOME_PAGES) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Text("Add a page", Modifier.padding(start = 6.dp))
                    }
                    val widgetsHere = layout.pages.getOrNull(currentPage)?.widgets?.size ?: 0
                    OutlinedButton(onClick = onRemovePage, enabled = layout.pages.size > 1) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                        Text(if (widgetsHere == 0) "Remove this page" else "Remove this page and its $widgetsHere widgets", Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}
