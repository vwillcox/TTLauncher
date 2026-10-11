@file:OptIn(ExperimentalFoundationApi::class)

package app.ttlauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ttlauncher.apps.AppEntry

@Composable
fun AppIcon(app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val icons = LocalIcons.current
    val theme = LocalIconTheme.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState(icons.cached(app, theme, px), app.key, theme, px) {
        value = icons.cached(app, theme, px) ?: icons.load(app, theme, px)
    }
    Box(modifier.size(size)) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) }
    }
}

/** Unread notifications per app, by [AppEntry.badgeKey]. Empty while badges are off or not yet allowed. */
val LocalBadges = compositionLocalOf<Map<String, Int>> { emptyMap() }

/** The unread notifications of [apps] together. Two icons of one app share its notifications, which count once. */
fun Map<String, Int>.unread(apps: List<AppEntry>): Int = if (isEmpty()) 0 else apps.map { it.badgeKey }.distinct().sumOf { this[it] ?: 0 }

/** The colour badges are filled with, and the colour of the figures on them. */
@Composable
fun badgeColors(): Pair<Color, Color> =
    if (LocalSkin.current.lcars) LcarsPalette[4] to Color.Black else MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary

/** An unread count for the corner of an icon. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val (fill, figures) = badgeColors()
    Box(
        // Ringed in the backdrop colour so it stands clear of the icon under it.
        modifier.height(18.dp).defaultMinSize(minWidth = 18.dp).background(LocalSkin.current.backdrop, CircleShape).padding(1.5.dp).background(fill, CircleShape)
            .padding(horizontal = 4.dp).semantics { contentDescription = "$count unread" },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else count.toString(), color = figures, fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** A mark with no figure, where there's no room for one. */
@Composable
fun BadgeDot(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(LocalSkin.current.backdrop, CircleShape).padding(1.dp).background(badgeColors().first, CircleShape))
}

@Composable
fun AppTile(app: AppEntry, size: Dp, showLabel: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick, onClickLabel = app.label).padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            AppIcon(app, size)
            val unread = LocalBadges.current[app.badgeKey] ?: 0
            // Kept within the tile, which clips whatever hangs over its edge.
            if (unread > 0) CountBadge(unread, Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-5).dp))
        }
        if (showLabel) Text(
            app.label.cased(), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current,
        )
    }
}
