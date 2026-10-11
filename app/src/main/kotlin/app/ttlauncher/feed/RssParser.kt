package app.ttlauncher.feed

import app.ttlauncher.data.Article
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ParsedFeed(val title: String?, val items: List<Article>)

/** Reads RSS 2.0, RSS 1.0 and Atom. Takes the parser so it runs off-device in tests. */
object RssParser {
    private val imageUrl = Regex("\\.(?:jpe?g|png|webp|gif)(?:\\?|$)", RegexOption.IGNORE_CASE)
    private val imgTag = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val tag = Regex("<[^>]*>")
    private val space = Regex("\\s+")
    private val entity = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")
    private val entities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "hellip" to "…", "mdash" to "—", "ndash" to "–", "lsquo" to "‘", "rsquo" to "’",
        "ldquo" to "“", "rdquo" to "”", "pound" to "£", "euro" to "€",
    )

    fun parse(parser: XmlPullParser): ParsedFeed {
        val items = ArrayList<Article>()
        var title: String? = null
        // A feed that breaks part-way still gives up the items before the break.
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name.lowercase()) {
                        "item", "entry" -> readItem(parser)?.let(items::add)
                        "title" -> if (title == null) title = clean(text(parser))
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            if (items.isEmpty()) throw e
        }
        return ParsedFeed(title?.takeIf { it.isNotEmpty() }, items)
    }

    private fun readItem(p: XmlPullParser): Article? {
        val end = p.name
        var title = ""
        var link = ""
        var summary = ""
        var content = ""
        var date = ""
        var image: String? = null
        var imageWidth = 0
        val categories = ArrayList<String>()
        while (true) {
            val event = p.next()
            if (event == XmlPullParser.END_DOCUMENT || (event == XmlPullParser.END_TAG && p.name == end)) break
            if (event != XmlPullParser.START_TAG) continue
            when (p.name.lowercase()) {
                "title" -> title = text(p)
                "link" -> {
                    val href = p.getAttributeValue(null, "href")
                    val rel = p.getAttributeValue(null, "rel")
                    if (href == null) link = text(p).trim()
                    else if (link.isEmpty() || rel == null || rel == "alternate") link = href
                }
                "description", "summary" -> summary = text(p)
                "content:encoded", "content" -> content = text(p)
                "pubdate", "published", "dc:date" -> date = text(p)
                "updated" -> text(p).let { if (date.isEmpty()) date = it }
                "category" -> {
                    val term = p.getAttributeValue(null, "term")
                    val body = text(p)
                    clean(term ?: body).takeIf { it.isNotEmpty() }?.let(categories::add)
                }
                "media:thumbnail" -> if (image == null) {
                    image = p.getAttributeValue(null, "url")
                    imageWidth = p.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                }
                "media:content", "enclosure" -> {
                    val url = p.getAttributeValue(null, "url")
                    val type = p.getAttributeValue(null, "type").orEmpty()
                    val isImage = type.startsWith("image") || p.getAttributeValue(null, "medium") == "image" ||
                        (type.isEmpty() && url != null && imageUrl.containsMatchIn(url))
                    // Feeds that offer one picture in several sizes list them smallest first.
                    val width = p.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                    if (url != null && isImage && (image == null || width > imageWidth)) {
                        image = url
                        imageWidth = width
                    }
                }
                // These carry a title of their own.
                "author", "source" -> text(p)
            }
        }
        if (image == null) image = imgTag.find(content)?.groupValues?.get(1) ?: imgTag.find(summary)?.groupValues?.get(1)
        val cleanTitle = clean(title)
        if (cleanTitle.isEmpty() || link.isEmpty()) return null
        return Article(
            title = cleanTitle,
            link = link,
            summary = clean(summary.ifBlank { content }).take(320),
            image = image?.let(::decodeEntities)?.takeIf { it.startsWith("http") },
            time = parseDate(date.trim()),
            categories = categories,
        )
    }

    /** The text inside the current element, markup included, leaving the parser on its end tag. */
    private fun text(p: XmlPullParser): String {
        val out = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.TEXT -> out.append(p.text.orEmpty())
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return out.toString()
    }

    fun clean(html: String): String = space.replace(decodeEntities(tag.replace(html, " ")), " ").trim()

    private fun decodeEntities(s: String): String = entity.replace(s) { m ->
        val name = m.groupValues[1]
        when {
            name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)?.let(::codePoint)
            name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePoint)
            else -> entities[name]
        } ?: m.value
    }

    private fun codePoint(code: Int): String? = if (Character.isValidCodePoint(code)) String(Character.toChars(code)) else null

    fun parseDate(s: String): Long {
        if (s.isEmpty()) return 0
        runCatching { return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(s).toEpochMilli() }
        // Named zones ("EST") and missing weekdays, which the RFC 1123 formatter refuses.
        for (pattern in listOf("EEE, d MMM yyyy HH:mm:ss z", "d MMM yyyy HH:mm:ss z", "EEE, d MMM yyyy HH:mm z")) {
            runCatching { return SimpleDateFormat(pattern, Locale.US).parse(s)!!.time }
        }
        return 0
    }
}
