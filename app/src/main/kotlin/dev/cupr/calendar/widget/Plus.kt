package dev.cupr.calendar.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import dev.cupr.calendar.R
import dev.cupr.calendar.render.ChromeRenderer
import dev.cupr.calendar.render.Scale
import dev.cupr.calendar.store.PlusCorner
import dev.cupr.calendar.store.TapTarget
import dev.cupr.calendar.tap.Tap
import dev.cupr.calendar.tap.Taps

/**
 * A widget's "+", at the bottom right or the bottom left of its card, as its
 * setup says (D58), or none when its setup says a tap on it opens nothing
 * (D63).
 */
internal object Plus {
    /**
     * Puts "+" of calendar of the widget id in views in look's corner, drawn
     * light and dark by chrome at the display's scale and kept, opening what
     * look's taps say for a new entry on today. Each bottom corner has its
     * own "+" in the layout (widget_fab.xml); the other one is hidden, so a
     * corner set back takes effect too, and both are when "+" opens nothing.
     */
    fun place(
        context: Context,
        views: RemoteViews,
        kept: Kept,
        chrome: ChromeRenderer,
        look: Look,
        id: Int,
        calendar: Content.Calendar,
    ) {
        val shown = look.taps.new != TapTarget.None
        val right = look.corner == PlusCorner.BOTTOM_RIGHT
        views.setViewVisibility(R.id.plus_right, if (shown && right) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.plus_left, if (shown && !right) View.VISIBLE else View.GONE)
        if (!shown) return
        val scale = Scale(context.resources.displayMetrics.density)
        val palettes = calendar.palettes
        val light = kept.keep(chrome.fab(scale, palettes.light))
        val dark = kept.keep(chrome.fab(scale, palettes.dark))
        views.setImageViewBitmap(if (right) R.id.fab_light else R.id.fab_left_light, light)
        views.setImageViewBitmap(if (right) R.id.fab_dark else R.id.fab_left_dark, dark)
        views.setOnClickPendingIntent(
            if (right) R.id.fab else R.id.fab_left,
            Taps.pendingIntent(context, Tap.New(calendar.today), id),
        )
    }
}
