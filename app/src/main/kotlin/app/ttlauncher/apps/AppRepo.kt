package app.ttlauncher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppEntry(
    /** Stable across restarts: package/class, plus the profile for apps outside the main one. */
    val key: String,
    val component: ComponentName,
    val user: UserHandle,
    val label: String,
    /** The auto-sorter's topic for this app. */
    val category: AppCategory,
    /** The id of the drawer folder it ends up in, filled in once the user's folders are applied. */
    val folder: String = OTHER_FOLDER,
    val info: LauncherActivityInfo,
) {
    /** What its notifications are counted under: the package, plus the profile for apps outside the main one. */
    val badgeKey: String get() = component.packageName + if ('#' in key) "#" + key.substringAfterLast('#') else ""
}

/** The installed apps, kept current by the system's own callbacks rather than by polling. */
class AppRepo(private val context: Context, scope: CoroutineScope, private val onPackageChanged: (String) -> Unit) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val reload = Channel<Unit>(Channel.CONFLATED)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    init {
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName)
            override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
            override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
            override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = packageNames.forEach(::changed)
            override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = packageNames.forEach(::changed)
        }, Handler(Looper.getMainLooper()))
        scope.launch(Dispatchers.Default) { for (request in reload) _apps.value = load() }
        reload.trySend(Unit)
    }

    private fun changed(packageName: String) {
        onPackageChanged(packageName)
        reload.trySend(Unit)
    }

    private fun load(): List<AppEntry> {
        val me = Process.myUserHandle()
        return launcherApps.profiles.flatMap { user ->
            // A locked work profile or Secure Folder refuses the query.
            runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList()).map { info ->
                val component = info.componentName
                val label = info.label.toString()
                val app = info.applicationInfo
                AppEntry(
                    key = component.flattenToShortString() + if (user == me) "" else "#" + userManager.getSerialNumberForUser(user),
                    component = component,
                    user = user,
                    label = label,
                    category = Categorizer.categorize(component.packageName, label, app.category, app.flags and ApplicationInfo.FLAG_SYSTEM != 0),
                    info = info,
                )
            }
        }.filter { it.component.packageName != context.packageName }.sortedBy { it.label.lowercase() }
    }

    /** [options] can carry the animation the app arrives with, from [android.app.ActivityOptions]. */
    fun launch(app: AppEntry, options: Bundle? = null) {
        runCatching { launcherApps.startMainActivity(app.component, app.user, null, options) }
            .onFailure { Toast.makeText(context, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show() }
    }

    fun showInfo(app: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(app: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.component.packageName, null))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
