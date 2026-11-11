package dev.cupr.calendar.widget

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.WindowManager

/**
 * The system's limit on a widget's bitmaps, and how the drawn widgets keep
 * within it (D40): they count their bitmaps' bytes before drawing, leave out
 * entries while over it, and warn when they had to.
 */
internal object BitmapLimit {
    /** The bytes per pixel of an ARGB_8888 bitmap, as [Bitmap.getAllocationByteCount] counts them. */
    private const val BYTES_PER_PIXEL = 4

    /** The bytes per pixel of the display that the system allows ([limitBytes]). */
    private const val LIMIT_PER_PIXEL = 6

    /** Each drawn part comes twice, light and dark. */
    private const val MODES = 2

    /**
     * The limit on the bytes of a widget's bitmaps: 6 × the display's width ×
     * its height in pixels, as AOSP's `AppWidgetServiceImpl` computes it
     * (`computeMaximumWidgetBitmapMemory`, from the display's real size) and
     * enforces it (`updateAppWidgetInstanceLocked` throws an
     * IllegalArgumentException above it). The display's size is read from the
     * window manager's maximum window metrics, its full size.
     */
    fun limitBytes(
        context: Context,
        tag: String,
        id: Int,
    ): Long {
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        return limitOf(bounds.width(), bounds.height(), tag, id)
    }

    /**
     * The limit for a display widthPx × heightPx; for a display without a
     * size, none, so nothing is left out, and the log says so.
     */
    fun limitOf(
        widthPx: Int,
        heightPx: Int,
        tag: String,
        id: Int,
    ): Long {
        if (widthPx > 0 && heightPx > 0) return LIMIT_PER_PIXEL.toLong() * widthPx * heightPx
        Log.w(tag, "Widget $id: the display reports no size ($widthPx x $heightPx), so its bitmaps are not limited")
        return Long.MAX_VALUE
    }

    /** A limit for the log: its bytes, or "no limit" for a display without a size ([limitOf]). */
    fun describe(limit: Long): String = if (limit == Long.MAX_VALUE) "no limit" else "$limit"

    /** What [capped] ends at, and how many entries it left out to get there. */
    class Capped<T>(
        val drawn: T,
        val left: Int,
    )

    /**
     * From start, leaves out one entry after another by next while bytesOf
     * counts it over room; next gives null when nothing is left to leave out,
     * and the last one is then as close as it gets.
     */
    fun <T> capped(
        start: T,
        room: Long,
        bytesOf: (T) -> Long,
        next: (T) -> T?,
    ): Capped<T> {
        var drawn = start
        var left = 0
        while (bytesOf(drawn) > room) {
            drawn = next(drawn) ?: break
            left++
        }
        return Capped(drawn, left)
    }

    /**
     * Warns when a widget's drawing, needed bytes in all, passes the limit:
     * how many entries it left out for it, and how far it is still over
     * when it ends at after bytes.
     */
    fun warnOver(
        tag: String,
        id: Int,
        needed: Long,
        limit: Long,
        left: Int,
        after: Long,
    ) {
        if (needed <= limit) return
        val still = if (after > limit) ", still ${after - limit} bytes over" else ""
        Log.w(tag, "Widget $id: its bitmaps take $needed bytes, over the limit of $limit; $left entries left out$still")
    }

    /** The bytes of a bitmap widthPx × heightPx, light and dark. */
    fun bytes(
        widthPx: Int,
        heightPx: Int,
    ): Long = MODES.toLong() * BYTES_PER_PIXEL * widthPx * heightPx
}
