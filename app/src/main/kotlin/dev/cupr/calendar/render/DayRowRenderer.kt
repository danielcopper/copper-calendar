package dev.cupr.calendar.render

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import androidx.core.graphics.createBitmap
import dev.cupr.calendar.R
import dev.cupr.calendar.days.DAYS_IN_WEEK
import dev.cupr.calendar.model.Palette
import dev.cupr.calendar.render.PhoneMonth.CARD_SIDE
import dev.cupr.calendar.render.TileWeek.ALL_DAY_TINT
import dev.cupr.calendar.render.TileWeek.BAR
import dev.cupr.calendar.render.TileWeek.BAR_RADIUS
import dev.cupr.calendar.render.TileWeek.COLUMN_GAP
import dev.cupr.calendar.render.TileWeek.DATE_FONT
import dev.cupr.calendar.render.TileWeek.DAY_COLUMN
import dev.cupr.calendar.render.TileWeek.DOT
import dev.cupr.calendar.render.TileWeek.ENTRY_END
import dev.cupr.calendar.render.TileWeek.ENTRY_PAD
import dev.cupr.calendar.render.TileWeek.ENTRY_RADIUS
import dev.cupr.calendar.render.TileWeek.FONT
import dev.cupr.calendar.render.TileWeek.HEAD_GAP
import dev.cupr.calendar.render.TileWeek.HEAD_LINE
import dev.cupr.calendar.render.TileWeek.LINE
import dev.cupr.calendar.render.TileWeek.LINE_GAP
import dev.cupr.calendar.render.TileWeek.MARK
import dev.cupr.calendar.render.TileWeek.OVER
import dev.cupr.calendar.render.TileWeek.PART_GAP
import dev.cupr.calendar.render.TileWeek.PAST
import dev.cupr.calendar.render.TileWeek.ROW_PAD
import dev.cupr.calendar.render.TileWeek.TIMED_TINT
import dev.cupr.calendar.render.TileWeek.TIME_FONT
import dev.cupr.calendar.render.TileWeek.TIME_GAP
import dev.cupr.calendar.render.TileWeek.TODAY_FADE
import dev.cupr.calendar.render.TileWeek.TODAY_LINE
import dev.cupr.calendar.render.TileWeek.TODAY_RADIUS
import dev.cupr.calendar.render.TileWeek.TODAY_REACH
import dev.cupr.calendar.render.TileWeek.WEEKDAY_FONT
import dev.cupr.calendar.render.TileWeek.WEEKDAY_SPACING
import dev.cupr.calendar.week.Addition
import dev.cupr.calendar.week.DayEntry
import dev.cupr.calendar.week.End
import dev.cupr.calendar.week.RowTime
import dev.cupr.calendar.week.WeekDay
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/** The week rows' visible texts, from the app's string resources (D25). */
class WeekTexts(
    /** The weekdays' short names, Monday to Sunday. */
    val weekdays: List<String>,
    val today: String,
    /** An empty day's line. */
    val free: String,
    val holiday: String,
    /** "wird %1$d" */
    val turns: String,
    /** "ab %1$s" */
    val from: String,
    /** "Tag 2 von 3": a day of an entry's days, and their count. */
    val dayOf: (Int, Int) -> String,
    /** "bis %1$s" */
    val until: String,
    /** "+3 weitere": the line counting the entries a full day leaves out. */
    val more: (Int) -> String,
) {
    init {
        require(weekdays.size == DAYS_IN_WEEK) { "The week needs $DAYS_IN_WEEK weekday names, not ${weekdays.size}" }
    }

    fun addition(addition: Addition): String =
        when (addition) {
            Addition.Holiday -> holiday
            is Addition.Turns -> turns.format(Locale.GERMAN, addition.age)
        }

    /** What a row says at its right end: "12:30–13:30", "ab 09:00", "Tag 2 von 3", "bis Di, 18:00", "08:00 – Di". */
    fun time(time: RowTime): String =
        when (time) {
            is RowTime.Within -> if (time.end == null) time.start else "${time.start}–${time.end}"
            is RowTime.From -> from.format(Locale.GERMAN, time.start)
            is RowTime.DayOf -> dayOf(time.day, time.days)
            is RowTime.Until -> until.format(Locale.GERMAN, end(time.end))
            is RowTime.FromTo -> "${time.start} – ${end(time.end)}"
        }

    /** An end as a row names it: "18:00" on its own day; "Di", "Di, 18:00" or "31.12." on a later one. */
    private fun end(end: End): String =
        when (end) {
            is End.At -> {
                end.time
            }

            is End.Later -> {
                val day = if (end.weekday) weekdays[end.day.dayOfWeek.value - 1] else end.day.format(SHORT_DATE)
                if (end.time == null) day else "$day, ${end.time}"
            }
        }

    /**
     * An entry's description for accessibility, worded as its row reads:
     * its title, addition and time, or with timeFirst its time first, as a
     * stacked timed row has it on its line above the title.
     */
    fun describe(
        entry: DayEntry,
        timeFirst: Boolean = false,
    ): String {
        val time = entry.time?.let(::time)
        val title = listOfNotNull(entry.entry.title, entry.addition?.let(::addition))
        return (if (timeFirst) listOfNotNull(time) + title else title + listOfNotNull(time)).joinToString(" ")
    }

    companion object {
        /** A date further on than a weekday names: "31.12." */
        private val SHORT_DATE = DateTimeFormatter.ofPattern("d.M.")

        fun of(resources: Resources) =
            WeekTexts(
                weekdays = resources.getStringArray(R.array.weekday_heads).toList(),
                today = resources.getString(R.string.week_today),
                free = resources.getString(R.string.week_free),
                holiday = resources.getString(R.string.week_holiday),
                turns = resources.getString(R.string.week_turns),
                from = resources.getString(R.string.week_from),
                dayOf = { day, days -> resources.getQuantityString(R.plurals.week_day_of, days, day, days) },
                until = resources.getString(R.string.week_until),
                more = { count -> resources.getQuantityString(R.plurals.week_more, count, count) },
            )
    }
}

/**
 * Draws one day of the week's rows ("Woche") as a bitmap, after the
 * dashboard's tile ([TileWeek]): its divider, the weekday and date at the
 * left, its entries' rows at the right (D61). The bitmap spans the card
 * inside its border; the row stands inside the card's side padding, and only
 * today's tint reaches into the padding at the left. It is transparent where
 * the card shows.
 *
 * Each entry's row spans the width from the entries' column to the row's
 * right edge, tinted in its colour, with its bar at the left: a timed row
 * its mark, title and, quietly at its right end, its time; an all-day row
 * its drawing or icon, else room for one, its title and addition.
 *
 * A [stacked] row, for a widget narrower than 4 cells ([stacksAt]), puts the
 * weekday above the date in a narrower column, today's too, which keeps its
 * tint and accent instead of "Heute"; a timed entry's row holds two lines,
 * its time above its mark and title; all-day rows and "frei" keep their one
 * line (D39).
 */
class DayRowRenderer internal constructor(
    private val fonts: Fonts,
    icons: Icons,
    drawings: Drawings,
    private val texts: WeekTexts,
    private val stacked: Boolean = false,
) {
    private val marks = MarkAssets(icons, drawings)
    private val column = if (stacked) STACKED_COLUMN else DAY_COLUMN

    fun render(
        day: WeekDay,
        widthPx: Int,
        scale: Scale,
        palette: Palette,
    ): Bitmap {
        val heightPx = heightPx(day, scale)
        val bitmap = createBitmap(widthPx, heightPx)
        bitmap.density = scale.dpi
        Row(Canvas(bitmap), scale, palette, widthPx.toFloat(), heightPx.toFloat()).draw(day, Lines(day))
        return bitmap
    }

    /** The row's height below its divider: its padding, and the taller of its date and its lines. */
    fun heightDp(day: WeekDay): Float {
        val head = if (day.today || stacked) 2 * TODAY_LINE else HEAD_LINE
        return 2 * ROW_PAD + max(head, Lines(day).heightDp)
    }

    /** The drawn row's height in pixels, its divider included. */
    fun heightPx(
        day: WeekDay,
        scale: Scale,
    ): Int = scale.rowPx(heightDp(day))

    /**
     * Where each entry the day shows ([WeekDay.shown]) is drawn, in the
     * bitmap's pixels and in that order: its row, from the entries' column
     * to the row's right edge, and down to the next one.
     */
    fun entryAreas(
        day: WeekDay,
        widthPx: Int,
        scale: Scale,
    ): List<RectF> {
        val left = scale.px(CARD_SIDE + column + COLUMN_GAP)
        val right = widthPx - scale.px(CARD_SIDE)
        val lines = Lines(day)
        return day.shown.zip(lines.entryTops) { entry, top ->
            RectF(
                left,
                scale.hairline + scale.px(ROW_PAD + top),
                right,
                scale.hairline + scale.px(ROW_PAD + top + entryDp(entry) + LINE_GAP),
            )
        }
    }

    /** An entry's description for accessibility, worded as its row reads. */
    fun describe(entry: DayEntry): String = texts.describe(entry, timeFirst = stacked && entry.timed)

    /** An entry's row: its line in its padding, a stacked timed one its time's line above its title's. */
    private fun entryDp(entry: DayEntry): Float = 2 * ENTRY_PAD + if (stacked && entry.timed) TIME_LINE + LINE else LINE

    /**
     * Where a row's lines stand, in dp below its top padding, one gap apart:
     * "frei" for an empty day, each entry's row shown, and "+N weitere" for
     * those left out. Drawing, height and tap areas all read it.
     */
    private inner class Lines(
        day: WeekDay,
    ) {
        val free: Boolean = day.entries.isEmpty()
        val entryTops: List<Float>
        val moreTop: Float?
        val heightDp: Float

        init {
            var y = 0f

            fun next(height: Float): Float = y.also { y += height + LINE_GAP }

            if (free) next(LINE)
            entryTops = day.shown.map { next(entryDp(it)) }
            moreTop = if (day.more > 0) next(LINE) else null
            heightDp = y - LINE_GAP
        }
    }

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

        fun draw(
            day: WeekDay,
            lines: Lines,
        ) {
            val saved = if (day.past) canvas.saveLayerAlpha(null, opacityAlpha(PAST)) else canvas.save()
            canvas.fill(left, 0f, right, scale.hairline, palette.divider)
            if (day.today) tint()
            head(day)
            val x = left + scale.px(column + COLUMN_GAP)
            if (lines.free) quiet(texts.free, x, top)
            day.shown.zip(lines.entryTops) { entry, y -> entry(entry, x, top + scale.px(y)) }
            lines.moreTop?.let { quiet(texts.more(day.more), x, top + scale.px(it)) }
            canvas.restoreToCount(saved)
        }

        /** Today's tint: clear at its left in the card's padding, full where the row begins, clear again further on. */
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

        /**
         * The weekday and the date beside it on one baseline, or stacked above
         * it; today "Heute" above its date, both in the accent, and stacked its
         * weekday in the accent.
         */
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
            val name = if (day.today && !stacked) texts.today else texts.weekdays[day.date.dayOfWeek.value - 1]
            val upper = name.uppercase(Locale.GERMAN)
            if (day.today || stacked) {
                val line = scale.px(TODAY_LINE)
                canvas.drawText(upper, left, weekday.baselineIn(top, line), weekday)
                canvas.drawText(number, left, date.baselineIn(top + line, line), date)
            } else {
                val baseline = date.baselineIn(top, scale.px(HEAD_LINE))
                canvas.drawText(upper, left, baseline, weekday)
                canvas.drawText(number, left + weekday.measureText(upper) + scale.px(HEAD_GAP), baseline, date)
            }
        }

        /** A line of muted text: the empty day's "frei", "+N weitere". */
        private fun quiet(
            text: String,
            x: Float,
            y: Float,
        ) {
            val paint = fonts.paint(scale, FONT, FontWeight.REGULAR, palette.tertiaryText)
            canvas.drawCut(text, x, paint.baselineIn(y, scale.px(LINE)), right - x, paint)
        }

        /**
         * An entry's row from x to the row's right edge, its top at y: tinted
         * in its colour, its bar, its mark, title and time, the whole row
         * dimmed when it is over. A stacked timed row has its time on a line
         * of its own above its mark and title.
         */
        private fun entry(
            row: DayEntry,
            x: Float,
            y: Float,
        ) {
            val color = palette.colorOf(row.entry)
            val field = RectF(x, y, right, y + scale.px(entryDp(row)))
            val saved = if (row.over) canvas.saveLayerAlpha(field, opacityAlpha(OVER)) else canvas.save()
            val radius = scale.px(ENTRY_RADIUS)
            val shape = roundedPath(field, radius, radius)
            canvas.fill(shape, color.withOpacity(if (row.timed) TIMED_TINT else ALL_DAY_TINT))
            canvas.fill(bar(row, field, shape), color)
            val markX = x + scale.px(BAR + PART_GAP)
            val end = field.right - scale.px(ENTRY_END)
            var lineTop = y + scale.px(ENTRY_PAD)
            val time = row.time?.let(texts::time)
            val timePaint =
                fonts.paint(scale, TIME_FONT, FontWeight.REGULAR, palette.secondaryText).apply {
                    fontFeatureSettings = "tnum"
                }
            val textX = markX + scale.px(MARK + PART_GAP)
            var titleEnd = end
            if (time != null && stacked && row.timed) {
                val timeLine = scale.px(TIME_LINE)
                canvas.drawCut(time, markX, timePaint.baselineIn(lineTop, timeLine), end - markX, timePaint)
                lineTop += timeLine
            } else if (time != null) {
                val timeX = max(end - timePaint.measureText(time), textX)
                canvas.drawCut(time, timeX, timePaint.baselineIn(lineTop, scale.px(LINE)), end - timeX, timePaint)
                titleEnd = timeX - scale.px(TIME_GAP + PART_GAP)
            }
            val mark = scale.px(MARK)
            // An all-day row without a drawing or icon leaves the mark's room free, so its title starts where all do.
            if (row.timed || row.entry.drawing != null || row.entry.icon != null) {
                val markY = lineTop + (scale.px(LINE) - mark) / 2
                canvas.drawMark(row.entry, color, markX, markY, mark, scale.px(DOT), marks)
            }
            val title = fonts.paint(scale, FONT, FontWeight.MEDIUM, palette.text)
            title(row, textX, title.baselineIn(lineTop, scale.px(LINE)), titleEnd - textX, title)
            canvas.restoreToCount(saved)
        }

        /**
         * An entry's bar inside its row's rounded shape, which cuts its
         * corners: a timed row's rounded and inside its padding, an all-day
         * row's square and as tall as the row.
         */
        private fun bar(
            row: DayEntry,
            field: RectF,
            shape: Path,
        ): Path {
            val width = scale.px(BAR)
            val bar =
                if (row.timed) {
                    val pad = scale.px(ENTRY_PAD)
                    val radius = scale.px(BAR_RADIUS)
                    roundedPath(
                        RectF(field.left, field.top + pad, field.left + width, field.bottom - pad),
                        radius,
                        radius,
                    )
                } else {
                    roundedPath(RectF(field.left, field.top, field.left + width, field.bottom), 0f, 0f)
                }
            return bar.apply { op(shape, Path.Op.INTERSECT) }
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
                additionPaint(addition),
            )
        }

        /** An addition's paint: a public holiday's in the holiday red, any other muted. */
        private fun additionPaint(addition: Addition): TextPaint =
            if (addition == Addition.Holiday) {
                fonts.paint(scale, FONT, FontWeight.SEMI_BOLD, palette.holiday)
            } else {
                fonts.paint(scale, FONT, FontWeight.REGULAR, palette.secondaryText)
            }
    }

    companion object {
        /**
         * The least width in dp at which a row keeps its one line per entry:
         * Android's least width for 4 cells (D34). Narrower, the rows stack
         * (D39).
         */
        private const val STACKED_BELOW_DP = 250

        /** Whether a widget widthDp wide draws its rows stacked. */
        fun stacksAt(widthDp: Int): Boolean = widthDp < STACKED_BELOW_DP

        /** The rows a widget widthDp wide draws: stacked below [STACKED_BELOW_DP], else one line per entry. */
        fun forWidth(
            fonts: Fonts,
            icons: Icons,
            drawings: Drawings,
            texts: WeekTexts,
            widthDp: Int,
        ) = DayRowRenderer(fonts, icons, drawings, texts, stacksAt(widthDp))

        /** A stacked row's day column, the weekday above the date: as wide as the widest, the date "10.". */
        internal const val STACKED_COLUMN = 19f

        /** A stacked timed entry's time line, above its title's. */
        internal const val TIME_LINE = 16f
    }
}
