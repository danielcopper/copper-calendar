package dev.cupr.calendar.widget

import dev.cupr.calendar.R
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** "Woche"'s preview in the widget picker, at 4 × 4 cells, Monday to Sunday ([WidgetPreviewCheck]). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+xxhdpi")
class WeekWidgetPreviewTest :
    WidgetPreviewCheck(
        "widget_week_preview.png",
        R.xml.widget_week_info,
        R.drawable.widget_week_preview,
        WidgetHost.HEIGHT_DP,
        { context, width -> WeekWidget.views(context, width) },
    )
