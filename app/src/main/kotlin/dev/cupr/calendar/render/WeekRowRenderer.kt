package dev.cupr.calendar.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.annotation.ColorInt
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import dev.cupr.calendar.days.DAYS_IN_WEEK
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.model.Palette
import dev.cupr.calendar.month.Bar
import dev.cupr.calendar.month.Day
import dev.cupr.calendar.month.Week
import dev.cupr.calendar.render.PhoneMonth.BAR_BAND
import dev.cupr.calendar.render.PhoneMonth.BAR_FONT
import dev.cupr.calendar.render.PhoneMonth.BAR_HEIGHT
import dev.cupr.calendar.render.PhoneMonth.BAR_INSET
import dev.cupr.calendar.render.PhoneMonth.BAR_OPEN_LEFT
import dev.cupr.calendar.render.PhoneMonth.BAR_SIDE
import dev.cupr.calendar.render.PhoneMonth.BAR_TINT
import dev.cupr.calendar.render.PhoneMonth.CELL_BOTTOM
import dev.cupr.calendar.render.PhoneMonth.CELL_MIN
import dev.cupr.calendar.render.PhoneMonth.CELL_SIDE
import dev.cupr.calendar.render.PhoneMonth.CELL_TOP
import dev.cupr.calendar.render.PhoneMonth.CHIP_BAND
import dev.cupr.calendar.render.PhoneMonth.CHIP_DRAWING_SHIFT
import dev.cupr.calendar.render.PhoneMonth.CHIP_LEFT
import dev.cupr.calendar.render.PhoneMonth.CHIP_RIGHT
import dev.cupr.calendar.render.PhoneMonth.CHIP_TINT
import dev.cupr.calendar.render.PhoneMonth.DOT
import dev.cupr.calendar.render.PhoneMonth.DRAWING
import dev.cupr.calendar.render.PhoneMonth.DRAWINGS_GAP
import dev.cupr.calendar.render.PhoneMonth.ENTRY_GAP
import dev.cupr.calendar.render.PhoneMonth.ENTRY_RADIUS
import dev.cupr.calendar.render.PhoneMonth.FONT
import dev.cupr.calendar.render.PhoneMonth.GAP
import dev.cupr.calendar.render.PhoneMonth.LANE
import dev.cupr.calendar.render.PhoneMonth.LINE
import dev.cupr.calendar.render.PhoneMonth.MARK
import dev.cupr.calendar.render.PhoneMonth.MORE_LEFT
import dev.cupr.calendar.render.PhoneMonth.NUMBER_FONT
import dev.cupr.calendar.render.PhoneMonth.NUMBER_GAP
import dev.cupr.calendar.render.PhoneMonth.NUMBER_HEIGHT
import dev.cupr.calendar.render.PhoneMonth.NUMBER_MIN_WIDTH
import dev.cupr.calendar.render.PhoneMonth.NUMBER_ROW
import dev.cupr.calendar.render.PhoneMonth.NUMBER_SIDE
import dev.cupr.calendar.render.PhoneMonth.OTHER_MONTH
import dev.cupr.calendar.render.PhoneMonth.PAST_ENTRY
import dev.cupr.calendar.render.PhoneMonth.TIMED_SIDE
import dev.cupr.calendar.render.PhoneMonth.WEEKEND_TINT
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/**
 * Draws one week of the month as a bitmap (D8), at phone size
 * ([PhoneMonth]): the week's top divider, seven cells and the bars over
 * them; "Woche quer" draws its seven days as one such week. The bitmap is
 * transparent where the card shows.
 */
class WeekRowRenderer(
    private val fonts: Fonts,
    private val icons: Icons,
    private val drawings: Drawings,
) {
    /** The week at widthPx; blankDp: room left blank below its cells, their columns running on through it. */
    fun render(
        week: Week,
        widthPx: Int,
        scale: Scale,
        palette: Palette,
        blankDp: Float = 0f,
    ): Bitmap {
        val heightPx = heightPx(week, scale, blankDp)
        val bitmap = createBitmap(widthPx, heightPx)
        bitmap.density = scale.dpi
        val canvas = Canvas(bitmap)
        val frame = Frame(canvas, scale, palette, widthPx / DAYS_IN_WEEK.toFloat(), heightPx.toFloat())
        canvas.fill(0f, 0f, widthPx.toFloat(), scale.hairline, palette.divider)
        week.days.forEachIndexed { column, day -> frame.cell(day, column, week.lanes) }
        week.bars.forEach { frame.bar(it) }
        return bitmap
    }

    /**
     * The days of the week drawn at widthPx whose marks beside the number do
     * not all have room, and how many of their marks each leaves out.
     */
    fun marksWithoutRoom(
        week: Week,
        widthPx: Int,
        scale: Scale,
    ): Map<LocalDate, Int> {
        val column = widthPx / DAYS_IN_WEEK.toFloat()
        return week.days
            .mapIndexedNotNull { index, day ->
                val cell = Cell(index, column, scale)
                val none = markPlaces(day, cell.contentLeft, cell.contentRight, scale).count { it.size <= 0f }
                if (none > 0) day.date to none else null
            }.toMap()
    }

    /** The cell in the column at index: its edges, the divider at its left, and where its content runs. */
    private class Cell(
        index: Int,
        column: Float,
        scale: Scale,
    ) {
        val left = index * column
        val right = left + column

        /** The divider at its left, none in the first column. */
        val border = if (index > 0) scale.hairline else 0f
        val contentLeft = left + border + scale.px(CELL_SIDE)
        val contentRight = right - scale.px(CELL_SIDE)
    }

    /** A mark beside a day's number: where it starts and its size, 0 for one with no room. */
    private class MarkPlace(
        val x: Float,
        val size: Float,
    )

    /** The width of a day's number, or of its pill today, at least [NUMBER_MIN_WIDTH]. */
    private fun numberWidth(
        day: Day,
        scale: Scale,
    ): Float {
        val paint = fonts.paint(scale, NUMBER_FONT, numberWeight(day), 0).apply { fontFeatureSettings = "tnum" }
        return max(
            scale.px(NUMBER_MIN_WIDTH),
            paint.measureText(day.date.dayOfMonth.toString()) + 2 * scale.px(NUMBER_SIDE),
        )
    }

    /**
     * Where a day's marks stand beside its number, in its cell's content from
     * x to right: each [DRAWING] at most and smaller where less room is left,
     * so it stays inside its own cell, and of size 0 with no room left.
     */
    private fun markPlaces(
        day: Day,
        x: Float,
        right: Float,
        scale: Scale,
    ): List<MarkPlace> {
        var drawingX = x + numberWidth(day, scale) + scale.px(NUMBER_GAP)
        return day.marks.map {
            val place = MarkPlace(drawingX, max(0f, min(scale.px(DRAWING), right - drawingX)))
            if (place.size > 0f) drawingX += place.size + scale.px(DRAWINGS_GAP)
            place
        }
    }

    /** The drawn row's height in pixels, its divider and blankDp below its cells included. */
    fun heightPx(
        week: Week,
        scale: Scale,
        blankDp: Float = 0f,
    ): Int = scale.rowPx(heightDp(week) + blankDp)

    /** The row's height below its divider: its tallest cell, at least [CELL_MIN]. */
    fun heightDp(week: Week): Float =
        max(
            CELL_MIN,
            week.days.maxOf { day ->
                CELL_TOP + NUMBER_ROW + GAP + week.lanes * LANE + day.lines * (GAP + LINE) + CELL_BOTTOM
            },
        )

    private inner class Frame(
        val canvas: Canvas,
        val scale: Scale,
        val palette: Palette,
        val column: Float,
        val bottom: Float,
    ) {
        private val top = scale.hairline

        fun cell(
            day: Day,
            index: Int,
            lanes: Int,
        ) {
            val cell = Cell(index, column, scale)
            val saved =
                if (day.otherMonth) {
                    canvas.saveLayerAlpha(cell.left, 0f, cell.right, bottom, opacityAlpha(OTHER_MONTH))
                } else {
                    canvas.save()
                }
            if (cell.border > 0f) canvas.fill(cell.left, top, cell.left + cell.border, bottom, palette.divider)
            if (day.weekend) {
                canvas.fill(cell.left + cell.border, top, cell.right, bottom, palette.text.withOpacity(WEEKEND_TINT))
            }
            val x = cell.contentLeft
            val width = cell.contentRight - x
            var y = top + scale.px(CELL_TOP)
            numberRow(day, x, y, cell.contentRight)
            y += scale.px(NUMBER_ROW + GAP + lanes * LANE + GAP)
            for (entry in day.shown) {
                entryLine(entry, day.past, x, y, width)
                y += scale.px(LINE + GAP)
            }
            if (day.more > 0) {
                val paint = fonts.paint(scale, FONT, FontWeight.BOLD, palette.text)
                canvas.drawText("+${day.more}", x + scale.px(MORE_LEFT), paint.baselineIn(y, scale.px(LINE)), paint)
            }
            canvas.restoreToCount(saved)
        }

        /**
         * The number (today in the accent's pill) and the marked entries'
         * marks beside it, placed by [markPlaces]; one with no room left is not
         * drawn ([marksWithoutRoom] names them).
         */
        private fun numberRow(
            day: Day,
            x: Float,
            y: Float,
            right: Float,
        ) {
            val paint = fonts.paint(scale, NUMBER_FONT, numberWeight(day), numberColor(day, palette))
            paint.fontFeatureSettings = "tnum"
            val label = day.date.dayOfMonth.toString()
            val width = numberWidth(day, scale)
            val height = scale.px(NUMBER_HEIGHT)
            val boxTop = y + (scale.px(NUMBER_ROW) - height) / 2
            if (day.today) {
                val pill = RectF(x, boxTop, x + width, boxTop + height)
                canvas.fill(roundedPath(pill, height / 2, height / 2), palette.accent)
            }
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(label, x + width / 2, paint.baselineIn(boxTop, height), paint)
            for ((entry, place) in day.marks.zip(markPlaces(day, x, right, scale))) {
                if (place.size <= 0f) break
                mark(entry, palette.colorOf(entry), place.x, y + (scale.px(NUMBER_ROW) - place.size) / 2, place.size)
            }
        }

        /**
         * An entry's line: an all-day entry as a tinted chip, with its mark if
         * it names a drawing; a timed one with its mark; then its title.
         */
        private fun entryLine(
            entry: Entry,
            past: Boolean,
            x: Float,
            y: Float,
            width: Float,
        ) {
            val saved = if (past) canvas.saveLayerAlpha(null, opacityAlpha(PAST_ENTRY)) else canvas.save()
            val color = palette.colorOf(entry)
            val height = scale.px(LINE)
            val shape = roundedPath(RectF(x, y, x + width, y + height), scale.px(ENTRY_RADIUS), scale.px(ENTRY_RADIUS))
            canvas.clipPath(shape)
            val textX: Float
            val textEnd: Float
            val weight: FontWeight
            if (entry.allDay) {
                canvas.fill(shape, color.withOpacity(CHIP_TINT))
                canvas.fill(insetBand(shape, scale.px(CHIP_BAND)), color)
                if (entry.drawing != null) {
                    val drawingX = x + scale.px(CHIP_LEFT + CHIP_DRAWING_SHIFT)
                    mark(entry, color, drawingX, y + (height - scale.px(MARK)) / 2, scale.px(MARK))
                    textX = drawingX + scale.px(MARK + ENTRY_GAP)
                } else {
                    textX = x + scale.px(CHIP_LEFT)
                }
                textEnd = x + width - scale.px(CHIP_RIGHT)
                weight = FontWeight.MEDIUM
            } else {
                val markX = x + scale.px(TIMED_SIDE)
                mark(entry, color, markX, y + (height - scale.px(MARK)) / 2, scale.px(MARK))
                textX = markX + scale.px(MARK + ENTRY_GAP)
                textEnd = x + width - scale.px(TIMED_SIDE)
                weight = FontWeight.REGULAR
            }
            val paint = fonts.paint(scale, FONT, weight, palette.text)
            canvas.drawCut(entry.title, textX, paint.baselineIn(y, height), textEnd - textX, paint)
            canvas.restoreToCount(saved)
        }

        /** An entry's mark in a size × size box, wherever one stands in a cell ([drawMark]), its dot inside the box. */
        private fun mark(
            entry: Entry,
            @ColorInt color: Int,
            x: Float,
            y: Float,
            size: Float,
        ) {
            canvas.drawMark(entry, color, x, y, size, min(scale.px(DOT), size), icons, drawings)
        }

        /** An entry over several days across the columns it covers, in its lane. */
        fun bar(bar: Bar) {
            val inset = scale.px(BAR_INSET)
            val left = bar.from * column + if (bar.opens) inset else 0f
            val right = (bar.to + 1) * column - if (bar.closes) inset else 0f
            val barTop = top + scale.px(CELL_TOP + NUMBER_ROW + bar.lane * LANE)
            val height = scale.px(BAR_HEIGHT)
            val round = height / 2
            val shape =
                roundedPath(
                    RectF(left, barTop, right, barTop + height),
                    if (bar.opens) round else 0f,
                    if (bar.closes) round else 0f,
                )
            val color = palette.colorOf(bar.entry)
            canvas.fill(shape, color.withOpacity(BAR_TINT))
            if (bar.opens) canvas.fill(insetBand(shape, scale.px(BAR_BAND)), color)
            if (!bar.titled) return
            val paint = fonts.paint(scale, BAR_FONT, FontWeight.SEMI_BOLD, palette.text)
            val textX = left + scale.px(if (bar.opens) BAR_OPEN_LEFT else BAR_SIDE)
            canvas.withClip(shape) {
                drawCut(
                    bar.entry.title,
                    textX,
                    paint.baselineIn(barTop, height),
                    right - scale.px(BAR_SIDE) - textX,
                    paint,
                )
            }
        }
    }
}

/** A day number's colour, the first that applies: today, past, public holiday, weekend, else the text's. */
@ColorInt
internal fun numberColor(
    day: Day,
    palette: Palette,
): Int =
    when {
        day.today -> palette.onAccent
        day.past -> palette.tertiaryText
        day.holiday -> palette.holiday
        day.weekend -> palette.secondaryText
        else -> palette.text
    }

/** A day number's weight: bold for today and a public holiday, else medium. */
internal fun numberWeight(day: Day): FontWeight = if (day.today || day.holiday) FontWeight.BOLD else FontWeight.MEDIUM
