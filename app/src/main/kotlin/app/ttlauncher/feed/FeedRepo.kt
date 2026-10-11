package app.ttlauncher.feed

import android.content.Context
import app.ttlauncher.data.Article
import app.ttlauncher.data.FeedSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File

/**
 * Fetches feeds only while the news page is on screen and the last fetch has gone stale. There is no
 * background sync: a launcher that wakes the radio on a timer costs battery for news nobody is reading.
 */
class FeedRepo(context: Context, private val http: OkHttpClient, private val scope: CoroutineScope) {
    @Serializable
    private data class Saved(val at: Long = 0, val sources: String = "", val articles: List<Article> = emptyList())

    private val file = File(context.cacheDir, "feed.json")
    private val json = Json { ignoreUnknownKeys = true }
    private var fetchedAt = 0L
    private var fetchedSources = ""

    private val _articles = MutableStateFlow<List<Article>>(emptyList())
    val articles: StateFlow<List<Article>> = _articles.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Kept apart from the stories and out of the cache, so clearing the cache doesn't bring read stories back.
    private val readFile = File(context.filesDir, "read.json")
    private val _read = MutableStateFlow<Set<String>>(emptySet())

    /** The links of the stories that have been opened, oldest first. */
    val read: StateFlow<Set<String>> = _read.asStateFlow()

    private val restored = scope.launch(Dispatchers.IO) {
        runCatching { json.decodeFromString<List<String>>(readFile.readText()) }.getOrNull()?.let { saved -> _read.update { saved.toSet() + it } }
        val saved = runCatching { json.decodeFromString<Saved>(file.readText()) }.getOrNull() ?: return@launch
        fetchedAt = saved.at
        fetchedSources = saved.sources
        _articles.value = saved.articles
    }

    fun markRead(link: String) = saveRead { (it - link + link).toList().takeLast(MAX_READ).toSet() }

    fun clearRead() = saveRead { emptySet() }

    private fun saveRead(change: (Set<String>) -> Set<String>) {
        val links = _read.updateAndGet(change)
        scope.launch(Dispatchers.IO) { runCatching { readFile.writeText(json.encodeToString(links.toList())) } }
    }

    suspend fun refresh(sources: List<FeedSource>, force: Boolean) {
        restored.join()
        val sourcesKey = sources.joinToString("\n") { it.url }
        val fresh = System.currentTimeMillis() - fetchedAt < STALE_AFTER_MS && sourcesKey == fetchedSources
        if (fresh && !force) return
        if (!_refreshing.compareAndSet(expect = false, update = true)) return
        try {
            val results = coroutineScope {
                sources.map { source -> async(Dispatchers.IO) { source to runCatching { fetch(source) } } }.awaitAll()
            }
            val failed = results.filter { it.second.isFailure }.map { it.first }
            _error.value = when {
                failed.isEmpty() -> null
                failed.size == sources.size -> "Couldn't reach any feeds. Showing saved stories."
                else -> "Couldn't load " + failed.joinToString { it.title.ifEmpty { it.url } }
            }
            if (failed.size == sources.size && sources.isNotEmpty()) return
            // A feed that failed this time keeps the stories it had.
            val kept = _articles.value.filter { old -> failed.any { it.url == old.feedUrl } }
            val pulled = (results.flatMap { it.second.getOrDefault(emptyList()) } + kept).distinctBy { it.link }
            val merged = pulled.sortedByDescending { it.time }.take(MAX_ARTICLES)
            _articles.value = merged
            // A read story that has dropped out of its feed can't come back, so there's nothing left to remember it for.
            val links = pulled.mapTo(HashSet()) { it.link }
            if (_read.value.any { it !in links }) saveRead { read -> read.filterTo(LinkedHashSet()) { it in links } }
            fetchedAt = System.currentTimeMillis()
            fetchedSources = sourcesKey
            withContext(Dispatchers.IO) { runCatching { file.writeText(json.encodeToString(Saved(fetchedAt, sourcesKey, merged))) } }
        } finally {
            _refreshing.value = false
        }
    }

    private fun fetch(source: FeedSource): List<Article> {
        val request = Request.Builder().url(source.url).header("User-Agent", "TTLauncher/0.1 (Android; RSS reader)").build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            // Not Xml.newPullParser(): that one resolves namespaces and reports media:thumbnail as plain "thumbnail".
            val parser = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(response.body.byteStream(), null) }
            val feed = RssParser.parse(parser)
            val name = source.title.ifEmpty { feed.title ?: source.url.toHttpUrlOrNull()?.host?.removePrefix("www.") ?: source.url }
            feed.items.map { it.copy(source = name, feedUrl = source.url) }
        }
    }

    private companion object {
        const val STALE_AFTER_MS = 30 * 60 * 1000L
        const val MAX_ARTICLES = 250
        // A backstop only: the list is cleared of whatever has left the feeds at each refresh.
        const val MAX_READ = 1000
    }
}
