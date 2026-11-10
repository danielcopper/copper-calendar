package dev.cupr.calendar.widget

import dev.cupr.calendar.R
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** "Woche quer"'s preview in the widget picker, at 4 × 2 cells, Monday to Sunday ([WidgetPreviewCheck]). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xxhdpi")
class WeekColumnsWidgetPreviewTest :
    WidgetPreviewCheck(
        "widget_week_columns_preview.png",
        R.xml.widget_week_columns_info,
        R.drawable.widget_week_columns_preview,
        WidgetHost.LOW_HEIGHT_DP,
        { context, width -> WeekColumnsWidget.views(context, width, WidgetHost.LOW_HEIGHT_DP) },
    )
