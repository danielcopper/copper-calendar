package dev.cupr.calendar.widget

import dev.cupr.calendar.R
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The month widget's preview in the widget picker, at 4 × 4 cells ([WidgetPreviewCheck]). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xxhdpi")
class MonthWidgetPreviewTest :
    WidgetPreviewCheck(
        "widget_month_preview.png",
        R.xml.widget_month_info,
        R.drawable.widget_month_preview,
        WidgetHost.HEIGHT_DP,
        WidgetHost.month,
    )
