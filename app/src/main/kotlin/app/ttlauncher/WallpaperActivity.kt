package app.ttlauncher

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ttlauncher.ui.colorScheme
import app.ttlauncher.ui.resolveSkin
import app.ttlauncher.ui.typography
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.max

/**
 * Sets a picture another app hands over as the wallpaper. It is what "Share" and "Use as" open,
 * and what the launcher's own photo picker passes its choice to.
 */
class WallpaperActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val picture = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: intent.data
        if (picture == null) {
            Toast.makeText(this, "No picture to use", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val store = (application as LauncherApp).store
        setContent {
            val state by store.state.collectAsStateWithLifecycle()
            val skin = resolveSkin(state)
            val scope = rememberCoroutineScope()
            var working by remember { mutableStateOf(false) }
            fun set(cover: Boolean = false, inner: Boolean = false, lock: Boolean = false) {
                working = true
                scope.launch {
                    val done = withContext(Dispatchers.IO) {
                        runCatching {
                            val bitmap = decode(this@WallpaperActivity, picture)
                            if (cover) save(this@WallpaperActivity, bitmap, inner = false)
                            if (inner) save(this@WallpaperActivity, bitmap, inner = true)
                            if (lock) WallpaperManager.getInstance(this@WallpaperActivity).setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                            bitmap.recycle()
                        }.isSuccess
                    }
                    if (done) {
                        val now = System.currentTimeMillis()
                        store.update { it.copy(coverWallpaper = if (cover) now else it.coverWallpaper, innerWallpaper = if (inner) now else it.innerWallpaper) }
                    }
                    Toast.makeText(this@WallpaperActivity, if (done) "Wallpaper set" else "Couldn't use that picture", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            MaterialTheme(colorScheme = skin.colorScheme(), typography = skin.typography()) {
                // A touch outside the card cancels.
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                        .clickable(remember { MutableInteractionSource() }, indication = null, enabled = !working) { finish() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(skin.radius))
                            .clickable(remember { MutableInteractionSource() }, indication = null) { }
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Set as wallpaper", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        AsyncImage(
                            model = picture, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(skin.radius.coerceAtMost(20.dp))),
                        )
                        if (working) LinearProgressIndicator(Modifier.fillMaxWidth()) else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { set(cover = true) }, Modifier.weight(1f)) { Text("Cover screen") }
                                Button(onClick = { set(inner = true) }, Modifier.weight(1f)) { Text("Inner screen") }
                            }
                            FilledTonalButton(onClick = { set(cover = true, inner = true) }, Modifier.fillMaxWidth()) { Text("Both screens") }
                            FilledTonalButton(onClick = { set(lock = true) }, Modifier.fillMaxWidth()) { Text("Lock screen") }
                            TextButton(onClick = { finish() }, Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
                        }
                    }
                }
            }
        }
    }

    companion object {
        // A little over the inner screen's 2160 pixels, and far less than a 50-megapixel original.
        private const val LONGEST_SIDE = 2400

        private fun decode(context: Context, picture: Uri): Bitmap =
            // ImageDecoder turns the picture the right way up from its EXIF data, which BitmapFactory does not.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, picture)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > LONGEST_SIDE) decoder.setTargetSampleSize(ceil(longest / LONGEST_SIDE.toFloat()).toInt())
            }

        /**
         * Where a screen's own wallpaper is kept. The launcher draws it itself, because Android's
         * wallpaper service holds a single picture for the whole device.
         */
        fun file(context: Context, inner: Boolean) = File(context.filesDir, if (inner) "wallpaper_inner.jpg" else "wallpaper_cover.jpg")

        private fun save(context: Context, bitmap: Bitmap, inner: Boolean) {
            file(context, inner).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        }

        fun intent(context: Context, picture: Uri) = Intent(context, WallpaperActivity::class.java).setData(picture)
    }
}
