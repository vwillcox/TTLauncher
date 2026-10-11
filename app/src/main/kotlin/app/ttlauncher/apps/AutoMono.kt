package app.ttlauncher.apps

import kotlin.math.sqrt

/**
 * Works out a single-colour glyph for an app that doesn't ship a monochrome icon, so themed icons
 * cover every app. Takes and returns plain pixel arrays, which keeps it testable off the device.
 */
object AutoMono {
    private const val VISIBLE = 40

    private fun alpha(pixel: Int) = pixel ushr 24
    private fun luma(pixel: Int) = ((pixel shr 16 and 0xFF) * 299 + (pixel shr 8 and 0xFF) * 587 + (pixel and 0xFF) * 114) / 1000

    /**
     * True for a shape on transparency, false for a picture that fills its outline. Measured within
     * the shape's own bounding box, so a small solid plate still counts as filled.
     */
    fun isGlyph(pixels: IntArray, width: Int): Boolean {
        var left = width
        var right = -1
        var top = Int.MAX_VALUE
        var bottom = -1
        var visible = 0
        for (i in pixels.indices) {
            if (alpha(pixels[i]) <= VISIBLE) continue
            visible++
            val x = i % width
            val y = i / width
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
        if (visible == 0) return false
        return visible < 0.72f * (right - left + 1) * (bottom - top + 1)
    }

    /** The glyph's opacity for each pixel, 0 to 255, or null when no usable glyph can be found. */
    fun mask(pixels: IntArray, width: Int): IntArray? {
        val visible = pixels.count { alpha(it) > VISIBLE }
        if (visible < pixels.size / 50) return null
        val mask = (if (isGlyph(pixels, width)) shades(pixels) else figure(pixels)) ?: return null
        // Too little left to recognise the app by.
        return mask.takeIf { m -> m.count { it > 128 } >= pixels.size / 60 }
    }

    /** A shape on transparency: its outline, with any light-and-dark detail inside kept as lighter and heavier ink. */
    private fun shades(pixels: IntArray): IntArray {
        val lumas = pixels.filter { alpha(it) > VISIBLE }.map(::luma).sorted()
        val low = lumas[lumas.size * 5 / 100]
        val high = lumas[lumas.size * 95 / 100]
        if (high - low < 40) return IntArray(pixels.size) { alpha(pixels[it]) }
        // Whichever tone there is more of becomes the solid ink, and the other the detail cut into it.
        val mostlyLight = lumas[lumas.size / 2] - low > (high - low) / 2
        return IntArray(pixels.size) { i ->
            val tone = ((luma(pixels[i]) - low).toFloat() / (high - low)).coerceIn(0f, 1f)
            (alpha(pixels[i]) * (0.35f + 0.65f * if (mostlyLight) tone else 1 - tone)).toInt()
        }
    }

    /** A figure on a coloured plate: the plate is the commonest colour, and whatever differs from it is the figure. */
    private fun figure(pixels: IntArray): IntArray? {
        val counts = IntArray(4096)
        val sums = Array(4096) { IntArray(3) }
        for (pixel in pixels) {
            if (alpha(pixel) <= VISIBLE) continue
            val r = pixel shr 16 and 0xFF
            val g = pixel shr 8 and 0xFF
            val b = pixel and 0xFF
            val bin = (r shr 4 shl 8) or (g shr 4 shl 4) or (b shr 4)
            counts[bin]++
            sums[bin][0] += r
            sums[bin][1] += g
            sums[bin][2] += b
        }
        val plate = counts.indices.maxBy { counts[it] }
        val plateColour = IntArray(3) { sums[plate][it] / counts[plate] }
        fun distance(pixel: Int): Float {
            val r = (pixel shr 16 and 0xFF) - plateColour[0]
            val g = (pixel shr 8 and 0xFF) - plateColour[1]
            val b = (pixel and 0xFF) - plateColour[2]
            return sqrt((r * r + g * g + b * b).toFloat())
        }
        val distances = pixels.filter { alpha(it) > VISIBLE }.map(::distance).sorted()
        val furthest = distances[distances.size * 95 / 100]
        // One flat colour: there is no figure to pull out.
        if (furthest < 48f) return null
        return IntArray(pixels.size) { i ->
            val t = ((distance(pixels[i]) / furthest - 0.2f) / 0.4f).coerceIn(0f, 1f)
            (alpha(pixels[i]) * t * t * (3 - 2 * t)).toInt()
        }
    }
}
