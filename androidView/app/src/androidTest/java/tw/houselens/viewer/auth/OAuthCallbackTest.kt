package tw.houselens.viewer.auth

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import io.github.jan.supabase.auth.auth
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import tw.houselens.viewer.data.createViewerClient

class OAuthCallbackTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val project = "https://callback-test.supabase.co"
    private val sessionJson = """{"access_token":"callback-access","refresh_token":"callback-refresh","expires_in":3600,"token_type":"bearer","user":{"id":"member","email":"member@example.test","aud":"authenticated","created_at":"2026-01-01T00:00:00Z"}}"""
    private suspend fun pending(store: SecureAuthStorage) {
        store.beginLogin()
        store.saveCodeVerifier("persisted-pkce-verifier")
    }
    @Test fun recreated_storage_callback_exchanges_once_with_persisted_verifier() = runBlocking {
        val store = SecureAuthStorage(context, project)
        store.erase()
        pending(store)
        var exchanges = 0
        val recreated = SecureAuthStorage(context, project)
        val sdk = createViewerClient(project, "sb_publishable_test", MockEngine { request ->
            exchanges++
            assertEquals("pkce", request.url.parameters["grant_type"])
            val body = (request.body as io.ktor.http.content.TextContent).text
            assertTrue(body.contains("persisted-pkce-verifier"))
            assertTrue(body.contains("approved-code"))
            respond(sessionJson, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }, recreated)
        val controller = SessionController(sdk, recreated, {})
        try {
            controller.handleCallback(Uri.parse("tw.houselens.viewer://auth/callback?code=approved-code"))
            assertTrue(controller.state.value is AuthState.Authorized)
            controller.handleCallback(Uri.parse("tw.houselens.viewer://auth/callback?code=approved-code"))
            assertEquals(1, exchanges)
            assertNull(recreated.loadCodeVerifier())
            assertEquals("callback-access", SecureAuthStorage(context, project).loadSession()!!.accessToken)
        } finally { sdk.close(); store.erase() }
    }
    @Test fun wrong_path_authority_missing_pending_and_duplicate_code_never_exchange() = runBlocking {
        val store = SecureAuthStorage(context, project)
        store.erase()
        var exchanges = 0
        val sdk = createViewerClient(project, "sb_publishable_test", MockEngine {
            exchanges++
            respond(sessionJson, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }, store)
        val controller = SessionController(sdk, store, {})
        try {
            controller.handleCallback(Uri.parse("tw.houselens.viewer://auth/callback?code=unexpected"))
            pending(store)
            for (uri in listOf(
                "https://auth/callback?code=x", "tw.houselens.viewer://auth/other?code=x",
                "tw.houselens.viewer://auth.evil/callback?code=x", "tw.houselens.viewer://user@auth/callback?code=x",
                "tw.houselens.viewer://auth:80/callback?code=x", "tw.houselens.viewer://auth/callback?code=",
                "tw.houselens.viewer://auth/callback?code=x&code=y", "tw.houselens.viewer://auth/callback?code=x&%63ode=y", "tw.houselens.viewer://auth/callback?code=x&%65rror=denied", "tw.houselens.viewer://auth/callback?code=x#fragment"
            )) controller.handleCallback(Uri.parse(uri))
            assertEquals(0, exchanges)
            assertFalse(controller.state.value is AuthState.Authorized)
            controller.handleCallback(Uri.parse("tw.houselens.viewer://auth/callback?code=approved-code"))
            assertEquals(1, exchanges)
        } finally { sdk.close(); store.erase() }
    }
    @Test fun provider_cancel_consumes_pending_and_cannot_later_login() = runBlocking {
        val store = SecureAuthStorage(context, project)
        store.erase()
        pending(store)
        val sdk = createViewerClient(project, "sb_publishable_test", MockEngine { error("Cancelled callback exchanged") }, store)
        val controller = SessionController(sdk, store, {})
        try {
            controller.handleCallback(Uri.parse("tw.houselens.viewer://auth/callback?error=access_denied"))
            assertEquals(AuthState.SignedOut, controller.state.value)
            assertNull(store.loadCodeVerifier())
            assertFalse(store.claimCallback())
            controller.handleCallback(Uri.parse("tw.houselens.viewer://auth/callback?code=late"))
            assertEquals(AuthState.SignedOut, controller.state.value)
        } finally { sdk.close(); store.erase() }
    }
    @Test fun login_url_is_only_returned_after_verifier_is_durable() = runBlocking {
        val store = SecureAuthStorage(context, project)
        store.erase()
        val sdk = createViewerClient(project, "sb_publishable_test", MockEngine { error("URL creation must not use network") }, store)
        val controller = SessionController(sdk, store, {})
        try {
            val url = controller.startGoogleLogin()
            assertEquals("google", url.getQueryParameter("provider"))
            assertEquals("select_account", url.getQueryParameter("prompt"))
            assertEquals("tw.houselens.viewer://auth/callback", url.getQueryParameter("redirect_to"))
            assertEquals("s256", url.getQueryParameter("code_challenge_method"))
            assertNotNull(SecureAuthStorage(context, project).loadCodeVerifier())
            assertTrue(SecureAuthStorage(context, project).claimCallback())
            controller.signOut()
            assertNull(SecureAuthStorage(context, project).loadCodeVerifier())
        } finally { sdk.close(); store.erase() }
    }
    @Test fun logout_during_callback_exchange_prevents_late_import() = runBlocking {
        val store = SecureAuthStorage(context, project)
        store.erase()
        pending(store)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val sdk = createViewerClient(project, "sb_publishable_test", MockEngine {
            entered.complete(Unit)
            release.await()
            respond(sessionJson, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }, store)
        val controller = SessionController(sdk, store, {})
        try {
            val callback = async { runCatching { controller.handleCallback(Uri.parse("tw.houselens.viewer://auth/callback?code=slow")) } }
            entered.await()
            controller.signOut()
            release.complete(Unit)
            callback.await()
            assertEquals(AuthState.SignedOut, controller.state.value)
            assertNull(sdk.auth.currentSessionOrNull())
            assertNull(store.loadSession())
        } finally { sdk.close(); store.erase() }
    }
}
