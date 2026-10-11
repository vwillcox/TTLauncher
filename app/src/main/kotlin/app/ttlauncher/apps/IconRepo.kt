package app.ttlauncher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import android.util.Xml
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.res.ResourcesCompat
import app.ttlauncher.data.IconShape
import app.ttlauncher.data.IconStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** How icons are drawn right now. Part of the cache key, so changing any of it redraws them. */
data class IconTheme(val shape: IconShape, val style: IconStyle, val pack: String?, val tintBackground: Int, val tintForeground: Int)

class IconPack(val packageName: String, val label: String)

/**
 * Draws each app icon once at the size it's shown and keeps the bitmap, so scrolling the drawer
 * never touches the package manager.
 */
class IconRepo(private val context: Context) {
    private val cache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4 / 1024
    }
    private val packs = ConcurrentHashMap<String, LoadedPack>()

    private fun cacheKey(app: AppEntry, theme: IconTheme, px: Int) = "${app.key}|$px|$theme"

    fun cached(app: AppEntry, theme: IconTheme, px: Int): ImageBitmap? = cache.get(cacheKey(app, theme, px))

    suspend fun load(app: AppEntry, theme: IconTheme, px: Int): ImageBitmap {
        cached(app, theme, px)?.let { return it }
        return withContext(Dispatchers.Default) { render(app, theme, px) }.also { cache.put(cacheKey(app, theme, px), it) }
    }

    fun invalidate(packageName: String) {
        cache.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach(cache::remove)
        packs.remove(packageName)
    }

    /** Installed icon packs in the ADW/Nova format, which is what nearly every pack on the Play Store ships. */
    fun installedPacks(): List<IconPack> {
        val pm = context.packageManager
        return listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME")
            .flatMap { pm.queryIntentActivities(Intent(it), 0) }
            .distinctBy { it.activityInfo.packageName }
            .map { IconPack(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    private fun render(app: AppEntry, theme: IconTheme, px: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // A pack's icons already have the pack's own shape.
        theme.pack?.let(::pack)?.drawable(app.component)?.let {
            it.setBounds(0, 0, px, px)
            it.draw(canvas)
            return bitmap.asImageBitmap()
        }
        val icon = runCatching { app.info.getIcon(0) }.getOrNull() ?: context.packageManager.defaultActivityIcon
        // Adaptive layers are drawn half as large again as the icon, and the mask crops the overhang.
        val over = px / 4
        fun Drawable.fill(onto: Canvas) = apply { setBounds(-over, -over, px + over, px + over) }.draw(onto)
        val themed = theme.style == IconStyle.THEMED
        val monochrome = if (themed && icon is AdaptiveIconDrawable && Build.VERSION.SDK_INT >= 33) icon.monochrome else null
        // Apps that ship no monochrome icon get one worked out from the icon they do have.
        val glyph = if (themed && monochrome == null) runCatching { glyphFor(icon, px, over) }.getOrNull() else null
        when {
            monochrome != null -> {
                canvas.drawColor(theme.tintBackground)
                monochrome.mutate().apply { setTint(theme.tintForeground) }.fill(canvas)
            }
            glyph != null -> {
                canvas.drawColor(theme.tintBackground)
                canvas.drawBitmap(glyph, 0f, 0f, Paint().apply { colorFilter = PorterDuffColorFilter(theme.tintForeground, PorterDuff.Mode.SRC_IN) })
                glyph.recycle()
            }
            icon is AdaptiveIconDrawable -> {
                icon.background?.fill(canvas)
                icon.foreground?.fill(canvas)
            }
            else -> {
                canvas.drawColor(0xFFF3F3F6.toInt())
                val inset = (px * 0.16f).toInt()
                icon.setBounds(inset, inset, px - inset, px - inset)
                icon.draw(canvas)
            }
        }
        // Masked through a shader, not clipPath, which leaves a jagged edge.
        val masked = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        Canvas(masked).drawPath(shapePath(theme.shape, px.toFloat()), paint)
        bitmap.recycle()
        return masked.asImageBitmap()
    }

    /** A bitmap whose alpha is the app's glyph, to be filled with the theme's colour, or null if none could be made. */
    private fun glyphFor(icon: Drawable, px: Int, over: Int): Bitmap? {
        fun pixels(draw: (Canvas) -> Unit): IntArray {
            val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            draw(Canvas(bitmap))
            return IntArray(px * px).also { bitmap.getPixels(it, 0, px, 0, 0, px, px); bitmap.recycle() }
        }
        fun Drawable.fill(onto: Canvas) = apply { setBounds(-over, -over, px + over, px + over) }.draw(onto)
        val source = if (icon is AdaptiveIconDrawable) {
            // The foreground alone when it is a shape; otherwise the whole icon, to find the figure against its background.
            val foreground = icon.foreground?.let { layer -> pixels { layer.fill(it) } }
            if (foreground != null && AutoMono.isGlyph(foreground, px)) foreground
            else pixels { icon.background?.fill(it); icon.foreground?.fill(it) }
        } else {
            // Older icons have no layers and no safe zone, so they are drawn a little inside the tile.
            val inset = (px * 0.14f).toInt()
            pixels { icon.setBounds(inset, inset, px - inset, px - inset); icon.draw(it) }
        }
        val mask = AutoMono.mask(source, px) ?: return null
        return Bitmap.createBitmap(IntArray(px * px) { mask[it] shl 24 or 0xFFFFFF }, px, px, Bitmap.Config.ARGB_8888)
    }

    private fun pack(packageName: String): LoadedPack? = packs[packageName] ?: loadPack(packageName)?.also { packs[packageName] = it }

    private fun loadPack(packageName: String): LoadedPack? = runCatching {
        val resources = context.packageManager.getResourcesForApplication(packageName)
        val id = resources.getIdentifier("appfilter", "xml", packageName)
        val parser: XmlPullParser = if (id != 0) resources.getXml(id)
        else Xml.newPullParser().apply { setInput(resources.assets.open("appfilter.xml"), null) }
        val drawables = HashMap<String, String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
            val component = parser.getAttributeValue(null, "component") ?: continue
            drawables[component] = parser.getAttributeValue(null, "drawable") ?: continue
        }
        LoadedPack(resources, packageName, drawables)
    }.getOrNull()

    private class LoadedPack(val resources: Resources, val packageName: String, val drawables: Map<String, String>) {
        fun drawable(component: ComponentName): Drawable? {
            val name = drawables["ComponentInfo{${component.packageName}/${component.className}}"] ?: return null
            val id = resources.getIdentifier(name, "drawable", packageName)
            return if (id == 0) null else runCatching { ResourcesCompat.getDrawable(resources, id, null) }.getOrNull()
        }
    }
}

fun shapePath(shape: IconShape, size: Float): Path = Path().apply {
    val r = size / 2
    when (shape) {
        IconShape.CIRCLE -> addCircle(r, r, r, Path.Direction.CW)
        IconShape.ROUNDED -> addRoundRect(0f, 0f, size, size, size * 0.24f, size * 0.24f, Path.Direction.CW)
        IconShape.TEARDROP -> addRoundRect(0f, 0f, size, size, floatArrayOf(r, r, r, r, size * 0.12f, size * 0.12f, r, r), Path.Direction.CW)
        IconShape.SQUIRCLE -> {
            // A superellipse, |x|^4 + |y|^4 = 1.
            for (step in 0 until 120) {
                val angle = Math.toRadians(step * 3.0)
                val cos = cos(angle).toFloat()
                val sin = sin(angle).toFloat()
                val x = r + r * sign(cos) * sqrt(abs(cos))
                val y = r + r * sign(sin) * sqrt(abs(sin))
                if (step == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }
}
