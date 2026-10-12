package app.ttlauncher.data

import kotlinx.serialization.Serializable

@Serializable
enum class WidgetStyle(val label: String) { GLASS("Glass"), SOLID("Solid"), OUTLINE("Outline"), BARE("Bare") }

/** A switch on what a widget shows. A null default means "whatever the phone is set to". */
class WidgetOption(val key: String, val label: String, val default: Boolean?)

@Serializable
enum class WidgetType(val label: String, val variants: List<String>, val options: List<WidgetOption> = emptyList()) {
    CLOCK(
        "Clock", listOf("Digital", "Stacked", "Analog"),
        listOf(WidgetOption("date", "Show the date", false), WidgetOption("h24", "24-hour clock", null), WidgetOption("ampm", "Show AM/PM", true)),
    ),
    DATE("Date", listOf("Line", "Card"), listOf(WidgetOption("year", "Show the year", false), WidgetOption("short", "Short names", false))),
    BATTERY("Battery", listOf("Bar", "Ring"), listOf(WidgetOption("status", "Show charging status", true))),
    CALENDAR("Month", listOf("Grid"), listOf(WidgetOption("monday", "Start the week on Monday", null), WidgetOption("title", "Show the month name", true))),
    SEARCH("Search", listOf("Pill")),
    SYSTEM("Android widget", listOf("Default")),
    CASCADE("Data cascade", listOf("Figures"), listOf(WidgetOption("animate", "Animate", true))),
    MACRO("Macro buttons", listOf("Pad")),
    WEATHER(
        "Weather", listOf("Readout"),
        listOf(WidgetOption("forecast", "Show the days ahead", true), WidgetOption("fahrenheit", "Fahrenheit", null), WidgetOption("mph", "Wind in mph", null)),
    ),
    HEALTH(
        "Health", listOf("Readout"),
        listOf(WidgetOption("trace", "Show the heart trace", true), WidgetOption("animate", "Animate the trace", true), WidgetOption("miles", "Distance in miles", null)),
    ),
    MEDIA("Spotify", listOf("Player"), listOf(WidgetOption("art", "Show the cover", true), WidgetOption("any", "Follow other players too", false))),
}

@Serializable
enum class WidgetFont(val label: String) { SANS("Sans"), SERIF("Serif"), MONO("Mono"), CURSIVE("Script") }

@Serializable
data class WidgetConfig(
    val id: String,
    val type: WidgetType,
    val variant: Int = 0,
    /** Null follows the skin, as do the font and both colours. */
    val style: WidgetStyle? = null,
    val font: WidgetFont? = null,
    val bold: Boolean = false,
    /** ARGB. */
    val textColor: Long? = null,
    val accentColor: Long? = null,
    /** [WidgetOption.key] to its setting, for the ones changed from their default. */
    val options: Map<String, Boolean> = emptyMap(),
    /** The search widget's hint. */
    val text: String = "",
    val appWidgetId: Int = -1,
    /** The macro widget's buttons, in the order shown. */
    val buttons: List<MacroButton> = emptyList(),
    /** In grid cells. Null until the user resizes it, which leaves the size to the widget's own preference. */
    val size: CellSize? = null,
    /** Where the weather widget reports on. Null until one is chosen. */
    val place: Place? = null,
) {
    /** Null when the option follows the phone and hasn't been set. */
    fun option(key: String): Boolean? = options[key] ?: type.options.firstOrNull { it.key == key }?.default
}

/** One button of a macro widget. It opens [app] if set, else [link] if set, else does [action]. */
@Serializable
data class MacroButton(val id: String, val label: String, val app: String? = null, val link: String = "", val action: BarAction = BarAction.NONE)

/** Somewhere a weather widget reports on. */
@Serializable
data class Place(val name: String, val latitude: Double, val longitude: Double)

@Serializable
data class CellSize(val cols: Int, val rows: Int)

val DefaultWidgets = listOf(
    WidgetConfig("clock", WidgetType.CLOCK),
    WidgetConfig("date", WidgetType.DATE, variant = 1),
    WidgetConfig("search", WidgetType.SEARCH),
    WidgetConfig("battery", WidgetType.BATTERY),
    WidgetConfig("month", WidgetType.CALENDAR),
)

/** One swipeable home page, with its widgets in the order they're laid out. */
@Serializable
data class WidgetPage(val id: String, val widgets: List<WidgetConfig> = emptyList())

/**
 * The home pages of one screen and the grid they share. The cover and inner screens each have
 * their own, so neither is a compromise for the other.
 */
@Serializable
data class ScreenLayout(
    val columns: Int,
    val rowHeightDp: Int = 72,
    val pages: List<WidgetPage> = listOf(WidgetPage("home", DefaultWidgets)),
)

@Serializable
enum class IconShape(val label: String) { CIRCLE("Circle"), SQUIRCLE("Squircle"), ROUNDED("Rounded"), TEARDROP("Teardrop") }

@Serializable
enum class IconStyle(val label: String) { ORIGINAL("Original"), THEMED("Themed") }

@Serializable
enum class DrawerMode(val label: String) { SWIPE_UP("Swipe up from home"), PAGE("Page beside home") }

/** What pressing a bar of an LCARS frame on the home page can do. */
@Serializable
enum class BarAction(val label: String) {
    NONE("Nothing"), DRAWER("Open apps"), SEARCH("Search apps"), NOTIFICATIONS("Notifications"),
    NEWS("News page"), EDIT("Rearrange widgets"), SETTINGS("Launcher settings"),
}

/** What a swipe between pages looks like. */
@Serializable
enum class PageTransition(val label: String) { SLIDE("Slide"), FADE("Fade"), ZOOM("Zoom"), CUBE("Cube"), TRANSPORTER("Transporter") }

@Serializable
enum class RailSide(val label: String) { LEFT("Left"), RIGHT("Right") }

/** A stack in the dock that fans its apps out when tapped. Listed in [LauncherState.dock] as "folder:" plus its id. */
@Serializable
data class DockFolder(val id: String, val name: String, val apps: List<String> = emptyList())

const val DOCK_FOLDER_PREFIX = "folder:"

/** The list with [item] taken out and put back where [target] was, the ones between shuffling along. */
fun <T> List<T>.moved(item: T, target: T): List<T> {
    val from = indexOf(item)
    val to = indexOf(target)
    if (from < 0 || to < 0 || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** A page of the app drawer. Apps land in it by topic, by word, or because the user put them there. */
@Serializable
data class Folder(
    val id: String,
    val name: String,
    /** A key of the icon picker in the folder editor. */
    val icon: String,
    /** Names of the auto-sorter's topics ([app.ttlauncher.apps.AppCategory]) this folder collects. */
    val topics: Set<String> = emptySet(),
    /** Words in an app's name or package that also send it here, ahead of its topic. */
    val keywords: List<String> = emptyList(),
)

val DefaultFolders = listOf(
    Folder("COMMUNICATION", "Chat", "forum", setOf("COMMUNICATION")),
    Folder("INTERNET", "Web", "public", setOf("INTERNET")),
    Folder("MEDIA", "Media", "play", setOf("AUDIO", "VIDEO", "READING")),
    Folder("GAMES", "Games", "games", setOf("GAMES")),
    Folder("PHOTOS", "Photos", "camera", setOf("PHOTOS")),
    Folder("WORK", "Work", "work", setOf("WORK")),
    Folder("SECURITY", "Security", "lock", setOf("SECURITY")),
    Folder("MONEY", "Money", "money", setOf("MONEY")),
    Folder("SHOPPING", "Shopping", "bag", setOf("SHOPPING")),
    Folder("FOOD", "Food", "food", setOf("FOOD")),
    Folder("TRAVEL", "Travel", "flight", setOf("TRAVEL")),
    Folder("HEALTH", "Health", "fitness", setOf("HEALTH")),
    Folder("SMART_HOME", "Home", "home", setOf("SMART_HOME")),
    Folder("EDUCATION", "Learning", "school", setOf("EDUCATION")),
    Folder("TOOLS", "Tools", "build", setOf("TOOLS")),
    Folder("SYSTEM", "System", "tune", setOf("SYSTEM")),
)

/** The topics the sorter had before [LauncherState.seenTopics] started keeping count. */
val FirstTopics = setOf(
    "COMMUNICATION", "INTERNET", "AUDIO", "VIDEO", "READING", "GAMES", "PHOTOS", "WORK", "SECURITY", "MONEY", "TRAVEL", "TOOLS", "SYSTEM",
)

/** Whole-word, any-case match for any of [words], singular or plural. Null when there is nothing to match. */
fun wordsRegex(words: Collection<String>): Regex? = words.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
    .takeIf { it.isNotEmpty() }
    ?.joinToString("|", "(?<![\\p{L}\\p{N}])(?:", ")s?(?![\\p{L}\\p{N}])") { Regex.escape(it) }
    ?.toRegex(RegexOption.IGNORE_CASE)

@Serializable
data class FeedSource(val url: String, val title: String = "")

@Serializable
data class Article(
    val title: String,
    val link: String,
    val summary: String = "",
    val image: String? = null,
    val source: String = "",
    val feedUrl: String = "",
    /** Epoch millis, or 0 when the feed gave no usable date. */
    val time: Long = 0,
    val categories: List<String> = emptyList(),
)

@Serializable
data class LauncherState(
    val skinId: String = "aurora",
    /** ARGB override of the skin's accent. */
    val accent: Long? = null,
    val widgetStyle: WidgetStyle? = null,
    val wallpaperDim: Float = 0.3f,
    /** When each screen's own wallpaper was last set, or 0 while it shows the system wallpaper. */
    val coverWallpaper: Long = 0,
    val innerWallpaper: Long = 0,
    val iconShape: IconShape = IconShape.SQUIRCLE,
    val iconStyle: IconStyle = IconStyle.ORIGINAL,
    val iconPack: String? = null,
    val iconSizeDp: Int = 54,
    val showLabels: Boolean = true,
    /** Unread counts on app icons and dock folders. They also need notification access, which Android grants. */
    val badges: Boolean = true,
    val drawerMode: DrawerMode = DrawerMode.SWIPE_UP,
    /** Swiping sideways in the app drawer moves between categories. */
    val categorySwipe: Boolean = true,
    val railSide: RailSide = RailSide.RIGHT,
    val pageTransition: PageTransition = PageTransition.SLIDE,
    /** With the transporter transition, apps are beamed in when opened and the home screen beamed back on return. */
    val beamApps: Boolean = true,
    /** Pulling down on a launcher screen opens the notification shade. */
    val pullDownShade: Boolean = true,
    val showDock: Boolean = true,
    val barTap: BarAction = BarAction.DRAWER,
    val barHold: BarAction = BarAction.EDIT,
    val coverLayout: ScreenLayout = ScreenLayout(columns = 4),
    val innerLayout: ScreenLayout = ScreenLayout(columns = 8),
    /** App keys and dock folders, in the order the dock shows them. */
    val dock: List<String> = emptyList(),
    val dockFolders: List<DockFolder> = emptyList(),
    val folders: List<Folder> = DefaultFolders,
    /** The sorter's topics this setup has been offered, so one added later gets its folder exactly once. */
    val seenTopics: Set<String> = FirstTopics,
    /** App key to [Folder.id], for apps the user filed by hand. */
    val categoryOverrides: Map<String, String> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    val feeds: List<FeedSource> = listOf(
        FeedSource("https://feeds.bbci.co.uk/news/rss.xml", "BBC News"),
        FeedSource("https://www.theguardian.com/uk/rss", "The Guardian"),
        FeedSource("https://feeds.arstechnica.com/arstechnica/index", "Ars Technica"),
    ),
    val mutedTopics: List<String> = emptyList(),
    val mutedPacks: Set<String> = emptySet(),
    val feedImages: Boolean = true,
    /** A story leaves the news page once it has been opened. */
    val hideRead: Boolean = true,
    /** Set by "Don't ask again" on the offer to become the default home app. */
    val skipDefaultPrompt: Boolean = false,
)
