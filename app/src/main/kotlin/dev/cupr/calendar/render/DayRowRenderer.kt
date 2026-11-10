package dev.cupr.calendar.render

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import androidx.annotation.ColorInt
import androidx.core.graphics.createBitmap
import dev.cupr.calendar.R
import dev.cupr.calendar.model.Palette
import dev.cupr.calendar.render.PhoneMonth.CARD_SIDE
import dev.cupr.calendar.render.TileWeek.BAR
import dev.cupr.calendar.render.TileWeek.BAR_RADIUS
import dev.cupr.calendar.render.TileWeek.CHIP_FONT
import dev.cupr.calendar.render.TileWeek.CHIP_GAP
import dev.cupr.calendar.render.TileWeek.CHIP_LEFT
import dev.cupr.calendar.render.TileWeek.CHIP_MARK
import dev.cupr.calendar.render.TileWeek.CHIP_RADIUS
import dev.cupr.calendar.render.TileWeek.CHIP_RIGHT
import dev.cupr.calendar.render.TileWeek.CHIP_TINT
import dev.cupr.calendar.render.TileWeek.COLUMN_GAP
import dev.cupr.calendar.render.TileWeek.DATE_FONT
import dev.cupr.calendar.render.TileWeek.DAY_COLUMN
import dev.cupr.calendar.render.TileWeek.DOT
import dev.cupr.calendar.render.TileWeek.FONT
import dev.cupr.calendar.render.TileWeek.HEAD_GAP
import dev.cupr.calendar.render.TileWeek.HEAD_LINE
import dev.cupr.calendar.render.TileWeek.LINE
import dev.cupr.calendar.render.TileWeek.LINE_GAP
import dev.cupr.calendar.render.TileWeek.MARK
import dev.cupr.calendar.render.TileWeek.PART_GAP
import dev.cupr.calendar.render.TileWeek.PAST
import dev.cupr.calendar.render.TileWeek.ROW_PAD
import dev.cupr.calendar.render.TileWeek.TIME
import dev.cupr.calendar.render.TileWeek.TODAY_FADE
import dev.cupr.calendar.render.TileWeek.TODAY_LINE
import dev.cupr.calendar.render.TileWeek.TODAY_RADIUS
import dev.cupr.calendar.render.TileWeek.TODAY_REACH
import dev.cupr.calendar.render.TileWeek.WEEKDAY_FONT
import dev.cupr.calendar.render.TileWeek.WEEKDAY_SPACING
import dev.cupr.calendar.week.Addition
import dev.cupr.calendar.week.DayEntry
import dev.cupr.calendar.week.WeekDay
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** The week rows' visible texts, from the app's string resources (D25). */
data class WeekTexts(
    /** The weekdays' short names, Monday to Sunday. */
    val weekdays: List<String>,
    val today: String,
    /** An empty day's line. */
    val free: String,
    val holiday: String,
    /** "wird %1$d" */
    val turns: String,
    /** "bis %1$s" */
    val until: String,
) {
    init {
        require(weekdays.size == WEEKDAYS) { "The week needs $WEEKDAYS weekday names, not ${weekdays.size}" }
    }

    fun addition(addition: Addition): String =
        when (addition) {
            Addition.Holiday -> holiday
            is Addition.Turns -> turns.format(Locale.GERMAN, addition.age)
            is Addition.Until -> until.format(Locale.GERMAN, addition.end)
        }

    companion object {
        private const val WEEKDAYS = 7

        fun of(resources: Resources) =
            WeekTexts(
                weekdays = resources.getStringArray(R.array.weekday_heads).toList(),
                today = resources.getString(R.string.week_today),
                free = resources.getString(R.string.week_free),
                holiday = resources.getString(R.string.week_holiday),
                turns = resources.getString(R.string.week_turns),
                until = resources.getString(R.string.week_until),
            )
    }
}

/**
 * Draws one day of the week's rows ("Woche") as a bitmap, after the
 * dashboard's tile ([TileWeek]): its divider, the weekday and date at the
 * left, its entries at the right. The bitmap spans the card inside its
 * border; the row stands inside the card's side padding, and only today's
 * tint reaches into the padding at the left. It is transparent where the card
 * shows.
 */
class DayRowRenderer(
    private val fonts: Fonts,
    private val icons: Icons,
    private val drawings: Drawings,
    private val texts: WeekTexts,
) {
    fun render(
        day: WeekDay,
        widthPx: Int,
        scale: Scale,
        palette: Palette,
    ): Bitmap {
        val heightPx = ceil(scale.hairline + scale.px(heightDp(day))).toInt()
        val bitmap = createBitmap(widthPx, heightPx)
        bitmap.density = scale.dpi
        Row(Canvas(bitmap), scale, palette, widthPx.toFloat(), heightPx.toFloat()).draw(day)
        return bitmap
    }

    /** The row's height below its divider: its padding, and the taller of its date and its lines. */
    fun heightDp(day: WeekDay): Float {
        val head = if (day.today) 2 * TODAY_LINE else HEAD_LINE
        val count = lines(day)
        return 2 * ROW_PAD + max(head, count * LINE + (count - 1) * LINE_GAP)
    }

    /**
     * Where each of the day's entries is drawn, in the bitmap's pixels, in
     * the order of [WeekDay.entries]: its line, from the entries' column to
     * the row's right edge, and down to the next line.
     */
    fun entryAreas(
        day: WeekDay,
        widthPx: Int,
        scale: Scale,
    ): List<RectF> {
        val left = scale.px(CARD_SIDE + DAY_COLUMN + COLUMN_GAP)
        val right = widthPx - scale.px(CARD_SIDE)
        val first = if (day.running.isNotEmpty()) 1 else 0
        return day.entries.indices.map { index ->
            val top = scale.hairline + scale.px(ROW_PAD + (first + index) * (LINE + LINE_GAP))
            RectF(left, top, right, top + scale.px(LINE + LINE_GAP))
        }
    }

    /** Its lines: the running titles' line above entries, each entry, or the empty day's one. */
    private fun lines(day: WeekDay): Int =
        if (day.entries.isEmpty()) 1 else day.entries.size + if (day.running.isNotEmpty()) 1 else 0

    private inner class Row(
        val canvas: Canvas,
        val scale: Scale,
        val palette: Palette,
        val width: Float,
        val height: Float,
    ) {
        private val left = scale.px(CARD_SIDE)
        private val right = width - scale.px(CARD_SIDE)
        private val top = scale.hairline + scale.px(ROW_PAD)

        fun draw(day: WeekDay) {
            val saved = if (day.past) canvas.saveLayerAlpha(null, opacityAlpha(PAST)) else canvas.save()
            canvas.fill(left, 0f, right, scale.hairline, palette.divider)
            if (day.today) tint()
            head(day)
            val x = left + scale.px(DAY_COLUMN + COLUMN_GAP)
            var y = top
            val running = day.running.joinToString(SEPARATOR)
            if (day.entries.isEmpty()) {
                quiet(if (running.isEmpty()) texts.free else texts.free + SEPARATOR + running, x, y)
            } else if (running.isNotEmpty()) {
                quiet(running, x, y)
                y += scale.px(LINE + LINE_GAP)
            }
            for (entry in day.entries) {
                line(entry, x, y)
                y += scale.px(LINE + LINE_GAP)
            }
            canvas.restoreToCount(saved)
        }

        /** Today's tint: from nothing at its left, reaching into the card's padding, to full at the row and out again. */
        private fun tint() {
            val from = left - scale.px(TODAY_REACH)
            val soft = palette.accentSoft
            val clear = soft.withOpacity(0f)
            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader =
                        LinearGradient(
                            from,
                            0f,
                            right,
                            0f,
                            intArrayOf(clear, soft, clear),
                            floatArrayOf(0f, scale.px(TODAY_REACH) / (right - from), TODAY_FADE),
                            Shader.TileMode.CLAMP,
                        )
                }
            val radius = scale.px(TODAY_RADIUS)
            canvas.drawRoundRect(RectF(from, scale.hairline, right, height), radius, radius, paint)
        }

        /** The weekday and the date beside it on one baseline; today "Heute" above its date, both in the accent. */
        private fun head(day: WeekDay) {
            val accentOr = { color: Int -> if (day.today) palette.accent else color }
            val weekday =
                fonts.paint(scale, WEEKDAY_FONT, FontWeight.BOLD, accentOr(palette.tertiaryText)).apply {
                    letterSpacing = WEEKDAY_SPACING
                }
            val dateColor =
                when {
                    day.holiday -> palette.holiday
                    day.today -> palette.accent
                    day.weekend -> palette.secondaryText
                    else -> palette.text
                }
            val date =
                fonts.paint(scale, DATE_FONT, FontWeight.SEMI_BOLD, dateColor).apply {
                    fontFeatureSettings =
                        "tnum"
                }
            val number = "${day.date.dayOfMonth}."
            if (day.today) {
                val line = scale.px(TODAY_LINE)
                canvas.drawText(texts.today.uppercase(Locale.GERMAN), left, weekday.baselineIn(top, line), weekday)
                canvas.drawText(number, left, date.baselineIn(top + line, line), date)
            } else {
                val name = texts.weekdays[day.date.dayOfWeek.value - 1].uppercase(Locale.GERMAN)
                val baseline = date.baselineIn(top, scale.px(HEAD_LINE))
                canvas.drawText(name, left, baseline, weekday)
                canvas.drawText(number, left + weekday.measureText(name) + scale.px(HEAD_GAP), baseline, date)
            }
        }

        /** A line of muted text: the empty day's "frei", the running titles. */
        private fun quiet(
            text: String,
            x: Float,
            y: Float,
        ) {
            val paint = fonts.paint(scale, FONT, FontWeight.REGULAR, palette.tertiaryText)
            canvas.drawCut(text, x, paint.baselineIn(y, scale.px(LINE)), right - x, paint)
        }

        /** An entry's line: the bar in its colour, then a timed entry's time, mark and title, or any other's chip. */
        private fun line(
            day: DayEntry,
            x: Float,
            y: Float,
        ) {
            val color = palette.colorOf(day.entry)
            val lineHeight = scale.px(LINE)
            val bar = scale.px(BAR_RADIUS)
            canvas.drawRoundRect(RectF(x, y, x + scale.px(BAR), y + lineHeight), bar, bar, solid(color))
            val start = x + scale.px(BAR + PART_GAP)
            if (day.time != null) timed(day, day.time, color, start, y) else chip(day, color, start, y)
        }

        private fun timed(
            day: DayEntry,
            time: String,
            @ColorInt color: Int,
            x: Float,
            y: Float,
        ) {
            val lineHeight = scale.px(LINE)
            val timePaint =
                fonts
                    .paint(
                        scale,
                        FONT,
                        FontWeight.REGULAR,
                        palette.secondaryText,
                    ).apply { fontFeatureSettings = "tnum" }
            canvas.drawCut(time, x, timePaint.baselineIn(y, lineHeight), scale.px(TIME), timePaint)
            val markX = x + scale.px(TIME + PART_GAP)
            val mark = scale.px(MARK)
            canvas.drawMark(day.entry, color, markX, y + (lineHeight - mark) / 2, mark, scale.px(DOT), icons, drawings)
            val textX = markX + mark + scale.px(PART_GAP)
            val title = fonts.paint(scale, FONT, FontWeight.MEDIUM, palette.text)
            title(day, textX, title.baselineIn(y, lineHeight), right - textX, title, FONT)
        }

        /** An all-day chip, as wide as its content up to the row's edge: tinted, its mark if it has one, its title. */
        private fun chip(
            day: DayEntry,
            @ColorInt color: Int,
            x: Float,
            y: Float,
        ) {
            val lineHeight = scale.px(LINE)
            val marked = day.entry.drawing != null || day.entry.icon != null
            val markRoom = if (marked) scale.px(CHIP_MARK + CHIP_GAP) else 0f
            val padding = scale.px(CHIP_LEFT + CHIP_RIGHT)
            val room = right - x - padding - markRoom
            val title = fonts.paint(scale, CHIP_FONT, FontWeight.SEMI_BOLD, palette.text)
            val textWidth = min(titleWidth(day, title, CHIP_FONT), max(room, 0f))
            val radius = scale.px(CHIP_RADIUS)
            val chip = RectF(x, y, x + padding + markRoom + textWidth, y + lineHeight)
            canvas.drawRoundRect(chip, radius, radius, solid(color.withOpacity(CHIP_TINT)))
            val innerX = x + scale.px(CHIP_LEFT)
            if (marked) {
                val mark = scale.px(CHIP_MARK)
                canvas.drawMark(
                    day.entry,
                    color,
                    innerX,
                    y + (lineHeight - mark) / 2,
                    mark,
                    scale.px(DOT),
                    icons,
                    drawings,
                )
            }
            title(day, innerX + markRoom, title.baselineIn(y, lineHeight), room, title, CHIP_FONT)
        }

        /** The title and its addition, as wide as drawn by [title]. */
        private fun titleWidth(
            day: DayEntry,
            title: TextPaint,
            size: Float,
        ): Float {
            val addition = day.addition ?: return title.measureText(day.entry.title)
            val after = additionPaint(addition, size)
            return title.measureText(day.entry.title + " ") + after.measureText(texts.addition(addition))
        }

        /**
         * The title and, after a space, its addition, cut with "…" at maxWidth
         * as one text would be: the addition first, then the title.
         */
        private fun title(
            day: DayEntry,
            x: Float,
            baseline: Float,
            maxWidth: Float,
            title: TextPaint,
            size: Float,
        ) {
            val addition = day.addition
            val head = if (addition == null) day.entry.title else day.entry.title + " "
            val headWidth = title.measureText(head)
            canvas.drawCut(head, x, baseline, maxWidth, title)
            if (addition == null || headWidth >= maxWidth) return
            canvas.drawCut(
                texts.addition(addition),
                x + headWidth,
                baseline,
                maxWidth - headWidth,
                additionPaint(addition, size),
            )
        }

        /** An addition's paint: a public holiday's in the holiday red, any other muted. */
        private fun additionPaint(
            addition: Addition,
            size: Float,
        ): TextPaint =
            if (addition == Addition.Holiday) {
                fonts.paint(scale, size, FontWeight.SEMI_BOLD, palette.holiday)
            } else {
                fonts.paint(scale, size, FontWeight.REGULAR, palette.secondaryText)
            }

        private fun solid(
            @ColorInt color: Int,
        ) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    }

    private companion object {
        const val SEPARATOR = " · "
    }
}
