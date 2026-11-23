package dev.cupr.calendar.signin

import android.content.Context
import android.util.Log
import dev.cupr.calendar.app.Services
import dev.cupr.calendar.ha.AuthorizationCode
import dev.cupr.calendar.ha.SignInRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The sign-in screen's log tag, for the screen, its WebView client and this flow. */
internal const val SIGN_IN_TAG = "SignIn"

/**
 * A sign-in or sign-out under way, and why the last sign-in or sign-out
 * did not come about (D43, D47). Held for the app's process, not the
 * screen, so a screen rebuilt meanwhile (a rotation, the dark mode) shows
 * the work still running and its result once done; only one runs at a
 * time.
 */
object SignInFlow {
    data class State(
        val busy: Boolean = false,
        val problem: Problem? = null,
        /** Counts the finished runs, so the screen reads the status anew after each. */
        val finished: Int = 0,
    )

    /**
     * Hands an error a run lets through straight to the thread's uncaught-exception handler, the last stop coroutines
     * take on their own too, so it ends the app (D43). Without it, coroutines first offer the error to every handler
     * the ServiceLoader finds: in the app only kotlinx-coroutines-android's, which does nothing from Android 9 on, but
     * in the tests also kotlinx-coroutines-test's collector, which, once any `runTest` has run, keeps the error and
     * fails the next `runTest` with it.
     */
    private val uncaught =
        CoroutineExceptionHandler { _, e ->
            val thread = Thread.currentThread()
            // Null only for a thread that has ended, never the running one.
            checkNotNull(thread.uncaughtExceptionHandler).uncaughtException(thread, e)
        }

    private val scope = CoroutineScope(SupervisorJob() + uncaught)
    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    /** Trades [code] for the sign-in and fetches at once; false when another run is under way. */
    fun signIn(
        context: Context,
        request: SignInRequest,
        code: AuthorizationCode,
    ): Boolean {
        val app = context.applicationContext
        return run { if (Services.signIn(app, request, code)) null else Problem.FAILED }
    }

    /** Signs out; false when another run is under way. */
    fun signOut(context: Context): Boolean {
        val app = context.applicationContext
        return run {
            Services.signOut(app)
            null
        }
    }

    /** A sign-in that ended in the login page, without a run. */
    fun fail(problem: Problem) = mutable.update { it.copy(problem = problem) }

    fun clearProblem() = mutable.update { it.copy(problem = null) }

    /**
     * Starts [work] unless a run is under way; a refused start is logged, and
     * the screen keeps showing the run. A run that throws a runtime
     * exception ends too, its problem [Problem.FAILED], and the log names the
     * exception's class alone, which holds no secret; an error is not caught
     * and reaches the thread's uncaught-exception handler (D43, as D54 draws
     * the line).
     */
    @Suppress("TooGenericExceptionCaught")
    private fun run(work: () -> Problem?): Boolean {
        var started = false
        mutable.update {
            started = !it.busy
            if (it.busy) it else it.copy(busy = true, problem = null)
        }
        if (!started) Log.w(SIGN_IN_TAG, "A sign-in or sign-out is under way; another was not started")
        if (started) {
            scope.launch(Services.io) {
                var problem: Problem? = null
                try {
                    problem = work()
                } catch (e: RuntimeException) {
                    if (e is CancellationException) throw e
                    Log.e(SIGN_IN_TAG, "A sign-in or sign-out failed: ${e.javaClass.simpleName}")
                    problem = Problem.FAILED
                } finally {
                    mutable.update { State(busy = false, problem = problem, finished = it.finished + 1) }
                }
            }
        }
        return started
    }
}
