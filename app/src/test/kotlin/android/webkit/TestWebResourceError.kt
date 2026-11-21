package android.webkit

/**
 * A WebView load error for the tests. WebResourceError's constructor is
 * package-private in the SDK, so this class lives in its package.
 */
class TestWebResourceError(
    private val code: Int,
    private val text: String,
) : WebResourceError() {
    override fun getErrorCode() = code

    override fun getDescription(): CharSequence = text
}
