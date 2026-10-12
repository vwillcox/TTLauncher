package app.ttlauncher

import app.ttlauncher.apps.AppCategory
import app.ttlauncher.apps.AutoMono
import app.ttlauncher.apps.Categorizer
import app.ttlauncher.apps.FolderSorter
import app.ttlauncher.apps.Note
import app.ttlauncher.apps.badgeCounts
import app.ttlauncher.apps.OTHER_FOLDER
import app.ttlauncher.data.DefaultFolders
import app.ttlauncher.data.FirstTopics
import app.ttlauncher.data.LauncherState
import app.ttlauncher.apps.withNewTopics
import app.ttlauncher.data.Folder
import app.ttlauncher.data.Article
import app.ttlauncher.data.CellSize
import app.ttlauncher.data.GridSpec
import app.ttlauncher.data.ScreenLayout
import app.ttlauncher.data.WidgetConfig
import app.ttlauncher.data.WidgetPage
import app.ttlauncher.data.WidgetType
import app.ttlauncher.data.limits
import app.ttlauncher.data.moveWidget
import app.ttlauncher.data.moved
import app.ttlauncher.data.packGrid
import app.ttlauncher.data.pageOf
import app.ttlauncher.data.widget
import app.ttlauncher.data.withoutWidget
import app.ttlauncher.feed.RssParser
import app.ttlauncher.feed.TopicFilter
import app.ttlauncher.feed.normalizeFeedUrl
import app.ttlauncher.ui.restingPage
import app.ttlauncher.weather.conditionOf
import app.ttlauncher.weather.key
import app.ttlauncher.weather.parsePlaces
import app.ttlauncher.weather.parseWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.StringReader

class LogicTest {
    private fun parse(xml: String) = RssParser.parse(KXmlParser().apply { setInput(StringReader(xml)) })

    @Test
    fun countsUnreadNotifications() {
        val counts = badgeCounts(
            listOf(
                // Two messages under a summary: the summary isn't a third.
                Note("chat", groupSummary = true), Note("chat"), Note("chat"),
                // The app's own count is believed.
                Note("mail", number = 7),
                // A summary with nothing under it still counts.
                Note("news", groupSummary = true),
                // Music controls and apps with badges switched off don't.
                Note("music", ongoing = true), Note("quiet", badge = false),
                // The same app in the work profile is counted apart.
                Note("mail#10"),
            ),
        )
        assertEquals(mapOf("chat" to 2, "mail" to 7, "news" to 1, "mail#10" to 1), counts)
    }

    @Test
    fun readsRss() {
        val feed = parse(
            """<?xml version="1.0"?>
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel>
              <title>Example News</title><link>https://example.com</link>
              <image><title>Logo</title><url>https://example.com/logo.png</url></image>
              <item>
                <title><![CDATA[Fish &amp; chips prices <b>rise</b>]]></title>
                <link>https://example.com/business/fish</link>
                <description>&lt;p&gt;Prices are up 5&amp;#37; this year.&lt;/p&gt;</description>
                <pubDate>Sat, 10 Oct 2026 09:30:00 GMT</pubDate>
                <category>Business</category><category>Food</category>
                <media:thumbnail url="https://example.com/fish.jpg"/>
              </item>
              <item><title>No link, so skipped</title></item>
              <item>
                <title>Second</title><link>https://example.com/2</link>
                <description><![CDATA[<img src="https://example.com/inline.png"> Body text]]></description>
                <pubDate>Fri, 9 Oct 2026 08:00:00 +0100</pubDate>
                <media:content width="140" url="https://example.com/small.jpg?width=140"/>
                <media:content width="700" url="https://example.com/large.jpg?width=700&amp;s=1"/>
              </item>
            </channel></rss>""",
        )
        assertEquals("Example News", feed.title)
        assertEquals(2, feed.items.size)
        val first = feed.items[0]
        assertEquals("Fish & chips prices rise", first.title)
        assertEquals("https://example.com/business/fish", first.link)
        assertEquals("Prices are up 5% this year.", first.summary)
        assertEquals("https://example.com/fish.jpg", first.image)
        assertEquals(listOf("Business", "Food"), first.categories)
        assertEquals(1791624600000L, first.time)
        assertEquals("https://example.com/large.jpg?width=700&s=1", feed.items[1].image)
        assertEquals("Body text", feed.items[1].summary)
        assertEquals(1791529200000L, feed.items[1].time)
    }

    @Test
    fun readsAtom() {
        val feed = parse(
            """<feed xmlns="http://www.w3.org/2005/Atom">
              <title type="text">Example Blog</title>
              <entry>
                <title>Hello</title>
                <link rel="self" href="https://example.com/self"/>
                <link rel="alternate" type="text/html" href="https://example.com/hello"/>
                <author><name>Someone</name></author>
                <published>2026-10-10T09:30:00Z</published>
                <updated>2026-10-11T00:00:00Z</updated>
                <category term="Kotlin"/>
                <content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml"><p>Nested <em>markup</em> here.</p></div></content>
              </entry>
            </feed>""",
        )
        assertEquals("Example Blog", feed.title)
        val entry = feed.items.single()
        assertEquals("Hello", entry.title)
        assertEquals("https://example.com/hello", entry.link)
        assertEquals("Nested markup here.", entry.summary)
        assertEquals(listOf("Kotlin"), entry.categories)
        assertEquals(1791624600000L, entry.time)
    }

    @Test
    fun keepsItemsBeforeABreak() {
        val feed = parse("<rss><channel><item><title>Ok</title><link>https://example.com/ok</link></item><item><title>Broken")
        assertEquals(listOf("Ok"), feed.items.map { it.title })
    }

    @Test
    fun mutesTopics() {
        val filter = TopicFilter(listOf("Soccer", "football", "transfer window"))
        fun article(title: String, link: String = "https://example.com/a", tags: List<String> = emptyList()) = Article(title, link, categories = tags)

        assertTrue(filter.hides(article("FOOTBALL: late winner at Anfield")))
        assertTrue(filter.hides(article("Five footballs lost in river")))
        assertTrue(filter.hides(article("Club busy as the Transfer Window shuts")))
        assertTrue(filter.hides(article("Late winner", tags = listOf("Soccer"))))
        assertTrue(filter.hides(article("Late winner", link = "https://www.bbc.co.uk/sport/football/articles/abc")))
        assertFalse(filter.hides(article("Footballer turned chef opens restaurant")))
        assertFalse(filter.hides(article("Budget day: what to expect")))
        assertFalse(TopicFilter(emptyList()).hides(article("football")))

        val sport = TopicFilter.of(emptyList(), listOf("sport"))
        assertTrue(sport.hides(article("England win the Ashes")))
        assertFalse(sport.hides(article("Public transport fares frozen")))
    }

    @Test
    fun sortsIntoFolders() {
        val defaults = FolderSorter(DefaultFolders)
        assertEquals("MEDIA", defaults.folderFor(AppCategory.VIDEO, "com.netflix.mediaclient", "Netflix", null))
        assertEquals("MEDIA", defaults.folderFor(AppCategory.AUDIO, "com.spotify.music", "Spotify", null))

        // Video and audio split out of Media, and a folder that only collects by word.
        val custom = FolderSorter(
            DefaultFolders.map { if (it.id == "MEDIA") it.copy(topics = setOf("READING")) else it } +
                Folder("video", "Video", "movie", setOf("VIDEO")) +
                Folder("audio", "Audio", "music", setOf("AUDIO")) +
                Folder("google", "Google", "star", keywords = listOf("google")),
        )
        assertEquals("video", custom.folderFor(AppCategory.VIDEO, "com.netflix.mediaclient", "Netflix", null))
        assertEquals("audio", custom.folderFor(AppCategory.AUDIO, "com.spotify.music", "Spotify", null))
        assertEquals("MEDIA", custom.folderFor(AppCategory.READING, "com.amazon.kindle", "Kindle", null))
        // A folder's words beat the topic; the user's own filing beats both.
        assertEquals("google", custom.folderFor(AppCategory.VIDEO, "com.google.android.youtube", "YouTube", null))
        assertEquals("video", custom.folderFor(AppCategory.VIDEO, "com.google.android.youtube", "YouTube", "video"))
        // A filing that points at a deleted folder is ignored.
        assertEquals("video", custom.folderFor(AppCategory.VIDEO, "com.netflix.mediaclient", "Netflix", "gone"))
        // A topic no folder collects.
        assertEquals(OTHER_FOLDER, FolderSorter(emptyList()).folderFor(AppCategory.GAMES, "com.example.g", "G", null))
    }

    @Test
    fun givesNewTopicsTheirFolders() {
        val first = DefaultFolders.filter { it.topics.all(FirstTopics::contains) }
        // A setup saved before Shopping existed, with the folders reordered and one of the user's own.
        val mine = Folder("mine", "Mine", "star", keywords = listOf("google"))
        val saved = LauncherState(folders = (first.reversed() + mine).map { if (it.id == "MONEY") it.copy(icon = "bag") else it }, seenTopics = FirstTopics)
        val updated = saved.withNewTopics()
        assertEquals(AppCategory.entries.map { it.name }.toSet(), updated.seenTopics)
        assertEquals(AppCategory.entries.map { it.name }.toSet(), updated.folders.flatMap { it.topics }.toSet())
        assertEquals(saved.folders.map { it.id }, updated.folders.map { it.id }.filter { id -> saved.folders.any { it.id == id } })
        assertEquals("SHOPPING", updated.folders[updated.folders.indexOfFirst { it.id == "MONEY" } + 1].id)
        assertEquals("money", updated.folders.first { it.id == "MONEY" }.icon)
        assertEquals(mine, updated.folders.first { it.id == "mine" })
        // Once offered, a topic the user took out of every folder stays out.
        val removed = updated.copy(folders = updated.folders.filter { it.id != "SHOPPING" })
        assertEquals(removed, removed.withNewTopics())
        // A folder that already collects the topic keeps it.
        val own = saved.copy(folders = saved.folders + Folder("buy", "Buy", "bag", setOf("SHOPPING")))
        assertTrue(own.withNewTopics().folders.none { it.id == "SHOPPING" })
        assertEquals(DefaultFolders, LauncherState().withNewTopics().folders)
    }

    @Test
    fun sizesWidgetsToTheGrid() {
        // The Fold's cover screen: 337dp of grid in four columns.
        val cover = GridSpec(columns = 4, rowDp = 72f, widthDp = 337f)
        val clock = WidgetConfig("c", WidgetType.CLOCK).limits()
        val month = WidgetConfig("m", WidgetType.CALENDAR).limits()
        assertEquals(CellSize(4, 2), cover.defaultSize(clock))
        // Can't be dragged narrower than its digits need, or taller than looks sensible.
        assertEquals(CellSize(2, 1), cover.clamp(CellSize(1, 0), clock))
        assertEquals(CellSize(4, 3), cover.clamp(CellSize(9, 9), clock))
        // A calendar needs most of the width and four rows.
        assertEquals(3..4, cover.columnRange(month))
        assertEquals(3, cover.rowRange(month).first)

        // The same widget on the inner screen's eight columns takes half the width by default.
        val inner = GridSpec(columns = 8, rowDp = 72f, widthDp = 675f)
        assertEquals(4, inner.defaultSize(clock).cols)
        // A finer grid needs more cells for the same minimum size.
        assertEquals(4, GridSpec(columns = 8, rowDp = 72f, widthDp = 337f).columnRange(clock).first)
    }

    @Test
    fun clockAndDateShareARow() {
        val clock = WidgetConfig("c", WidgetType.CLOCK).limits()
        val date = WidgetConfig("d", WidgetType.DATE, variant = 1).limits()
        // On the cover screen, at every column count it offers that can hold two widgets.
        for (columns in 4..7) {
            val grid = GridSpec(columns, rowDp = 72f, widthDp = 337f)
            val sizes = listOf(grid.clamp(CellSize(1, 1), clock), grid.clamp(CellSize(1, 1), date))
            assertTrue("$columns columns: ${sizes.map { it.cols }}", sizes.sumOf { it.cols } <= columns)
            assertEquals("$columns columns", listOf(0, 0), packGrid(columns, sizes).map { it.row })
        }
    }

    @Test
    fun packsWidgetsIntoGaps() {
        // A tall widget on the left, with two short ones stacking beside it, then a full-width one below.
        val placed = packGrid(4, listOf(CellSize(2, 2), CellSize(2, 1), CellSize(2, 1), CellSize(4, 1), CellSize(9, 1)))
        assertEquals(listOf(0 to 0, 2 to 0, 2 to 1, 0 to 2, 0 to 3), placed.map { it.column to it.row })
    }

    @Test
    fun movesWidgetsBetweenPages() {
        val layout = ScreenLayout(4, pages = listOf(WidgetPage("a", listOf(WidgetConfig("x", WidgetType.CLOCK), WidgetConfig("y", WidgetType.DATE))), WidgetPage("b")))
        val moved = layout.moveWidget("x", 1)
        assertEquals(listOf("y"), moved.pages[0].widgets.map { it.id })
        assertEquals(listOf("x"), moved.pages[1].widgets.map { it.id })
        assertEquals(1, moved.pageOf("x"))
        assertNull(moved.withoutWidget("x").widget("x"))
    }

    @Test
    fun makesGlyphsForUnthemedIcons() {
        val size = 20
        fun icon(paint: (x: Int, y: Int) -> Int) = IntArray(size * size) { paint(it % size, it / size) }
        fun inCross(x: Int, y: Int) = (x in 8..11 && y in 3..16) || (y in 8..11 && x in 3..16)
        val white = 0xFFFFFFFF.toInt()
        val green = 0xFF05CE78.toInt()

        // A white cross on transparency is already a glyph: it comes back as itself.
        val cross = icon { x, y -> if (inCross(x, y)) white else 0 }
        assertTrue(AutoMono.isGlyph(cross, size))
        val crossMask = AutoMono.mask(cross, size)!!
        assertEquals(255, crossMask[10 * size + 10])
        assertEquals(0, crossMask[0])

        // The same cross on a green plate: the plate drops out and the cross is left.
        val plated = icon { x, y -> if (inCross(x, y)) white else green }
        assertFalse(AutoMono.isGlyph(plated, size))
        val platedMask = AutoMono.mask(plated, size)!!
        assertEquals(255, platedMask[10 * size + 10])
        assertEquals(0, platedMask[0])

        // One flat colour has nothing to pull out, so the app keeps its own icon.
        assertNull(AutoMono.mask(icon { _, _ -> green }, size))
        assertNull(AutoMono.mask(icon { _, _ -> 0 }, size))
    }

    @Test
    fun normalizesFeedUrls() {
        assertEquals("https://example.com/rss", normalizeFeedUrl(" example.com/rss "))
        assertEquals("https://example.com/rss", normalizeFeedUrl("http://example.com/rss"))
        assertEquals("https://example.com/rss", normalizeFeedUrl("feed://example.com/rss"))
        assertNull(normalizeFeedUrl("not a url"))
        assertNull(normalizeFeedUrl("ftp://example.com/rss"))
    }

    @Test
    fun categorizesApps() {
        fun category(pkg: String, label: String, system: Int = -1, isSystem: Boolean = false) = Categorizer.categorize(pkg, label, system, isSystem)

        assertEquals(AppCategory.COMMUNICATION, category("com.whatsapp", "WhatsApp"))
        assertEquals(AppCategory.COMMUNICATION, category("com.microsoft.office.outlook", "Outlook"))
        assertEquals(AppCategory.WORK, category("com.microsoft.office.word", "Word"))
        assertEquals(AppCategory.INTERNET, category("org.mozilla.firefox", "Firefox"))
        assertEquals(AppCategory.TRAVEL, category("com.booking", "Booking.com"))
        assertEquals(AppCategory.SYSTEM, category("com.android.vending", "Play Store"))
        // Declared by the developer.
        assertEquals(AppCategory.GAMES, category("com.example.blocks", "Blocks", system = 0))
        assertEquals(AppCategory.TRAVEL, category("com.example.somewhere", "Somewhere", system = 6))
        // Worked out from the name.
        assertEquals(AppCategory.COMMUNICATION, category("com.samsung.android.email.provider", "Email"))
        assertEquals(AppCategory.TOOLS, category("com.sec.android.app.voicenote", "Voice Recorder"))
        assertEquals(AppCategory.SECURITY, category("com.onepassword.android", "1Password"))
        assertEquals(AppCategory.SECURITY, category("com.blizzard.bma", "Authenticator"))
        assertEquals(AppCategory.SECURITY, category("com.example.keys", "Password Safe"))
        assertEquals(AppCategory.HEALTH, category("com.sec.android.app.shealth", "Samsung Health"))
        assertEquals(AppCategory.HEALTH, category("com.example.fit", "Home Workout"))
        assertEquals(AppCategory.SHOPPING, category("com.amazon.mShop.android.shopping", "Amazon Shopping"))
        assertEquals(AppCategory.SHOPPING, category("com.ebay.mobile", "eBay"))
        assertEquals(AppCategory.SHOPPING, category("com.example.grocer", "Tesco Groceries"))
        assertEquals(AppCategory.SHOPPING, category("com.example.app", "Corner Shop"))
        assertEquals(AppCategory.MONEY, category("com.example.tb", "Tesco Bank"))
        assertEquals(AppCategory.SHOPPING, category("com.example.post", "Parcel Tracker"))
        assertEquals(AppCategory.TRAVEL, category("com.example.ride", "City Bus"))
        assertEquals(AppCategory.FOOD, category("com.ubercab.eats", "Uber Eats"))
        assertEquals(AppCategory.TRAVEL, category("com.ubercab", "Uber"))
        assertEquals(AppCategory.FOOD, category("com.example.pizza", "Pizza Place"))
        assertEquals(AppCategory.SMART_HOME, category("com.samsung.android.oneconnect", "SmartThings"))
        assertEquals(AppCategory.SMART_HOME, category("com.example.heat", "Smart Thermostat"))
        assertEquals(AppCategory.EDUCATION, category("com.duolingo", "Duolingo"))
        assertEquals(AppCategory.EDUCATION, category("com.example.words", "Learn Spanish"))
        assertEquals(AppCategory.MONEY, category("uk.co.example.app", "Example Bank"))
        assertEquals(AppCategory.PHOTOS, category("com.example.snap", "Open Camera"))
        assertEquals(AppCategory.AUDIO, category("com.example.cast", "Podcasts"))
        assertEquals(AppCategory.AUDIO, category("com.spotify.music", "Spotify"))
        assertEquals(AppCategory.AUDIO, category("com.google.android.apps.youtube.music", "YT Music"))
        assertEquals(AppCategory.VIDEO, category("com.google.android.youtube", "YouTube"))
        assertEquals(AppCategory.VIDEO, category("com.example.flix", "Movies Anywhere"))
        assertEquals(AppCategory.READING, category("com.example.paper", "Daily News"))
        // Nothing to go on.
        assertEquals(AppCategory.TOOLS, category("com.example.zzz", "Zzz"))
        assertEquals(AppCategory.SYSTEM, category("com.samsung.zzz", "Zzz", isSystem = true))
    }

    @Test
    fun readsWeather() {
        val weather = parseWeather(
            """{"latitude":51.5,"current":{"time":"2026-10-11T12:00","temperature_2m":14.2,"apparent_temperature":12.9,"relative_humidity_2m":71,"wind_speed_10m":18.4,"weather_code":61,"is_day":0},
            "daily":{"time":["2026-10-11","2026-10-12","2026-10-13"],"weather_code":[61,3,null],"temperature_2m_max":[15.1,12.0,null],"temperature_2m_min":[8.4,7.0,5.0]}}""",
            at = 5,
        )
        assertEquals(14.2, weather.temperature, 0.001)
        assertEquals(71, weather.humidity)
        assertFalse(weather.day)
        assertEquals("Rain", conditionOf(weather.code))
        // The day with no high is left out.
        assertEquals(listOf("2026-10-11", "2026-10-12"), weather.days.map { it.date })
        assertEquals(7.0, weather.days[1].low, 0.001)

        val places = parsePlaces("""{"results":[{"id":1,"name":"Leeds","latitude":53.79648,"longitude":-1.54785,"admin1":"England","country":"United Kingdom"}]}""")
        assertEquals("Leeds", places.single().place.name)
        assertEquals("England, United Kingdom", places.single().region)
        assertEquals("53.80,-1.55", places.single().place.key())
        assertTrue(parsePlaces("""{"generationtime_ms":0.5}""").isEmpty())
    }

    @Test
    fun settlesADraggedPage() {
        // Let go slowly: back where it started unless it got a third of the way.
        assertEquals(0, restingPage(at = 0.2f, from = 0, speed = 0f, flick = 100f))
        assertEquals(1, restingPage(at = 0.4f, from = 0, speed = 0f, flick = 100f))
        assertEquals(1, restingPage(at = 1.6f, from = 2, speed = 0f, flick = 100f))
        // A flick carries it on whichever way it was going, however short.
        assertEquals(1, restingPage(at = 0.05f, from = 0, speed = -300f, flick = 100f))
        assertEquals(0, restingPage(at = 0.6f, from = 0, speed = 300f, flick = 100f))
        assertEquals(1, restingPage(at = 1.9f, from = 2, speed = 300f, flick = 100f))
    }

    @Test
    fun movesDockEntries() {
        val dock = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), dock.moved("a", "c"))
        assertEquals(listOf("d", "a", "b", "c"), dock.moved("d", "a"))
        // Anything that isn't there leaves the order alone.
        assertEquals(dock, dock.moved("a", "x"))
        assertEquals(dock, dock.moved("b", "b"))
    }
}
