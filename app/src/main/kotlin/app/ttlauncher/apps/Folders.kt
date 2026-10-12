package app.ttlauncher.apps

import app.ttlauncher.data.DefaultFolders
import app.ttlauncher.data.Folder
import app.ttlauncher.data.LauncherState
import app.ttlauncher.data.wordsRegex

/** Where apps go when no folder claims them. Shown as "Other", and only when it has something in it. */
const val OTHER_FOLDER = ""

/** Decides which of the user's folders an app belongs in. */
class FolderSorter(folders: List<Folder>) {
    private val ids = folders.map { it.id }.toSet()
    private val byWords = folders.mapNotNull { folder -> wordsRegex(folder.keywords)?.let { folder.id to it } }
    private val byTopic = buildMap { folders.forEach { folder -> folder.topics.forEach { putIfAbsent(it, folder.id) } } }

    /** The user's own filing first, then a folder's words, then the folder that collects the app's topic. */
    fun folderFor(topic: AppCategory, packageName: String, label: String, override: String?): String {
        if (override != null && override in ids) return override
        val text = packageName.replace('.', ' ').replace('_', ' ') + " " + label
        byWords.firstOrNull { it.second.containsMatchIn(text) }?.let { return it.first }
        return byTopic[topic.name] ?: OTHER_FOLDER
    }
}

/**
 * Brings a saved setup up to date with topics the sorter has learned since it was saved: each gets
 * its default folder, next to the one it follows by default, unless a folder already collects it.
 */
fun LauncherState.withNewTopics(): LauncherState {
    val all = AppCategory.entries.map { it.name }.toSet()
    val fresh = all - seenTopics
    if (fresh.isEmpty()) return this
    // Money had the shopping bag until Shopping took it.
    var result = if ("SHOPPING" in fresh) folders.map { if (it.id == "MONEY" && it.icon == "bag") it.copy(icon = "money") else it } else folders
    DefaultFolders.forEachIndexed { index, folder ->
        val wanted = folder.topics.filter { topic -> topic in fresh && result.none { topic in it.topics } }.toSet()
        if (wanted.isEmpty()) return@forEachIndexed
        result = if (result.any { it.id == folder.id }) {
            result.map { if (it.id == folder.id) it.copy(topics = it.topics + wanted) else it }
        } else {
            // After the nearest default folder before it that the user still has.
            val at = DefaultFolders.take(index).asReversed().firstNotNullOfOrNull { before -> result.indexOfFirst { it.id == before.id }.takeIf { it >= 0 } }?.plus(1) ?: 0
            val added = folder.copy(topics = wanted)
            if (at > 0) result.take(at) + added + result.drop(at) else result + added
        }
    }
    return copy(folders = result, seenTopics = all)
}
