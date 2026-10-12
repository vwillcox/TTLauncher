package app.ttlauncher.apps

/** What the auto-sorter can tell apart. The user's folders each collect one or more of these. */
enum class AppCategory(val label: String) {
    COMMUNICATION("Chat"),
    INTERNET("Web"),
    AUDIO("Audio"),
    VIDEO("Video"),
    READING("News & reading"),
    GAMES("Games"),
    PHOTOS("Photos"),
    WORK("Work"),
    SECURITY("Security"),
    MONEY("Money"),
    SHOPPING("Shopping"),
    FOOD("Food"),
    TRAVEL("Travel"),
    HEALTH("Health"),
    SMART_HOME("Smart home"),
    EDUCATION("Learning"),
    TOOLS("Tools"),
    SYSTEM("System"),
}

/**
 * Gives an app a topic from what the device already knows about it: a table of well-known
 * packages, the category the developer declared, then words in the package name and label.
 * Nothing leaves the device.
 */
object Categorizer {
    // ApplicationInfo.CATEGORY_*, repeated here so this stays free of Android classes.
    private const val GAME = 0
    private const val AUDIO = 1
    private const val VIDEO = 2
    private const val IMAGE = 3
    private const val SOCIAL = 4
    private const val NEWS = 5
    private const val MAPS = 6
    private const val PRODUCTIVITY = 7
    private const val ACCESSIBILITY = 8

    private val known: Map<String, AppCategory> = buildMap {
        fun add(category: AppCategory, vararg packages: String) = packages.forEach { put(it, category) }
        add(
            AppCategory.COMMUNICATION,
            "com.whatsapp", "org.telegram", "org.thoughtcrime.securesms", "com.discord", "com.facebook.orca",
            "com.facebook.katana", "com.instagram", "com.twitter.android", "com.snapchat.android",
            "com.reddit.frontpage", "com.linkedin.android", "com.google.android.gm", "ch.protonmail.android",
            "com.microsoft.office.outlook", "com.samsung.android.messaging", "com.samsung.android.dialer",
            "com.samsung.android.app.contacts", "com.google.android.dialer", "com.google.android.contacts",
            "com.google.android.apps.messaging", "org.joinmastodon.android", "xyz.blueskyweb.app",
        )
        add(
            AppCategory.INTERNET,
            "com.android.chrome", "org.mozilla", "com.sec.android.app.sbrowser", "com.brave.browser",
            "com.duckduckgo.mobile.android", "com.microsoft.emmx", "com.opera", "com.vivaldi.browser",
            "com.google.android.googlequicksearchbox", "ch.protonvpn.android",
        )
        add(
            AppCategory.AUDIO,
            "com.spotify.music", "com.google.android.apps.youtube.music", "com.bbc.sounds", "com.audible.application",
            "com.sec.android.app.music", "com.apple.android.music", "com.amazon.mp3", "deezer.android.app",
            "au.com.shiftyjelly.pocketcasts", "com.soundcloud.android", "com.shazam.android",
        )
        add(
            AppCategory.VIDEO,
            "com.google.android.youtube", "com.netflix", "com.amazon.avod", "com.disney.disneyplus",
            "tv.twitch.android.app", "org.videolan.vlc", "com.plexapp.android", "org.jellyfin", "bbc.iplayer.android",
            "com.samsung.android.video", "com.zhiliaoapp.musically", "com.samsung.android.tvplus",
        )
        add(AppCategory.READING, "com.amazon.kindle", "com.google.android.apps.books", "bbc.mobile.news.uk", "com.guardian")
        add(
            AppCategory.PHOTOS,
            "com.sec.android.app.camera", "com.sec.android.gallery3d", "com.google.android.apps.photos",
            "com.google.android.GoogleCamera", "com.adobe.lrmobile",
        )
        add(
            AppCategory.WORK,
            "com.google.android.apps.docs", "com.microsoft.office", "com.microsoft.teams", "com.Slack",
            "us.zoom.videomeetings", "com.google.android.calendar", "com.samsung.android.calendar",
            "com.samsung.android.app.notes", "com.google.android.keep", "notion.id", "com.todoist",
            "com.github.android", "com.termux",
        )
        add(
            AppCategory.SECURITY,
            "com.onepassword.android", "com.agilebits.onepassword", "com.x8bit.bitwarden", "proton.android.pass",
            "com.lastpass.lpandroid", "com.dashlane", "com.kunzisoft.keepass", "keepass2android",
            "com.google.android.apps.authenticator2", "com.azure.authenticator", "com.authy.authy",
            "com.beemdevelopment.aegis", "org.fedorahosted.freeotp", "com.yubico.yubioath", "com.duosecurity.duomobile",
            "com.blizzard.bma", "com.blizzard.messenger", "com.samsung.android.samsungpass", "com.samsung.knox.securefolder",
        )
        add(
            AppCategory.MONEY,
            "com.paypal.android.p2pmobile", "com.google.android.apps.walletnfcrel", "com.samsung.android.spay",
            "com.revolut.revolut", "co.uk.getmondo", "com.starlingbank.android", "com.klarna.android",
        )
        add(
            AppCategory.SHOPPING,
            "com.amazon.mShop.android.shopping", "com.ebay.mobile", "com.etsy.android", "fr.vinted",
            "com.alibaba.aliexpresshd", "com.einnovation.temu", "com.zzkko", "com.ingka.ikea.app", "com.asos.app",
            "com.homeretailgroup.argos.android", "com.tesco.grocery.view", "com.tesco.clubcardmobile",
            "com.sainsburys.gol", "com.asda.android", "com.ocado.mobile.android", "de.mobile.android.app",
            "com.gumtree.android", "com.shopify.arrive",
        )
        add(
            AppCategory.FOOD,
            "com.ubercab.eats", "com.deliveroo.orderapp", "com.justeat.app.uk", "com.dd.doordash",
            "com.mcdonalds.app.uk", "com.starbucks.mobilecard", "com.app.tgtg", "com.hellofresh.androidapp",
        )
        add(
            AppCategory.HEALTH,
            "com.sec.android.app.shealth", "com.google.android.apps.fitness", "com.google.android.apps.healthdata",
            "com.strava", "com.fitbit.FitbitMobile", "com.garmin.android.apps.connectmobile", "com.myfitnesspal.android",
            "com.nhs.online.nhsonline", "com.calm.android", "com.getsomeheadspace.android",
        )
        add(
            AppCategory.SMART_HOME,
            "com.samsung.android.oneconnect", "com.google.android.apps.chromecast.app", "io.homeassistant.companion.android",
            "com.philips.lighting.hue2", "com.amazon.dee.app", "com.tplink.iot", "com.ringapp", "com.nest.android",
            "com.tado", "uk.co.centrica.hive",
        )
        add(
            AppCategory.EDUCATION,
            "com.duolingo", "org.khanacademy.android", "com.google.android.apps.classroom", "org.coursera.android",
            "com.udemy.android", "com.ichi2.anki",
        )
        add(
            AppCategory.TRAVEL,
            "com.google.android.apps.maps", "com.waze", "com.ubercab", "com.airbnb.android", "com.booking",
            "com.thetrainline", "com.citymapper.app.release",
        )
        add(
            AppCategory.TOOLS,
            "com.sec.android.app.popupcalculator", "com.sec.android.app.clockpackage", "com.google.android.calculator",
            "com.sec.android.app.myfiles", "com.google.android.apps.nbu.files", "com.google.android.deskclock",
        )
        add(
            AppCategory.SYSTEM,
            "com.android.settings", "com.android.vending", "com.sec.android.app.samsungapps", "com.samsung.android.lool",
        )
    }

    private fun words(pattern: String) = Regex("\\b(?:$pattern)\\b")

    // Checked in order, against the package name and label as lower-case words.
    private val keywords = listOf(
        AppCategory.SECURITY to words("authenticator|passwords?|passkeys?|2fa|otp|totp|keepass|vault|antivirus|security"),
        AppCategory.GAMES to words("games?|gaming|puzzle|arcade|chess|sudoku|solitaire|minecraft|roblox|supercell|rovio|playrix|king|zynga|nintendo|steam|playstation|xbox"),
        AppCategory.TRAVEL to words("maps?|navigation|travel|flights?|airlines?|airways|hotels?|taxis?|cabs?|minicabs?|bus|buses|rail\\w*|trains?|transit|parking|booking|trip\\w*"),
        AppCategory.MONEY to words("bank\\w*|pay|payments?|wallet|finance|money|markets?|invest\\w*|trading|crypto|insurance|cash\\w*"),
        AppCategory.FOOD to words("food\\w*|eats?|pizza|burgers?|restaurants?|recipes?|cook|cooking|cookbook|takeaways?|kitchen|sushi|curry|kebabs?|coffee|cafe|deliveroo|justeat|mcdonalds|dominos|starbucks|greggs|nandos|kfc"),
        AppCategory.SHOPPING to words("shop\\w*|stores?|marketplace|supermarket|grocer\\w*|deals?|vouchers?|coupons?|fashion|clubcard|amazon|ebay|etsy|vinted|aliexpress|temu|shein|ikea|argos|tesco|asda|sainsbury\\w*|aldi|lidl|ocado|parcels?|courier"),
        AppCategory.HEALTH to words("health\\w*|fitness|workouts?|gym|yoga|meditat\\w*|sleep|pedometer|calories?|diet|pharmacy|nhs|medical|doctor|strava|fitbit|garmin"),
        AppCategory.SMART_HOME to words("smartthings|smart ?home|home ?assistant|thermostat|doorbell|hue|nest|alexa|tapo|kasa|tado|govee|tuya|zigbee"),
        AppCategory.PHOTOS to words("camera|gallery|photos?|photography|snapseed|lightroom|picsart"),
        AppCategory.COMMUNICATION to words("messag\\w*|messenger|chat\\w*|e?mail|sms|dialer|phone|contacts|social|telegram|signal|whatsapp|discord|mastodon"),
        AppCategory.EDUCATION to words("learn\\w*|education\\w*|courses?|classroom|school|university|study|lessons?|tutor\\w*|flashcards?|quiz\\w*|dictionary|duolingo|babbel|memrise|coursera|udemy"),
        AppCategory.WORK to words("office|docs?|sheets?|slides|calendar|notes?|todo|tasks?|drive|meet\\w*|pdf|word|excel|powerpoint|jira|github|terminal|ssh"),
        AppCategory.AUDIO to words("music|radio|podcasts?|audio|audiobooks?|fm"),
        AppCategory.VIDEO to words("videos?|tv|stream\\w*|movies?|cinema|player|iplayer"),
        AppCategory.READING to words("books?|reader|news|comics?|magazines?|rss"),
        AppCategory.INTERNET to words("browser|web|search|vpn|wifi|downloads?|torrent|firefox|chrome"),
        AppCategory.TOOLS to words("calculator|clock|files?|weather|flashlight|recorder|compass|translat\\w*|scanner|keyboard|remote"),
        AppCategory.SYSTEM to words("settings|setup|updates?|launcher|system|services?|device|backup"),
    )

    fun categorize(packageName: String, label: String, systemCategory: Int, isSystemApp: Boolean): AppCategory {
        knownPackage(packageName)?.let { return it }
        when (systemCategory) {
            GAME -> return AppCategory.GAMES
            AUDIO -> return AppCategory.AUDIO
            VIDEO -> return AppCategory.VIDEO
            NEWS -> return AppCategory.READING
            IMAGE -> return AppCategory.PHOTOS
            SOCIAL -> return AppCategory.COMMUNICATION
            MAPS -> return AppCategory.TRAVEL
            ACCESSIBILITY -> return AppCategory.SYSTEM
        }
        val text = (packageName.replace('.', ' ').replace('_', ' ') + " " + label).lowercase()
        keywords.firstOrNull { it.second.containsMatchIn(text) }?.let { return it.first }
        // Declared by far more apps than it describes, so it only breaks ties.
        if (systemCategory == PRODUCTIVITY) return AppCategory.WORK
        return if (isSystemApp) AppCategory.SYSTEM else AppCategory.TOOLS
    }

    /** The longest entry of [known] that is this package or a parent of it. */
    private fun knownPackage(packageName: String): AppCategory? {
        var name = packageName
        while (true) {
            known[name]?.let { return it }
            val dot = name.lastIndexOf('.')
            if (dot < 0) return null
            name = name.substring(0, dot)
        }
    }
}
