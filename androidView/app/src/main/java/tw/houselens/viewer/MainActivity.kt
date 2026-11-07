package tw.houselens.viewer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tw.houselens.viewer.auth.AuthScreen
import tw.houselens.viewer.auth.AuthState
import tw.houselens.viewer.ui.HouseLensTheme
import tw.houselens.viewer.ui.LocalPropertyImageLoader

class MainActivity : ComponentActivity() {
    private val graph get() = (application as HouseLensApplication).graph
    private var receivedCallback = false
    private val loginBrowser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (!receivedCallback && graph.sessions.state.value == AuthState.SignedOut) {
            graph.scope.launch { graph.sessions.signOut() }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        receivedCallback = intent.data != null || savedInstanceState?.getBoolean("receivedCallback") == true
        graph.initialize(intent.data)
        setContent {
            val state by graph.sessions.state.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            var busy by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            val login: () -> Unit = {
                scope.launch {
                    busy = true
                    error = null
                    try {
                        val uri = graph.sessions.startGoogleLogin()
                        receivedCallback = false
                        loginBrowser.launch(CustomTabsIntent.Builder().build().intent.setData(uri))
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) {
                        graph.sessions.signOut()
                        error = getString(R.string.auth_browser_error)
                    } finally { busy = false }
                }
            }
            val signOut: () -> Unit = { scope.launch { graph.sessions.signOut(); error = null } }
            HouseLensTheme {
                Surface(Modifier.fillMaxSize()) {
                    val authorized = state as? AuthState.Authorized
                    if (authorized != null) {
                        key(authorized.owner, graph.sessions.version) {
                            val session = remember { graph.authorizedSession(authorized.owner) }
                            if (session != null) CompositionLocalProvider(LocalPropertyImageLoader provides session.imageLoader) {
                                AuthScreen(state, busy, error, login, {}, signOut)
                            }
                        }
                    } else AuthScreen(state, busy, error, login, {
                        scope.launch { error = null; graph.sessions.restore() }
                    }, signOut)
                }
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("receivedCallback", receivedCallback)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receivedCallback = intent.data != null
        graph.initialize(intent.data)
    }
}
