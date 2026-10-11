package app.ttlauncher.feed

import app.ttlauncher.data.Article
import app.ttlauncher.data.wordsRegex

class TopicPack(val id: String, val label: String, val keywords: List<String>)

/** Ready-made topics, so muting "football" also catches the stories that never use the word. */
val TopicPacks = listOf(
    TopicPack(
        "football", "Football / soccer",
        listOf(
            "football", "soccer", "footballer", "premier league", "champions league", "europa league", "la liga",
            "bundesliga", "serie a", "ligue 1", "fa cup", "efl", "carabao cup", "fifa", "uefa", "world cup",
            "transfer window", "man utd", "manchester united", "manchester city", "man city", "arsenal",
            "liverpool fc", "chelsea fc", "tottenham", "spurs", "nfl", "super bowl", "quarterback", "touchdown",
        ),
    ),
    TopicPack(
        "sport", "All sport",
        listOf(
            "sport", "cricket", "rugby", "tennis", "golf", "f1", "formula 1", "formula one", "grand prix", "nba",
            "mlb", "nhl", "olympics", "olympic", "boxing", "ufc", "wimbledon", "six nations", "the ashes",
            "darts", "snooker", "cycling", "athletics", "match report", "player ratings",
        ),
    ),
    TopicPack(
        "celebrity", "Celebrity & reality TV",
        listOf("celebrity", "celeb", "kardashian", "love island", "strictly come dancing", "reality tv", "red carpet", "showbiz"),
    ),
    TopicPack(
        "politics", "Politics",
        listOf("politics", "election", "parliament", "westminster", "downing street", "congress", "senate", "white house", "tory", "tories", "labour party"),
    ),
    TopicPack(
        "crypto", "Crypto",
        listOf("crypto", "cryptocurrency", "bitcoin", "ethereum", "nft", "blockchain", "memecoin"),
    ),
)

/** Hides articles that mention any muted word or phrase, as whole words, in any case, singular or plural. */
class TopicFilter(keywords: Collection<String>) {
    private val regex: Regex? = wordsRegex(keywords)

    fun hides(article: Article): Boolean {
        val regex = regex ?: return false
        // The link is included because sections often live there: bbc.co.uk/sport/football/...
        return regex.containsMatchIn(article.title) || regex.containsMatchIn(article.summary) ||
            article.categories.any(regex::containsMatchIn) || regex.containsMatchIn(article.link.replace('-', ' '))
    }

    companion object {
        fun of(topics: Collection<String>, packs: Collection<String>) =
            TopicFilter(topics + TopicPacks.filter { it.id in packs }.flatMap { it.keywords })
    }
}

fun normalizeFeedUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || ' ' in trimmed || '.' !in trimmed) return null
    return when {
        trimmed.startsWith("https://") -> trimmed
        trimmed.startsWith("http://") -> "https://" + trimmed.removePrefix("http://")
        trimmed.startsWith("feed://") -> "https://" + trimmed.removePrefix("feed://")
        "://" in trimmed -> null
        else -> "https://$trimmed"
    }
}
