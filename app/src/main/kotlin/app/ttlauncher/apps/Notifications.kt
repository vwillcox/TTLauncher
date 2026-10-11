package app.ttlauncher.apps

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.UserManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One posted notification, cut down to what a badge needs. */
class Note(
    /** The app's package, plus "#" and the profile's serial number for apps outside the main profile. */
    val owner: String,
    /** The count the app itself gave, or 0 when it gave none. */
    val number: Int = 0,
    /** Music controls, calls in progress and the like, which aren't unread anything. */
    val ongoing: Boolean = false,
    val groupSummary: Boolean = false,
    /** False when the app's badges are switched off in the system settings. */
    val badge: Boolean = true,
)

/** How many unread notifications each app has, by [Note.owner]. Apps with none are left out. */
fun badgeCounts(notes: List<Note>): Map<String, Int> =
    notes.filter { it.badge && !it.ongoing }.groupBy { it.owner }.mapValues { (_, own) ->
        // A group's summary stands for the notifications under it, so it only counts when it is all there is.
        own.filterNot { it.groupSummary }.ifEmpty { own.take(1) }.sumOf { maxOf(it.number, 1) }
    }

/**
 * Counts each app's notifications for the badges on its icon. Android starts this once the user
 * allows notification access, and tells it of every change, so nothing is polled.
 */
class NotificationWatcher : NotificationListenerService() {
    private val users by lazy { getSystemService(UserManager::class.java) }

    override fun onListenerConnected() {
        instance = this
        if (wanted) publish() else runCatching { requestUnbind() }
    }

    override fun onListenerDisconnected() {
        instance = null
        _counts.value = emptyMap()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = publish()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()

    // Switching an app's badges off in the system settings arrives this way.
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = publish()

    private fun publish() {
        if (instance !== this) return
        val me = Process.myUserHandle()
        val rankings = runCatching { currentRanking }.getOrNull()
        val ranking = Ranking()
        val notes = runCatching { activeNotifications }.getOrNull().orEmpty().map { sbn ->
            Note(
                owner = sbn.packageName + if (sbn.user == me) "" else "#" + users.getSerialNumberForUser(sbn.user),
                number = sbn.notification.number,
                ongoing = sbn.isOngoing,
                groupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                badge = rankings?.getRanking(sbn.key, ranking) != true || ranking.canShowBadge(),
            )
        }
        _counts.value = badgeCounts(notes)
    }

    companion object {
        private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())

        /** Unread notifications per app, by [AppEntry.badgeKey]. Empty until notification access is allowed. */
        val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

        private var instance: NotificationWatcher? = null
        private var wanted = true

        /** Lets go of the listener while badges are switched off, and asks for it back when they're on. */
        fun setWanted(context: Context, on: Boolean) {
            wanted = on
            if (on) runCatching { requestRebind(ComponentName(context, NotificationWatcher::class.java)) }
            else instance?.let { runCatching { it.requestUnbind() } }
        }

        fun hasAccess(context: Context): Boolean = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        /** Opens the system's switch for this listener, or the list of all of them where a phone has no such page. */
        fun openAccessSettings(context: Context) {
            val own = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, NotificationWatcher::class.java).flattenToString())
            runCatching { context.startActivity(own.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
    }
}
