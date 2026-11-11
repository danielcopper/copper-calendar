package dev.cupr.calendar.widget

/**
 * Where a widget's list scrolls so today's row stands on top. The only list
 * scroll a widget's views offer, `setScrollPosition`, is a smooth scroll to a
 * position, which ends with a row below the visible ones as the last visible
 * row; so the list scrolls not to today's row but to the last row that still
 * fits below it, and today's row stands on top when the list comes from above.
 */
internal object TodayOnTop {
    /** The row to scroll to, and the summed height in px of today's row through it. */
    data class Target(
        val index: Int,
        val rowsPx: Int,
    )

    /**
     * The last row at or after today whose rows from today's on fit into
     * listPx, by the rows' heights in px; the last row when the rows after
     * today do not fill the list, today's row when it alone is taller than
     * the list. Null when today is no row's index, as -1 from indexOfFirst.
     */
    fun target(
        heightsPx: List<Int>,
        today: Int,
        listPx: Float,
    ): Target? {
        if (today !in heightsPx.indices) return null
        var index = today
        var rowsPx = heightsPx[today]
        while (index + 1 < heightsPx.size && rowsPx + heightsPx[index + 1] <= listPx) {
            index += 1
            rowsPx += heightsPx[index]
        }
        return Target(index, rowsPx)
    }
}
