package dev.cupr.calendar.render

import android.content.res.AssetManager
import android.graphics.Path
import android.graphics.Typeface
import android.util.Log
import androidx.core.graphics.PathParser
import com.caverock.androidsvg.SVG

/**
 * The bundled Roboto (D22): Google Fonts' variable Roboto, `ofl/roboto/
 * Roboto[wdth,wght].ttf` of github.com/google/fonts at 1c627bf, under the
 * SIL Open Font License 1.1 (assets/licenses/Roboto-OFL.txt).
 */
class Fonts(
    private val assets: AssetManager,
    private val path: String = PATH,
) {
    private val byWeight = mutableMapOf<Int, Typeface>()

    /** Roboto at a font weight (400 regular, 500 medium, 600 semi-bold, 700 bold). */
    fun weight(weight: Int): Typeface =
        byWeight.getOrPut(weight) {
            val typeface: Typeface? =
                Typeface
                    .Builder(assets, path)
                    .setFontVariationSettings("'wght' $weight")
                    .build()
            checkNotNull(typeface) { "The font $path does not load at weight $weight" }
        }

    private companion object {
        const val PATH = "fonts/Roboto.ttf"
    }
}

/**
 * MDI 7.4.47 icon path data (D22), as scripts/generate_mdi_icons.py writes
 * it: the icons Home Assistant names for the kinds, plus the widgets' own.
 */
class Icons(
    assets: AssetManager,
) {
    private val data: Map<String, String> =
        assets.open(PATH).bufferedReader().useLines { lines ->
            lines
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .associate { it.substringBefore(' ') to it.substringAfter(' ') }
        }
    private val paths = mutableMapOf<String, Path>()
    private val unknown = UnknownNames("icon")

    /**
     * The icon's outline in a 24 × 24 box (`mdi:swim` or `swim`), none for
     * an icon the app does not ship (logged once per name).
     */
    fun path(name: String): Path? {
        val key = name.removePrefix("mdi:")
        val pathData = data[key] ?: return unknown.report(name)
        return paths.getOrPut(key) { PathParser.createPathFromPathData(pathData) }
    }

    companion object {
        /** The size of the box MDI draws in. */
        const val BOX = 24f
        private const val PATH = "icons/mdi.txt"
    }
}

/**
 * The drawings of special days and entries, copies of the dashboard's
 * (homelab, infra/homeassistant/config/www/zuhause/events/), rendered still:
 * AndroidSVG draws an SVG's static state and leaves out its animation.
 */
class Drawings(
    private val assets: AssetManager,
) {
    private val shipped: Set<String> = assets.list(DIRECTORY).orEmpty().toSet()
    private val parsed = mutableMapOf<String, SVG>()
    private val unknown = UnknownNames("drawing")

    /** The drawing of a file name (`lantern.svg`), none for a drawing the app does not ship (logged once per name). */
    fun get(name: String): SVG? {
        if (name !in shipped) return unknown.report(name)
        return parsed.getOrPut(name) { SVG.getFromAsset(assets, "$DIRECTORY/$name") }
    }

    private companion object {
        const val DIRECTORY = "drawings"
    }
}

/** Logs a name the app does not ship, once per name, so a missing asset shows in the log without flooding it. */
private class UnknownNames(
    private val kind: String,
) {
    private val logged = mutableSetOf<String>()

    fun <T> report(name: String): T? {
        if (logged.add(name)) Log.w(TAG, "The app ships no $kind named $name")
        return null
    }

    private companion object {
        const val TAG = "RenderAssets"
    }
}
