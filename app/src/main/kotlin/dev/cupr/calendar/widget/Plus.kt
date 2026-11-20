package dev.cupr.calendar.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import dev.cupr.calendar.R
import dev.cupr.calendar.render.ChromeRenderer
import dev.cupr.calendar.render.Scale
import dev.cupr.calendar.store.PlusCorner
import dev.cupr.calendar.tap.Tap

/**
 * A widget's "+", at the bottom right or the bottom left of its card, as its
 * setup says (D58), or none when its setup says a tap on it opens nothing
 * (D63).
 */
internal object Plus {
    /**
     * Puts "+" of calendar in views in set's corner, drawn light and dark by
     * chrome at the display's scale and kept, opening what set's taps say
     * for a new entry on today. Each bottom corner has its own "+" in the layout
     * (widget_fab.xml); the other one is hidden, so a corner set back takes
     * effect too, and both are when "+" has no tap.
     */
    fun place(
        context: Context,
        views: RemoteViews,
        kept: Kept,
        chrome: ChromeRenderer,
        set: FixedParts,
        calendar: Content.Calendar,
    ) {
        val tap = set.taps.pendingIntent(context, Tap.New(calendar.today))
        val right = set.corner == PlusCorner.BOTTOM_RIGHT
        views.setViewVisibility(R.id.plus_right, if (tap != null && right) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.plus_left, if (tap != null && !right) View.VISIBLE else View.GONE)
        if (tap == null) return
        val scale = Scale(context.resources.displayMetrics.density)
        val palettes = calendar.palettes
        val light = kept.keep(chrome.fab(scale, palettes.light))
        val dark = kept.keep(chrome.fab(scale, palettes.dark))
        views.setImageViewBitmap(if (right) R.id.fab_light else R.id.fab_left_light, light)
        views.setImageViewBitmap(if (right) R.id.fab_dark else R.id.fab_left_dark, dark)
        views.setOnClickPendingIntent(if (right) R.id.fab else R.id.fab_left, tap)
    }
}
