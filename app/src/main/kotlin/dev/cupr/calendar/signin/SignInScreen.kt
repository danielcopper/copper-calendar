package dev.cupr.calendar.signin

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.work.WorkManager
import dev.cupr.calendar.R
import dev.cupr.calendar.app.Services
import dev.cupr.calendar.fetch.FetchWork
import dev.cupr.calendar.ha.HaAddress
import dev.cupr.calendar.ha.SignInRequest
import dev.cupr.calendar.store.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * Signed out: the address field and "Mit Home Assistant anmelden", which
 * opens Home Assistant's login page in a WebView (D42, D43). Signed in: who
 * is signed in, when the last fetch was, whether it failed, and "Abmelden"
 * (D46, D47). The status is read off the main thread, again each time the
 * screen starts ([starts]) and whenever a fetch changes state.
 */
@Composable
fun SignInScreen(starts: Int) {
    val context = LocalContext.current
    val app = context.applicationContext
    var status by remember { mutableStateOf<Status?>(null) }
    var request by rememberSaveable(stateSaver = RequestSaver) { mutableStateOf<SignInRequest?>(null) }
    val flow by SignInFlow.state.collectAsState()
    val scope = rememberCoroutineScope()

    fun reload() = scope.launch { status = withContext(Dispatchers.IO) { Status.read(app) } }

    // Again after each sign-in or sign-out, which run in SignInFlow, beyond this screen's life.
    LaunchedEffect(starts, flow.finished) { reload() }
    WatchFetches { reload() }

    val signIn = request
    if (signIn == null) {
        Content(
            status = status,
            busy = flow.busy,
            problem = flow.problem,
            onSignIn = { address ->
                SignInFlow.clearProblem()
                request = SignInRequest.start(address)
                scope.launch(Dispatchers.IO) { Services.account(app).rememberAddress(address) }
            },
            onSignOut = { SignInFlow.signOut(app) },
        )
        return
    }
    BackHandler { request = null }
    LoginPage(
        signIn = signIn,
        onCallback = { callback ->
            request = null
            if (callback is SignInRequest.Callback.Code) {
                SignInFlow.signIn(app, signIn, callback.code)
            } else {
                Log.w(SIGN_IN_TAG, "Sign-in failed: $callback")
                SignInFlow.fail(Problem.FAILED)
            }
        },
        onUnreachable = {
            request = null
            SignInFlow.fail(Problem.UNREACHABLE)
        },
    )
}

/** Calls [onChange] whenever a fetch, started here or by the period, changes its state, so a new fetch time shows. */
@Composable
private fun WatchFetches(onChange: () -> Unit) {
    val context = LocalContext.current
    val change by rememberUpdatedState(onChange)
    LaunchedEffect(Unit) {
        val work = WorkManager.getInstance(context)
        merge(work.getWorkInfosForUniqueWorkFlow(FetchWork.NOW), work.getWorkInfosForUniqueWorkFlow(FetchWork.PERIODIC))
            .collect { change() }
    }
}

@Composable
private fun Content(
    status: Status?,
    busy: Boolean,
    problem: Problem?,
    onSignIn: (HaAddress) -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            if (status == null) return@Column
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            status.line(context, now, zone)?.let { Text(it) }
            status.failedLine(context, now, zone)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            problem?.let { Text(stringResource(it.text), color = MaterialTheme.colorScheme.error) }
            if (status.state == Account.State.SIGNED_IN) {
                OutlinedButton(enabled = !busy, onClick = onSignOut) { Text(stringResource(R.string.sign_out)) }
            } else {
                AddressForm(status.address, busy, onSignIn)
            }
        }
    }
}

@Composable
private fun AddressForm(
    initial: String,
    busy: Boolean,
    onSignIn: (HaAddress) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            invalid = false
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.sign_in_address)) },
        placeholder = { Text(stringResource(R.string.sign_in_address_example)) },
        isError = invalid,
        supportingText = if (invalid) ({ Text(stringResource(R.string.sign_in_address_invalid)) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
    Button(
        enabled = !busy,
        onClick = {
            val address = HaAddress.parse(text)
            if (address == null) invalid = true else onSignIn(address)
        },
    ) { Text(stringResource(R.string.sign_in)) }
}

/**
 * Home Assistant's login page in a WebView, its redirect and errors
 * handled by [LoginClient]. JavaScript is on because the login page is a
 * JavaScript app that shows nothing without it.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginPage(
    signIn: SignInRequest,
    onCallback: (SignInRequest.Callback) -> Unit,
    onUnreachable: () -> Unit,
) {
    val callback by rememberUpdatedState(onCallback)
    val unreachable by rememberUpdatedState(onUnreachable)
    AndroidView(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = LoginClient(signIn, { callback(it) }, { unreachable() })
                loadUrl(signIn.authorizeUrl.toString())
            }
        },
        // The page is gone once the redirect is caught, the page failed, or the person goes back.
        onRelease = WebView::destroy,
    )
}

/** Keeps a sign-in under way across a configuration change: its address, verifier and state. */
internal val RequestSaver =
    Saver<SignInRequest?, ArrayList<String>>(
        save = { request -> request?.let { arrayListOf(it.address.url.toString(), it.verifier, it.state) } },
        restore = { saved -> HaAddress.stored(saved[0])?.let { SignInRequest(it, saved[1], saved[2]) } },
    )
