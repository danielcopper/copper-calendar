package dev.cupr.calendar.render

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Path
import android.graphics.Typeface
import android.util.Log
import androidx.core.graphics.PathParser
import com.caverock.androidsvg.SVG

/**
 * The fonts, icons and drawings the widgets draw with, loaded once per
 * process. Used on the main thread only, where widget updates run: their
 * caches are plain maps, and the Paths and SVGs they hand out are shared.
 */
class RenderAssets private constructor(
    assets: AssetManager,
) {
    val fonts = Fonts(assets)
    val icons = Icons(assets)
    val drawings = Drawings(assets)

    companion object {
        @Volatile private var shared: RenderAssets? = null

        fun of(context: Context): RenderAssets =
            shared ?: synchronized(this) {
                shared ?: RenderAssets(context.applicationContext.assets).also { shared = it }
            }
    }
}

/** A font weight, so a weight and a colour (both numbers) cannot be swapped. */
@JvmInline
value class FontWeight(
    val value: Int,
) {
    init {
        require(value in MIN..MAX) { "A font weight runs from $MIN to $MAX, not $value" }
    }

    companion object {
        private const val MIN = 1
        private const val MAX = 1000

        val REGULAR = FontWeight(400)
        val MEDIUM = FontWeight(500)
        val SEMI_BOLD = FontWeight(600)
        val BOLD = FontWeight(700)
    }
}

/**
 * The bundled Roboto (D22): Google Fonts' variable Roboto, `ofl/roboto/
 * Roboto[wdth,wght].ttf` of github.com/google/fonts at 1c627bf, under the
 * SIL Open Font License 1.1 (assets/licenses/Roboto-OFL.txt).
 */
class Fonts(
    private val assets: AssetManager,
    private val path: String = PATH,
) {
    private val byWeight = mutableMapOf<FontWeight, Typeface>()

    /** Roboto at a weight; fails, naming the file and weight, when the font does not load. */
    fun weight(weight: FontWeight): Typeface =
        byWeight.getOrPut(weight) {
            val typeface: Typeface? =
                Typeface
                    .Builder(assets, path)
                    .setFontVariationSettings("'wght' ${weight.value}")
                    .build()
            checkNotNull(typeface) { "The font $path does not load at weight ${weight.value}" }
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
    private val data: Map<String, String> = assets.open(PATH).bufferedReader().useLines(::parseIcons)
    private val paths = mutableMapOf<String, Path>()
    private val unknown = UnknownNames("icon")

    /** The names of the icons the app ships. */
    val names: Set<String> get() = data.keys

    /**
     * The icon's outline in a 24 × 24 box (`mdi:swim` or `swim`), shared, so
     * not to be changed; none for an icon the app does not ship (logged once
     * per name).
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
 * The lines of icons/mdi.txt (`<name> <path data>`, `#` comments) as name →
 * path data; fails on a line without path data or a name given twice.
 */
internal fun parseIcons(lines: Sequence<String>): Map<String, String> {
    val pairs =
        lines
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val name = line.substringBefore(' ', missingDelimiterValue = "")
                val pathData = line.substringAfter(' ', missingDelimiterValue = "")
                require(
                    name.isNotEmpty() && pathData.isNotBlank(),
                ) { "icons/mdi.txt: a line without a name or path data: $line" }
                name to pathData
            }.toList()
    val twice =
        pairs
            .groupingBy { it.first }
            .eachCount()
            .filterValues { it > 1 }
            .keys
    require(twice.isEmpty()) { "icons/mdi.txt names these icons twice: $twice" }
    return pairs.toMap()
}

/**
 * The drawings of special days and entries, copies of the dashboard's (its
 * folder is named in [PhoneMonth]), rendered still: AndroidSVG draws an
 * SVG's static state and leaves out its animation.
 */
class Drawings(
    private val assets: AssetManager,
) {
    /** The file names of the drawings the app ships. */
    val names: Set<String> = assets.list(DIRECTORY).orEmpty().toSet()
    private val parsed = mutableMapOf<String, SVG>()
    private val unknown = UnknownNames("drawing")

    /**
     * The drawing of a file name (`lantern.svg`), shared, so not to be
     * changed; none for a drawing the app does not ship (logged once per name).
     */
    fun get(name: String): SVG? {
        if (name !in names) return unknown.report(name)
        return parsed.getOrPut(name) { SVG.getFromAsset(assets, "$DIRECTORY/$name") }
    }

    private companion object {
        const val DIRECTORY = "drawings"
    }
}

/** What an entry's mark is drawn from ([drawMark]): its drawing, else its icon. */
internal class MarkAssets(
    val icons: Icons,
    val drawings: Drawings,
)

/**
 * Logs a name the app does not ship, once per name for as long as its owner
 * lives (a process, through [RenderAssets]), so a missing asset shows in the
 * log without flooding it.
 */
private class UnknownNames(
    private val kind: String,
) {
    private val logged = mutableSetOf<String>()

    fun report(name: String): Nothing? {
        if (logged.add(name)) Log.w(TAG, "The app ships no $kind named $name")
        return null
    }

    private companion object {
        const val TAG = "RenderAssets"
    }
}
