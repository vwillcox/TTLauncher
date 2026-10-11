@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package app.ttlauncher.ui

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ttlauncher.data.Article
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import app.ttlauncher.data.GRID_TIGHT_GAP_DP
import app.ttlauncher.data.WidgetStyle
import coil3.compose.AsyncImage

@Composable
fun FeedPage(
    articles: List<Article>,
    /** How many stories the muted topics removed, shown so the filter is never a mystery. */
    hiddenCount: Int,
    /** How many more are out of sight for having been opened. */
    readCount: Int,
    hasSources: Boolean,
    refreshing: Boolean,
    error: String?,
    showImages: Boolean,
    dim: Float,
    /** Room to leave at the bottom for the page dots, which are drawn over every page. */
    bottomReserve: Dp,
    onRefresh: () -> Unit,
    onRead: (Article) -> Unit,
    onArticleMenu: (Article) -> Unit,
    onSettings: () -> Unit,
) {
    val skin = LocalSkin.current
    val context = LocalContext.current
    CompositionLocalProvider(LocalContentColor provides skin.onSurface) {
        Column(Modifier.fillMaxSize().background(skin.pageScrim(dim)).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // An LCARS text bar: an end cap, the title, then a bar that carries the small print.
                if (skin.lcars) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp, 26.dp).background(LcarsPalette[0], RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)))
                    Text("NEWS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = skin.accent)
                    Box(Modifier.weight(1f).height(26.dp).background(LcarsPalette[2]), contentAlignment = Alignment.BottomEnd) {
                        if (hiddenCount > 0) Text(
                            "$hiddenCount MUTED", Modifier.padding(end = 8.dp, bottom = 2.dp), color = Color.Black,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1,
                        )
                    }
                } else Column(Modifier.weight(1f)) {
                    Text("News".cased(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    if (hiddenCount > 0) Text(
                        "$hiddenCount hidden by muted topics", style = MaterialTheme.typography.labelMedium, color = skin.onSurface.copy(alpha = 0.65f),
                    )
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Refresh") }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Tune, "Feeds and muted topics") }
            }
            val stories = @Composable {
                // On LCARS the frames sit against each other, as they do on the home pages, so their bars run on as one.
                val between = if (skin.lcars) GRID_TIGHT_GAP_DP.dp else 12.dp
                // One column on the cover screen, two on the inner one.
                var lanes by remember { mutableIntStateOf(1) }
                val density = LocalDensity.current
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(300.dp),
                    modifier = Modifier.fillMaxSize().onSizeChanged { lanes = with(density) { ((it.width + between.roundToPx()) / (300.dp + between).roundToPx()).coerceAtLeast(1) } },
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                    verticalItemSpacing = between,
                    horizontalArrangement = Arrangement.spacedBy(between),
                ) {
                    val note = error ?: when {
                        !hasSources -> "No feeds yet. Tap the sliders icon to add an RSS feed."
                        articles.isEmpty() && !refreshing && readCount > 0 -> "You've read everything here. Tap refresh for more."
                        articles.isEmpty() && !refreshing && hiddenCount > 0 -> "Every story is hidden by your muted topics."
                        articles.isEmpty() && !refreshing -> "No stories yet. Tap refresh to try again."
                        else -> null
                    }
                    if (note != null) item(key = "note", span = StaggeredGridItemSpan.FullLine) {
                        Text(note, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = skin.onSurface.copy(alpha = 0.8f))
                    }
                    itemsIndexed(articles, key = { _, article -> article.link }) { index, article ->
                        ArticleCard(
                            article, showImages, down = index % 2 == 1,
                            // In a single column every story but the first and last has another against the corner its bars turn.
                            junction = lanes == 1 && if (index % 2 == 1) index < articles.lastIndex else index > 0,
                            onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onSuccess { onRead(article) }
                            },
                            onLongClick = { onArticleMenu(article) },
                        )
                    }
                }
            }
            // Scrolling and turning the page are the only gestures here. Pulling down does nothing, so a swipe
            // across that dips on the way can't be taken for one; the button above refreshes.
            Box(Modifier.weight(1f)) {
                stories()
                if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(bottomReserve))
        }
    }
}

@Composable
private fun ArticleCard(article: Article, showImage: Boolean, down: Boolean, junction: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val skin = LocalSkin.current
    val age = if (article.time > 0) DateUtils.getRelativeTimeSpanString(article.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString() else null
    // An LCARS frame carries the source and the age in its bar, so they aren't said again underneath.
    val inBar = skin.lcars && skin.widgetStyle != WidgetStyle.BARE
    WidgetFrame(
        if (skin.widgetStyle == WidgetStyle.BARE) WidgetStyle.GLASS else skin.widgetStyle,
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Hide stories like this"),
        label = article.source,
        note = age,
        down = down,
        junction = junction,
    ) {
        Column {
            if (showImage && article.image != null) AsyncImage(
                model = article.image, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(topStart = skin.radius, topEnd = skin.radius)),
            )
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!inBar) Text(article.source + (age?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelMedium, color = skin.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(article.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (article.summary.isNotEmpty()) Text(
                    article.summary, style = MaterialTheme.typography.bodyMedium, color = skin.onSurface.copy(alpha = 0.75f), maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
