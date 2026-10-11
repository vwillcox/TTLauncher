@file:OptIn(ExperimentalFoundationApi::class)

package app.ttlauncher.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.Rating
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.ttlauncher.apps.NotificationWatcher
import app.ttlauncher.data.WidgetConfig
import kotlinx.coroutines.delay

private const val SPOTIFY = "com.spotify.music"

/** What a player has on, cut down to what the widget shows. */
private class NowPlaying(
    val app: String,
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    /** Whether the track is among the listener's favourites, or null where the player doesn't say. */
    val liked: Boolean?,
    val duration: Long,
    private val position: Long,
    /** When [position] was true, on the clock that keeps counting while the phone sleeps. */
    private val positionAt: Long,
    private val speed: Float,
) {
    /** How far through the track it is now, from 0 to 1. A player only says where it was when something last changed. */
    fun progress(): Float {
        if (duration <= 0) return 0f
        val now = if (playing) position + ((SystemClock.elapsedRealtime() - positionAt) * speed).toLong() else position
        return (now.toFloat() / duration).coerceIn(0f, 1f)
    }
}

/**
 * Follows Spotify's media session, or with [any] whichever player there is, and passes on the
 * transport buttons. Android shows other apps' sessions only to an app with notification access,
 * which is the same access the badges use. It tells this of every change, so nothing is polled.
 */
@Stable
private class Player(private val context: Context, private val any: Boolean) {
    var now by mutableStateOf<NowPlaying?>(null)
        private set

    /** False when Android refused, which is what it does until notification access is given. */
    var allowed by mutableStateOf(true)
        private set

    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val access = ComponentName(context, NotificationWatcher::class.java)
    private var controller: MediaController? = null
    private var art: Pair<android.graphics.Bitmap, ImageBitmap>? = null

    private val changes = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = read()
        override fun onMetadataChanged(metadata: MediaMetadata?) = read()
        override fun onSessionDestroyed() = follow(emptyList())
    }
    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { follow(it.orEmpty()) }

    fun start() {
        allowed = runCatching {
            manager.addOnActiveSessionsChangedListener(sessions, access)
            follow(manager.getActiveSessions(access))
        }.isSuccess
    }

    fun stop() {
        runCatching { manager.removeOnActiveSessionsChangedListener(sessions) }
        follow(emptyList())
    }

    private fun follow(active: List<MediaController>) {
        val next = active.firstOrNull { it.packageName == SPOTIFY }
            ?: if (any) active.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: active.firstOrNull() else null
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(changes)
            controller = next
            next?.registerCallback(changes)
        }
        read()
    }

    private fun read() {
        val from = controller
        val data = from?.metadata
        if (from == null || data == null) {
            now = null
            return
        }
        val state = from.playbackState
        val cover = data.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: data.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: data.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        // The same cover comes round again with every change of state, and is only wrapped the once.
        if (cover !== art?.first) art = cover?.let { it to it.asImageBitmap() }
        now = NowPlaying(
            app = from.packageName,
            title = data.getString(MediaMetadata.METADATA_KEY_TITLE) ?: data.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "",
            artist = data.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: data.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: data.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: "",
            art = art?.second,
            playing = state?.state == PlaybackState.STATE_PLAYING,
            liked = collectionAction(state)?.let(::removes) ?: data.getRating(MediaMetadata.METADATA_KEY_USER_RATING)?.takeIf { it.ratingStyle == Rating.RATING_HEART }?.hasHeart(),
            duration = data.getLong(MediaMetadata.METADATA_KEY_DURATION),
            position = state?.position ?: 0,
            positionAt = state?.lastPositionUpdateTime ?: 0,
            speed = state?.playbackSpeed ?: 1f,
        )
    }

    /** With nothing to control, opens the player instead, which is how there comes to be something. */
    fun toggle() = controller?.transportControls?.let { if (now?.playing == true) it.pause() else it.play() } ?: open()

    fun next() = controller?.transportControls?.skipToNext() ?: open()

    fun previous() = controller?.transportControls?.skipToPrevious() ?: open()

    /**
     * Spotify offers its Liked Songs as a button of its own on the session, the one the notification
     * shows as a plus or a tick: one action to add the track, which is swapped for one to remove it
     * once it's there. Which of the two is on offer is how the widget knows whether the track is liked.
     */
    private fun collectionAction(state: PlaybackState?) = state?.customActions?.firstOrNull { "collection" in it.action.lowercase() || "collection" in it.name.toString().lowercase() }

    private fun removes(action: PlaybackState.CustomAction) = "remove" in action.action.lowercase() || "remove" in action.name.toString().lowercase()

    /** Adds the track to the favourites, or takes it out again. The player answers with a change of state, which is read as any other. */
    fun toggleLiked() {
        val from = controller ?: return open()
        val action = collectionAction(from.playbackState)
        if (action != null) from.transportControls.sendCustomAction(action, action.extras)
        // Players without a button of their own may still take a heart as a rating.
        else if (from.ratingType == Rating.RATING_HEART) from.transportControls.setRating(Rating.newHeartRating(now?.liked != true))
    }

    fun open() {
        if (!allowed) return NotificationWatcher.openAccessSettings(context)
        val intent = context.packageManager.getLaunchIntentForPackage(now?.app ?: SPOTIFY) ?: return
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/**
 * What Spotify is playing, with its cover, how far through it is, and buttons for back, play or
 * pause, on, and keeping the track as a favourite. Short ones are a single row; taller ones put the buttons along the bottom.
 */
@Composable
fun MediaWidget(widget: WidgetConfig, accent: Color, width: Dp, height: Dp, live: Boolean, onEdit: () -> Unit) {
    val skin = LocalSkin.current
    val context = LocalContext.current
    val any = widget.option("any") == true
    val player = if (!live) null else remember(any) { Player(context.applicationContext, any) }.also {
        // Listened to only while the launcher is on screen.
        LifecycleStartEffect(it) {
            it.start()
            onStopOrDispose { it.stop() }
        }
    }
    val now = player?.now
    val playing = now?.playing == true
    val title = when {
        player == null -> "Track title"
        !player.allowed -> "Needs notification access"
        now == null -> if (any) "Nothing playing" else "Spotify"
        else -> now.title
    }
    val artist = when {
        player == null -> "Artist"
        !player.allowed -> "Tap to allow it"
        now == null -> if (any) "Tap to open Spotify" else "Standby. Tap to open"
        else -> now.artist
    }

    // Steps the progress bar on while something plays and the launcher is in front. Only the bar is redrawn.
    var tick by remember { mutableIntStateOf(0) }
    if (playing) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(1000)
                    tick++
                }
            }
        }
    }

    val gap = if (skin.lcars) 3.dp else 6.dp
    val compact = height < 104.dp
    val buttonsHeight = if (compact) height else (height * 0.28f).coerceIn(30.dp, 52.dp)
    val top = if (compact) height else height - buttonsHeight - gap
    // A short, narrow one has room for the cover or the words, and the words say more.
    val showArt = widget.option("art") != false && !(compact && width < 200.dp)
    val artSize = min(top, width * 0.34f)
    val titleSize = (top * 0.2f).coerceIn(13.dp, 22.dp)
    val track = LocalContentColor.current.copy(alpha = 0.18f)
    val press = if (player == null) Modifier else Modifier.combinedClickable(onClickLabel = "Open the player", onLongClick = onEdit, onClick = player::open)

    val buttons = @Composable { modifier: Modifier, all: Boolean ->
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
            val round = if (skin.lcars) 50 else 30
            if (all) TransportButton("Prev", Icons.Rounded.SkipPrevious, 0, accent, RoundedCornerShape(topStartPercent = round, bottomStartPercent = round, topEndPercent = if (skin.lcars) 0 else round, bottomEndPercent = if (skin.lcars) 0 else round), Modifier.weight(1f), onEdit) { player?.previous() }
            TransportButton(
                if (playing) "Pause" else "Play", if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 3, accent,
                if (skin.lcars && all) RoundedCornerShape(0.dp) else RoundedCornerShape(round), Modifier.weight(if (all) 1.4f else 1f), onEdit, lit = playing,
            ) { player?.toggle() }
            val end = RoundedCornerShape(topEndPercent = round, bottomEndPercent = round, topStartPercent = if (skin.lcars) 0 else round, bottomStartPercent = if (skin.lcars) 0 else round)
            // The favourites button goes on the end, where the player has such a thing, and is lit while the track is one.
            val liked = now?.liked
            if (all) TransportButton("Next", Icons.Rounded.SkipNext, 5, accent, if (liked != null && skin.lcars) RoundedCornerShape(0.dp) else end, Modifier.weight(1f), onEdit) { player?.next() }
            if (all && liked != null) TransportButton(
                if (liked) "Liked" else "Like", if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, 1, accent, end, Modifier.weight(1f), onEdit, lit = liked,
            ) { player?.toggleLiked() }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.fillMaxWidth().height(top), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).fillMaxHeight().then(press), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showArt) Box(
                    Modifier.size(artSize).clip(RoundedCornerShape(if (skin.lcars) 0.dp else 10.dp)).background(if (skin.lcars) LcarsPalette[2] else accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    val cover = now?.art
                    if (cover != null) Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Rounded.MusicNote, null, Modifier.size(artSize * 0.5f), tint = if (skin.lcars) Color.Black else accent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title.cased(), color = accent, fontSize = titleSize.sp(), lineHeight = (titleSize * 1.15f).sp(), fontWeight = FontWeight.Bold, maxLines = if (top >= 96.dp) 2 else 1, overflow = TextOverflow.Ellipsis)
                    Text(artist.cased(), color = LocalContentColor.current.copy(alpha = 0.75f), fontSize = (titleSize * 0.8f).sp(), lineHeight = titleSize.sp(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // How far through, as a row of LCARS segments or a plain bar.
                    if (top >= 60.dp) Canvas(Modifier.fillMaxWidth().padding(top = 4.dp).height(if (skin.lcars) 8.dp else 4.dp)) {
                        tick
                        val done = now?.progress() ?: if (player == null) 0.4f else 0f
                        if (!skin.lcars) {
                            val radius = CornerRadius(size.height / 2)
                            drawRoundRect(track, size = size, cornerRadius = radius)
                            drawRoundRect(accent, size = Size(size.width * done, size.height), cornerRadius = radius)
                        } else {
                            val count = (size.width / 9.dp.toPx()).toInt().coerceAtLeast(4)
                            val each = size.width / count
                            for (i in 0 until count) {
                                drawRect(if ((i + 0.5f) / count <= done) LcarsPalette[(i / 4) % LcarsPalette.size] else track, Offset(i * each, 0f), Size(each - 2.dp.toPx(), size.height))
                            }
                        }
                    }
                }
            }
            // A short widget has its buttons on the end of the one row, and only the one where it's narrow too.
            if (compact) buttons(Modifier.width(if (width < 280.dp) 52.dp else if (now?.liked != null) 196.dp else 150.dp).fillMaxHeight().padding(vertical = 2.dp), width >= 280.dp)
        }
        if (!compact) buttons(Modifier.fillMaxWidth().height(buttonsHeight), true)
    }
}

/** A transport button: an LCARS block with its name in the corner, or a tinted plate with the usual symbol. */
@Composable
private fun TransportButton(name: String, icon: ImageVector, index: Int, accent: Color, shape: Shape, modifier: Modifier, onHold: () -> Unit, lit: Boolean = false, onClick: () -> Unit) {
    val skin = LocalSkin.current
    Box(
        modifier.fillMaxHeight().clip(shape)
            .background(if (!skin.lcars) accent.copy(alpha = if (lit) 0.4f else 0.24f) else if (lit) LcarsHighlight else LcarsPalette[index % LcarsPalette.size])
            .combinedClickable(onClickLabel = name, onLongClick = onHold, onClick = onClick),
        contentAlignment = if (skin.lcars) Alignment.BottomEnd else Alignment.Center,
    ) {
        if (skin.lcars) Text(
            name.uppercase(), Modifier.padding(start = 4.dp, end = 9.dp, bottom = 2.dp), color = Color.Black,
            fontSize = 12.dp.sp(), lineHeight = 13.dp.sp(), fontWeight = FontWeight.Bold, maxLines = 1,
        ) else Icon(icon, name, tint = LocalContentColor.current)
    }
}

@Composable
private fun Dp.sp() = with(LocalDensity.current) { toSp() }
