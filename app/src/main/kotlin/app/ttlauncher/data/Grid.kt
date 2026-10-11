package app.ttlauncher.data

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

const val GRID_GAP_DP = 12f

/** The gap on skins whose frames are meant to sit against each other, with only a black line between. */
const val GRID_TIGHT_GAP_DP = 2f

/**
 * How small and how large a widget may be, in dp. The floor is what its content needs to stay
 * readable; the ceiling stops it floating in empty space. Width may always reach the full row.
 */
data class SizeLimits(val minWidth: Float, val minHeight: Float, val preferredWidth: Float, val preferredHeight: Float, val maxHeight: Float)

fun WidgetConfig.limits(): SizeLimits = when (type) {
    WidgetType.CLOCK -> when (variant) {
        1 -> SizeLimits(120f, 170f, 160f, 220f, 360f)
        2 -> SizeLimits(140f, 150f, 160f, 180f, 360f)
        else -> SizeLimits(125f, 72f, 330f, 130f, 240f)
    }
    WidgetType.DATE -> if (variant == 1) SizeLimits(150f, 72f, 330f, 100f, 180f) else SizeLimits(180f, 52f, 330f, 60f, 110f)
    WidgetType.BATTERY -> if (variant == 1) SizeLimits(130f, 140f, 160f, 170f, 300f) else SizeLimits(150f, 72f, 330f, 80f, 130f)
    WidgetType.CALENDAR -> SizeLimits(230f, 240f, 330f, 300f, 460f)
    WidgetType.SEARCH -> SizeLimits(140f, 52f, 330f, 60f, 90f)
    WidgetType.SYSTEM -> SizeLimits(110f, 60f, 330f, 160f, 640f)
    WidgetType.CASCADE -> SizeLimits(125f, 72f, 330f, 130f, 320f)
    WidgetType.MACRO -> SizeLimits(110f, 60f, 330f, 130f, 400f)
    WidgetType.WEATHER -> SizeLimits(150f, 72f, 330f, 150f, 320f)
    WidgetType.MEDIA -> SizeLimits(180f, 72f, 330f, 150f, 320f)
}

/** One screen's home grid, turning dp into whole cells and back. */
class GridSpec(val columns: Int, val rowDp: Float, val widthDp: Float, val gapDp: Float = GRID_GAP_DP) {
    val cellDp = (widthDp - gapDp * (columns - 1)) / columns

    fun widthOf(cols: Int) = cols * cellDp + (cols - 1) * gapDp
    fun heightOf(rows: Int) = rows * rowDp + (rows - 1) * gapDp

    // n cells span n*unit + (n-1)*gap, so a length of dp needs (dp + gap) / (unit + gap) of them.
    private fun cells(dp: Float, unit: Float) = (dp + gapDp) / (unit + gapDp)

    fun columnRange(limits: SizeLimits): IntRange = ceil(cells(limits.minWidth, cellDp) - 0.01f).toInt().coerceIn(1, columns)..columns

    fun rowRange(limits: SizeLimits): IntRange {
        val min = ceil(cells(limits.minHeight, rowDp) - 0.01f).toInt().coerceAtLeast(1)
        return min..floor(cells(limits.maxHeight, rowDp) + 0.01f).toInt().coerceAtLeast(min)
    }

    fun clamp(size: CellSize, limits: SizeLimits) = CellSize(size.cols.coerceIn(columnRange(limits)), size.rows.coerceIn(rowRange(limits)))

    /** The size a widget gets until the user resizes it. */
    fun defaultSize(limits: SizeLimits) =
        clamp(CellSize(cells(limits.preferredWidth, cellDp).roundToInt(), cells(limits.preferredHeight, rowDp).roundToInt()), limits)
}

class Placed(val column: Int, val row: Int)

/**
 * Lays widgets out in order, each in the first gap it fits, so a small widget can sit beside a
 * tall one instead of starting a new row.
 */
fun packGrid(columns: Int, sizes: List<CellSize>): List<Placed> {
    val taken = ArrayList<BooleanArray>()
    fun free(column: Int, row: Int, size: CellSize): Boolean {
        for (r in row until row + size.rows) {
            val cells = taken.getOrNull(r) ?: continue
            for (c in column until column + size.cols) if (cells[c]) return false
        }
        return true
    }
    return sizes.map { wanted ->
        val size = CellSize(wanted.cols.coerceIn(1, columns), wanted.rows.coerceAtLeast(1))
        var row = 0
        var column: Int
        while (true) {
            column = (0..columns - size.cols).firstOrNull { free(it, row, size) } ?: -1
            if (column >= 0) break
            row++
        }
        while (taken.size < row + size.rows) taken.add(BooleanArray(columns))
        for (r in row until row + size.rows) for (c in column until column + size.cols) taken[r][c] = true
        Placed(column, row)
    }
}

fun LauncherState.layout(inner: Boolean) = if (inner) innerLayout else coverLayout

fun LauncherState.withLayout(inner: Boolean, change: (ScreenLayout) -> ScreenLayout) =
    if (inner) copy(innerLayout = change(innerLayout)) else copy(coverLayout = change(coverLayout))

fun ScreenLayout.withPage(index: Int, change: (WidgetPage) -> WidgetPage) = copy(pages = pages.mapIndexed { i, page -> if (i == index) change(page) else page })

fun ScreenLayout.withWidget(id: String, change: (WidgetConfig) -> WidgetConfig) =
    copy(pages = pages.map { page -> page.copy(widgets = page.widgets.map { if (it.id == id) change(it) else it }) })

fun ScreenLayout.withoutWidget(id: String) = copy(pages = pages.map { page -> page.copy(widgets = page.widgets.filter { it.id != id }) })

fun ScreenLayout.widget(id: String): WidgetConfig? = pages.firstNotNullOfOrNull { page -> page.widgets.firstOrNull { it.id == id } }

fun ScreenLayout.pageOf(id: String): Int = pages.indexOfFirst { page -> page.widgets.any { it.id == id } }

/** Moves a widget to the end of another page. */
fun ScreenLayout.moveWidget(id: String, toPage: Int): ScreenLayout {
    val widget = widget(id) ?: return this
    return withoutWidget(id).withPage(toPage) { it.copy(widgets = it.widgets + widget) }
}

/** How many home pages one screen may have. */
const val MAX_HOME_PAGES = 12

/** How many columns each screen's grid may have. */
fun columnChoices(inner: Boolean) = if (inner) 4..12 else 2..7
