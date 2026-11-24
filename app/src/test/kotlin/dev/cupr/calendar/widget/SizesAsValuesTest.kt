package dev.cupr.calendar.widget

import android.widget.RemoteViews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * No call in the main code takes one of RemoteViews' forms that pass a size as a dimension's or an attribute's number
 * (D66). A scan of the sources rather than detekt's ForbiddenMethodCall, which needs type resolution: it runs in
 * `detektMain`, not in the `detekt` the gate runs.
 */
class SizesAsValuesTest {
    @Test
    fun `no main code passes a size to a widget's views as a resource number`() {
        val files = File(MAIN).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("no Kotlin files under $MAIN", files.isNotEmpty())
        val found = files.flatMap { file -> byNumber(file.readText()).map { "${file.path}:$it" } }
        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun `the scan finds each form that passes a size as a resource number`() {
        for (name in BY_NUMBER) {
            assertEquals(name, listOf("1: $name"), byNumber("views.$name(R.id.list, R.dimen.widget_card_border)"))
        }
        for (name in BY_NUMBER_OR_VALUE) {
            assertEquals(name, listOf("1: $name"), byNumber("views.$name(R.id.card, \"setMinimumHeight\", R.dimen.x)"))
            assertEquals(name, listOf("2: $name"), byNumber("\nviews.$name(\n    R.id.card,\n    \"set\",\n    x,\n)"))
            assertEquals(name, listOf("1: $name"), byNumber("val set = RemoteViews::$name"))
        }
    }

    @Test
    fun `the scan lets sizes given as values through, and names in comments and strings`() {
        val source =
            """
            views.setViewLayoutHeight(R.id.entry_slot, height.toFloat(), TypedValue.COMPLEX_UNIT_PX)
            views.setIntDimen(R.id.card, "setMinimumHeight", 12f, TypedValue.COMPLEX_UNIT_DIP)
            views.setFloatDimen(
                R.id.card,
                "setElevation",
                f(a, b),
                TypedValue.COMPLEX_UNIT_DIP,
            )
            // Never views.setViewLayoutWidthDimen(R.id.list, R.dimen.width).
            /* Nor views.setIntDimen(R.id.card, "x", R.dimen.width). */
            Log.i(TAG, "setViewLayoutHeightDimen(R.id.list, R.dimen.height)")
            """.trimIndent()
        assertEquals(emptyList<String>(), byNumber(source))
    }

    @Test
    fun `the scan reads on past literals with quotes in them`() {
        val call = "\nviews.setViewLayoutWidthDimen(R.id.list, R.dimen.width)"
        val expected = listOf("2: setViewLayoutWidthDimen")
        for (literal in listOf("'\"'", "\"a\\\"b\"", "\"\"\"5\" tall\"\"\"", "\"\"\"say \"hi\"\"\"\"")) {
            assertEquals(literal, expected, byNumber("val text = $literal$call"))
        }
    }

    @Test
    fun `the scan knows each of RemoteViews' methods that take a size by a resource's number`() {
        // A method that takes a size by a resource or an attribute is named …Dimen or …Attr; the ones so named that
        // are not for a size are left out by name, so one a new SDK adds fails here until it is sorted in.
        val methods = RemoteViews::class.java.methods.filter { it.name.endsWith("Dimen") || it.name.endsWith("Attr") }
        val sizes = methods.filter { it.name !in NOT_SIZES }
        assertEquals((BY_NUMBER + BY_NUMBER_OR_VALUE).toSet(), sizes.mapTo(mutableSetOf()) { it.name })
        val withValue = sizes.filter { it.parameterTypes.contentEquals(VALUE_FORM) }.mapTo(mutableSetOf()) { it.name }
        assertEquals(BY_NUMBER_OR_VALUE.toSet(), withValue)
    }

    private companion object {
        /** The main code, from the module's directory, where the unit tests run. */
        const val MAIN = "src/main/kotlin"

        /** RemoteViews' methods that take a size only as a resource's number: a dimension's or an attribute's. */
        val BY_NUMBER =
            listOf(
                "setViewLayoutWidthDimen",
                "setViewLayoutHeightDimen",
                "setViewLayoutMarginDimen",
                "setViewOutlinePreferredRadiusDimen",
                "setViewLayoutWidthAttr",
                "setViewLayoutHeightAttr",
                "setViewLayoutMarginAttr",
                "setViewOutlinePreferredRadiusAttr",
                "setIntDimenAttr",
                "setFloatDimenAttr",
            )

        /**
         * RemoteViews' methods that take a dimension's number with three arguments (view, method, resource) and a
         * value with four ([VALUE_FORM]).
         */
        val BY_NUMBER_OR_VALUE = listOf("setIntDimen", "setFloatDimen")

        /** The value form of [BY_NUMBER_OR_VALUE]: view, method, value, unit. */
        val VALUE_FORM = arrayOf(Int::class.java, String::class.java, Float::class.java, Int::class.java)
        val VALUE_ARGUMENTS = VALUE_FORM.size

        /** RemoteViews' methods named …Attr that take a text's or a colour's attribute, not a size's. */
        val NOT_SIZES = setOf("setCharSequenceAttr", "setColorAttr", "setColorStateListAttr")

        val NAME = Regex("""\b(${(BY_NUMBER + BY_NUMBER_OR_VALUE).joinToString("|")})\b\s*""")

        /**
         * Each place in [source] that passes a size as a resource's number, as its line and the method's name. A
         * reference to a method of [BY_NUMBER_OR_VALUE] counts, as it does not say which form it takes.
         */
        fun byNumber(source: String): List<String> {
            val code = codeOnly(source)
            return NAME
                .findAll(code)
                .filter { match ->
                    val name = match.groupValues[1]
                    name in BY_NUMBER || arguments(code, match.range.last + 1) != VALUE_ARGUMENTS
                }.map { match -> "${code.lineAt(match.range.first)}: ${match.groupValues[1]}" }
                .toList()
        }

        private fun String.lineAt(index: Int): Int = substring(0, index).count { it == '\n' } + 1

        /** The number of arguments of the call whose parenthesis opens at [open] in [code], or null without one. */
        private fun arguments(
            code: String,
            open: Int,
        ): Int? {
            val close = if (code.getOrNull(open) == '(') closing(code, open) else null
            return close?.let { count(code.substring(open + 1, it)) }
        }

        /** Where the parenthesis opening at [open] in [code] closes, or null if it never does. */
        private fun closing(
            code: String,
            open: Int,
        ): Int? {
            var depth = 0
            for (i in open until code.length) {
                when (code[i]) {
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> depth--
                }
                if (depth == 0) return i
            }
            return null
        }

        /** The number of arguments in [list], a call's text between its parentheses. */
        private fun count(list: String): Int {
            var depth = 0
            val commas = mutableListOf<Int>()
            list.forEachIndexed { i, c ->
                when (c) {
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> depth--
                    ',' -> if (depth == 0) commas += i
                }
            }
            // Kotlin allows a comma after the last argument.
            val trailing = commas.isNotEmpty() && list.substring(commas.last() + 1).isBlank()
            return when {
                list.isBlank() -> 0
                trailing -> commas.size
                else -> commas.size + 1
            }
        }

        /** [source] with its comments and string and character literals blanked, its newlines kept. */
        private fun codeOnly(source: String): String {
            val code = StringBuilder(source.length)
            var i = 0
            while (i < source.length) {
                val end = nonCodeEnd(source, i)
                if (end == null) {
                    code.append(source[i])
                    i++
                } else {
                    source.substring(i, end).forEach { code.append(if (it == '\n') '\n' else ' ') }
                    i = end
                }
            }
            return code.toString()
        }

        /** Where the comment or literal starting at [at] in [source] ends, or null if none starts there. */
        private fun nonCodeEnd(
            source: String,
            at: Int,
        ): Int? =
            when {
                source.startsWith("//", at) -> source.indexOf('\n', at).takeIf { it >= 0 } ?: source.length
                source.startsWith("/*", at) -> after(source, at + 2, "*/")
                source.startsWith("\"\"\"", at) -> afterRaw(source, at + 3)
                source.startsWith("\"", at) -> afterQuote(source, at + 1, '"')
                source.startsWith("'", at) -> afterQuote(source, at + 1, '\'')
                else -> null
            }

        private fun after(
            source: String,
            from: Int,
            token: String,
        ): Int = source.indexOf(token, from).takeIf { it >= 0 }?.plus(token.length) ?: source.length

        /** Where the raw string whose text starts at [from] ends: after the first run of three or more quotes. */
        private fun afterRaw(
            source: String,
            from: Int,
        ): Int {
            var end = after(source, from, "\"\"\"")
            while (source.getOrNull(end) == '"') end++
            return end
        }

        private fun afterQuote(
            source: String,
            from: Int,
            quote: Char,
        ): Int {
            var i = from
            while (i < source.length && source[i] != quote) {
                i += if (source[i] == '\\') 2 else 1
            }
            return minOf(i + 1, source.length)
        }
    }
}
