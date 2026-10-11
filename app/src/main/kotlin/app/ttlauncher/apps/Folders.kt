package app.ttlauncher.apps

import app.ttlauncher.data.Folder
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
