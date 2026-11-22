package dev.cupr.calendar.widget

import android.view.View
import android.widget.RemoteViews
import dev.cupr.calendar.R
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
     * Sets the card in views to opacity. Every redraw sets both its alpha
     * and whether it shows, also at 100 %, as the launcher applies a widget's
     * new views onto the views it shows and would keep an earlier opacity
     * otherwise.
     */
    fun show(
        views: RemoteViews,
        opacity: CardOpacity,
    ) {
        views.setInt(R.id.card, "setImageAlpha", opacity.alpha)
        views.setViewVisibility(R.id.card, if (opacity.shown) View.VISIBLE else View.GONE)
    }
}
