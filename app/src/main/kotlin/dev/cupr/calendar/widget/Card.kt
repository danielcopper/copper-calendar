package dev.cupr.calendar.widget

import android.view.View
import android.widget.RemoteViews
import androidx.annotation.IdRes
import dev.cupr.calendar.R
import dev.cupr.calendar.render.opacityAlpha
import dev.cupr.calendar.store.CardOpacity

/**
 * The card behind a widget at the opacity its setup sets (D64): the image
 * of its fill and border (widget_card_layer.xml) at that opacity, its
 * colours unchanged, and none at 0 %; the texts and drawings above it stay
 * opaque. A widget's views cannot set a view's background's alpha alone, so
 * the card is an image of its own, whose alpha `ImageView.setImageAlpha`, a
 * `@RemotableViewMethod`, sets without a bitmap of the card (D40).
 */
internal object Card {
    /**
     * Sets the card in views to opacity, in percent. Every redraw sets both
     * its alpha and whether it shows, also at 100 %, as the launcher applies a
     * widget's new views onto the views it shows and would keep an earlier
     * opacity otherwise.
     */
    fun show(
        views: RemoteViews,
        opacity: Int,
    ) = layer(views, R.id.card, opacity, shown = true)

    /**
     * Sets the image id in views, a layer in the card's colour such as
     * "Woche quer"'s fade, to the card's opacity, in percent, when shown,
     * and hides it when not or at 0 %.
     */
    fun layer(
        views: RemoteViews,
        @IdRes id: Int,
        opacity: Int,
        shown: Boolean,
    ) {
        require(CardOpacity.isStep(opacity)) { "A card's opacity runs from 0 to 100 % in steps of 10, not $opacity" }
        views.setInt(id, "setImageAlpha", opacityAlpha(opacity / CardOpacity.FULL.toFloat()))
        views.setViewVisibility(id, if (shown && opacity > 0) View.VISIBLE else View.GONE)
    }
}
