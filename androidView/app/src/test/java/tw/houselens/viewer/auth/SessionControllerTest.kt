package tw.houselens.viewer.auth

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import tw.houselens.viewer.data.*

class SessionControllerTest {
    private fun session(token: String = "old") = viewerJson.decodeFromString<UserSession>(
        """{"access_token":"$token","refresh_token":"refresh","expires_in":3600,"token_type":"bearer","user":{"id":"member","email":"member@example.test","aud":"authenticated","created_at":"2026-01-01T00:00:00Z"}}"""
    )
    private fun client(storage: ViewerAuthStorage, engine: MockEngine = MockEngine { error("Unexpected network") }) =
        createViewerClient("https://project.supabase.co", "sb_publishable_test", engine, storage)

    @Test fun cold_start_waits_for_membership_before_authorizing() = runTest {
        val storage = MemoryAuthStorage(session())
        val sdk = client(storage)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val controller = SessionController(sdk, storage, { entered.complete(Unit); release.await() })
        try {
            val restore = launch { controller.restore() }
            entered.await()
            assertEquals(AuthState.Checking, controller.state.value)
            release.complete(Unit)
            restore.join()
            assertEquals(AuthState.Authorized(OwnerKey("https://project.supabase.co", "member"), "member@example.test"), controller.state.value)
            assertFalse(sdk.auth.isAutoRefreshRunning)
        } finally { sdk.close() }
    }
    @Test fun forbidden_membership_denies_and_invalidates_before_state_changes() = runTest {
        for (status in listOf(403)) {
            val storage = MemoryAuthStorage(session())
            val sdk = client(storage)
            var cleared = false
            lateinit var controller: SessionController
            controller = SessionController(sdk, storage, { throw ApiException(status, null, "denied") }, {
                assertTrue(controller.version > 0)
                assertFalse(controller.state.value is AuthState.Authorized)
                cleared = true
            })
            try {
                controller.restore()
                assertTrue(cleared)
                assertEquals(AuthState.Denied("member@example.test"), controller.state.value)
                assertNull(storage.loadSession())
            } finally { sdk.close() }
        }
    }
    @Test fun cold_start_offline_preserves_session_and_retry_runs_probe() = runTest {
        val storage = MemoryAuthStorage(session())
        val sdk = client(storage)
        var offline = true
        val controller = SessionController(sdk, storage, { if (offline) throw IOException("offline") })
        try {
            controller.restore()
            assertTrue(controller.state.value is AuthState.ConnectionError)
            assertNotNull(storage.loadSession())
            offline = false
            controller.restore()
            assertTrue(controller.state.value is AuthState.Authorized)
        } finally { sdk.close() }
    }
    @Test fun logout_during_slow_restore_never_remounts_previous_owner() = runTest {
        val storage = MemoryAuthStorage(session())
        val sdk = client(storage)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val controller = SessionController(sdk, storage, { entered.complete(Unit); release.await() })
        try {
            val restore = async { runCatching { controller.restore() } }
            entered.await()
            controller.signOut()
            release.complete(Unit)
            restore.await()
            assertEquals(AuthState.SignedOut, controller.state.value)
            assertNull(sdk.auth.currentSessionOrNull())
            assertNull(storage.loadSession())
        } finally { sdk.close() }
    }
    @Test fun concurrent_forced_refresh_is_shared_and_logout_disables_refresh() = runTest {
        val storage = MemoryAuthStorage(session())
        var calls = 0
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val sdk = client(storage, MockEngine {
            calls++
            entered.complete(Unit)
            release.await()
            respond(viewerJson.encodeToString(UserSession.serializer(), session("new")), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        })
        val controller = SessionController(sdk, storage, {})
        try {
            controller.restore()
            val first = async { controller.accessToken(true) }
            entered.await()
            val second = async { controller.accessToken(true) }
            yield()
            release.complete(Unit)
            assertEquals(listOf("new", "new"), awaitAll(first, second))
            assertEquals(1, calls)
            controller.signOut()
            try { controller.accessToken(true); fail() } catch (_: CancellationException) {}
            assertEquals(1, calls)
        } finally { sdk.close() }
    }
    @Test fun logout_during_refresh_prevents_sdk_import_and_storage_refill() = runTest {
        val storage = MemoryAuthStorage(session())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val sdk = client(storage, MockEngine {
            entered.complete(Unit)
            release.await()
            respond(viewerJson.encodeToString(UserSession.serializer(), session("late")), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        })
        val controller = SessionController(sdk, storage, {})
        try {
            controller.restore()
            val refresh = async { runCatching { controller.accessToken(true) } }
            entered.await()
            controller.signOut()
            release.complete(Unit)
            assertTrue(refresh.await().exceptionOrNull() is CancellationException)
            assertNull(sdk.auth.currentSessionOrNull())
            assertNull(storage.loadSession())
        } finally { sdk.close() }
    }
    @Test fun fatal_refresh_401_signs_out_instead_of_denied() = runTest {
        val storage = MemoryAuthStorage(session())
        val sdk = client(storage, MockEngine {
            respond("""{"message":"expired"}""", HttpStatusCode.Unauthorized, headersOf("Content-Type", "application/json"))
        })
        val controller = SessionController(sdk, storage, {})
        try {
            controller.restore()
            runCatching { controller.accessToken(true) }
            assertEquals(AuthState.SignedOut, controller.state.value)
            assertNull(storage.loadSession())
        } finally { sdk.close() }
    }
    @Test fun verifier_timeout_clears_pending_and_returns_a_retryable_auth_error() = runTest {
        val storage = object : MemoryAuthStorage() {
            override suspend fun saveCodeVerifier(codeVerifier: String) {}
            override suspend fun awaitVerifier() { awaitCancellation() }
        }
        val sdk = client(storage)
        val controller = SessionController(sdk, storage, {})
        try {
            val failure = runCatching { controller.startGoogleLogin() }.exceptionOrNull()
            assertTrue("Expected a retryable auth failure, got $failure", failure is AuthStorageException)
            assertFalse(storage.claimCallback())
            assertNull(storage.loadCodeVerifier())
        } finally { sdk.close() }
    }
    @Test fun storage_failure_during_refresh_clears_session() = runTest {
        var broken = false
        val storage = object : MemoryAuthStorage(session()) {
            override suspend fun saveSession(session: UserSession) {
                if (broken) throw AuthStorageException()
                super.saveSession(session)
            }
        }
        val sdk = client(storage, MockEngine {
            respond(viewerJson.encodeToString(UserSession.serializer(), session("new")), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        })
        val controller = SessionController(sdk, storage, {})
        try {
            controller.restore()
            broken = true
            runCatching { controller.accessToken(true) }
            assertEquals(AuthState.SignedOut, controller.state.value)
            assertNull(sdk.auth.currentSessionOrNull())
        } finally { sdk.close() }
    }
    @Test fun offline_refresh_preserves_already_authorized_session() = runTest {
        val storage = MemoryAuthStorage(session())
        val sdk = client(storage, MockEngine { throw IOException("offline") })
        val controller = SessionController(sdk, storage, {})
        try {
            controller.restore()
            val authorized = controller.state.value
            runCatching { controller.accessToken(true) }
            assertEquals(authorized, controller.state.value)
            assertEquals("old", storage.loadSession()!!.accessToken)
        } finally { sdk.close() }
    }
    @Test fun cold_start_membership_401_retries_once_then_signs_out() = runTest {
        val storage = MemoryAuthStorage(session())
        var refreshes = 0
        var probes = 0
        val sdk = client(storage, MockEngine {
            refreshes++
            respond(viewerJson.encodeToString(UserSession.serializer(), session("new")), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        })
        val controller = SessionController(sdk, storage, {
            probes++
            throw ApiException(401, null, "expired")
        })
        try {
            controller.restore()
            assertEquals(2, probes)
            assertEquals(1, refreshes)
            assertEquals(AuthState.SignedOut, controller.state.value)
            assertNull(storage.loadSession())
        } finally { sdk.close() }
    }
    @Test fun deletion_failure_clears_sdk_and_reports_retryable_storage_error_without_throwing() = runTest {
        val storage = object : MemoryAuthStorage(session()) {
            override suspend fun erase() { throw AuthStorageException() }
        }
        val sdk = client(storage)
        val controller = SessionController(sdk, storage, {})
        try {
            controller.restore()
            val previousVersion = controller.version
            val failure = runCatching { controller.signOut() }.exceptionOrNull()
            assertNull("Cleanup errors must not escape into Activity or graph coroutines", failure)
            assertEquals(AuthState.StorageError, controller.state.value)
            assertNull(sdk.auth.currentSessionOrNull())
            assertFalse(sdk.auth.isAutoRefreshRunning)
            assertTrue(controller.version > previousVersion)
        } finally { sdk.close() }
    }
    @Test fun corrupted_storage_resets_to_login() = runTest {
        val storage = object : MemoryAuthStorage() {
            override suspend fun loadSession(): UserSession? = throw AuthStorageException()
        }
        val sdk = client(storage)
        val controller = SessionController(sdk, storage, { fail("Must not probe") })
        try { controller.restore(); assertEquals(AuthState.SignedOut, controller.state.value) }
        finally { sdk.close() }
    }
}

open class MemoryAuthStorage(private var session: UserSession? = null) : ViewerAuthStorage {
    private var verifier: String? = null
    private var pending = false
    override suspend fun saveSession(session: UserSession) { this.session = session }
    override suspend fun loadSession() = session
    override suspend fun deleteSession() { session = null }
    override suspend fun saveCodeVerifier(codeVerifier: String) { verifier = codeVerifier }
    override suspend fun loadCodeVerifier() = verifier
    override suspend fun deleteCodeVerifier() { verifier = null }
    override suspend fun beginLogin() { pending = true; verifier = null }
    override suspend fun awaitVerifier() { check(verifier != null) }
    override suspend fun claimCallback(): Boolean = (pending && verifier != null).also { pending = false }
    override suspend fun cancelPending() { pending = false; verifier = null }
    override suspend fun erase() { session = null; pending = false; verifier = null }
}
