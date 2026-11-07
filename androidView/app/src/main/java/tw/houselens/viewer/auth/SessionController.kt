package tw.houselens.viewer.auth

import android.net.Uri
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.exceptions.RestException
import java.net.URI
import java.net.URLDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import tw.houselens.viewer.data.ApiException

data class OwnerKey(val projectUrl: String, val userId: String)
sealed interface AuthState {
    data object Checking : AuthState
    data object SignedOut : AuthState
    data class Denied(val email: String?) : AuthState
    data class Authorized(val owner: OwnerKey, val email: String) : AuthState
    data object ConnectionError : AuthState
    data object StorageError : AuthState
}

class SessionController(
    private val client: SupabaseClient,
    private val storage: ViewerAuthStorage,
    private val verifyMembership: suspend (String) -> Unit,
    private val onInvalidated: suspend (OwnerKey?) -> Unit = {}
) {
    private val mutableState = MutableStateFlow<AuthState>(AuthState.Checking)
    val state: StateFlow<AuthState> = mutableState.asStateFlow()
    @Volatile var version: Long = 0
        private set
    private val stateMutex = Mutex()
    private val refreshMutex = Mutex()
    // SDK code exchange deletes its verifier after HTTP; serialize it with new logins.
    private val oauthMutex = Mutex()
    private val projectUrl = client.supabaseHttpUrl.trimEnd('/')

    suspend fun restore() {
        val expected = stateMutex.withLock {
            if (mutableState.value is AuthState.Authorized) return
            mutableState.value = AuthState.Checking
            version
        }
        try {
            val saved = storage.loadSession()
            if (saved == null) {
                invalidate(AuthState.SignedOut, expected)
                return
            }
            install(saved, expected)
            authorize(expected)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { handleFailure(e, expected) }
    }

    suspend fun startGoogleLogin(): Uri = oauthMutex.withLock {
        invalidate(AuthState.SignedOut)
        if (mutableState.value == AuthState.StorageError) throw AuthStorageException()
        val expected = version
        try {
            val url = stateMutex.withLock {
                ensureCurrent(expected)
                storage.beginLogin()
                client.auth.getOAuthUrl(Google, CALLBACK) { queryParams["prompt"] = "select_account" }
            }
            withTimeout(5.seconds) { storage.awaitVerifier() }
            stateMutex.withLock { ensureCurrent(expected) }
            Uri.parse(url)
        } catch (e: Exception) {
            withContext(NonCancellable) {
                stateMutex.withLock { if (expected == version) storage.cancelPending() }
            }
            if (e is TimeoutCancellationException) throw AuthStorageException()
            throw e
        }
    }

    suspend fun handleCallback(uri: Uri): Unit = oauthMutex.withLock {
        val parsed = try { URI(uri.toString()) } catch (_: Exception) { return }
        if (parsed.scheme != "tw.houselens.viewer" || parsed.rawAuthority != "auth" ||
            parsed.rawPath != "/callback" || parsed.rawFragment != null) return
        val parameters = parsed.rawQuery.orEmpty().split('&').map {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to
                URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }
        val codes = parameters.filter { it.first == "code" }.map { it.second }
        val errors = parameters.filter { it.first == "error" }.map { it.second }
        val cancelled = codes.isEmpty() && errors.size == 1 && errors.single().isNotBlank()
        if (!cancelled && (errors.isNotEmpty() || codes.size != 1 || codes.single().isBlank())) return
        val expected = version
        try {
            val claimed = stateMutex.withLock {
                ensureCurrent(expected)
                storage.claimCallback()
            }
            if (!claimed) {
                if (mutableState.value == AuthState.Checking) restore()
                return
            }
            if (cancelled) {
                invalidate(AuthState.SignedOut, expected)
                return
            }
            stateMutex.withLock { ensureCurrent(expected); mutableState.value = AuthState.Checking }
            val exchanged = client.auth.exchangeCodeForSession(codes.single(), saveSession = false)
            install(exchanged, expected)
            authorize(expected)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { handleFailure(e, expected) }
        finally {
            withContext(NonCancellable) {
                stateMutex.withLock { if (expected == version) storage.cancelPending() }
            }
        }
    }

    suspend fun accessToken(forceRefresh: Boolean = false): String {
        val expected = version
        if (mutableState.value == AuthState.SignedOut || mutableState.value is AuthState.Denied ||
            mutableState.value == AuthState.ConnectionError) throw CancellationException("Signed out")
        val initial = client.auth.currentSessionOrNull() ?: throw CancellationException("Signed out")
        return refreshMutex.withLock {
            ensureCurrent(expected)
            val current = client.auth.currentSessionOrNull() ?: throw CancellationException("Signed out")
            if (current.accessToken != initial.accessToken ||
                (!forceRefresh && current.expiresAt > Clock.System.now() + 30.seconds)) return@withLock current.accessToken
            try {
                val refreshed = client.auth.refreshSession(current.refreshToken)
                install(refreshed, expected)
                refreshed.accessToken
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (e is AuthStorageException || status(e) in listOf(400, 401, 403, 422)) invalidate(AuthState.SignedOut, expected)
                throw e
            }
        }
    }

    suspend fun deny() = invalidate(AuthState.Denied(client.auth.currentUserOrNull()?.email))
    suspend fun signOut() = invalidate(AuthState.SignedOut)

    private suspend fun authorize(expected: Long) {
        var token = accessToken()
        try { verifyMembership(token) }
        catch (e: Exception) {
            if (status(e) != 401) throw e
            token = accessToken(forceRefresh = true)
            verifyMembership(token)
        }
        stateMutex.withLock {
            ensureCurrent(expected)
            val session = client.auth.currentSessionOrNull() ?: throw CancellationException("Signed out")
            val user = session.user ?: throw AuthStorageException()
            if (user.id.isBlank() || user.email.isNullOrBlank()) throw AuthStorageException()
            mutableState.value = AuthState.Authorized(OwnerKey(projectUrl, user.id), user.email!!)
        }
    }

    private suspend fun install(session: UserSession, expected: Long) = stateMutex.withLock {
        ensureCurrent(expected)
        if (session.accessToken.isBlank() || session.refreshToken.isBlank() || session.user?.id.isNullOrBlank()) throw AuthStorageException()
        storage.saveSession(session)
        client.auth.importSession(session, autoRefresh = false)
    }

    private suspend fun handleFailure(error: Exception, expected: Long) {
        when {
            error is AuthStorageException -> invalidate(AuthState.SignedOut, expected)
            status(error) == 401 || status(error) in listOf(400, 422) -> invalidate(AuthState.SignedOut, expected)
            status(error) == 403 -> invalidate(AuthState.Denied(client.auth.currentUserOrNull()?.email), expected)
            else -> stateMutex.withLock {
                ensureCurrent(expected)
                if (mutableState.value !is AuthState.Authorized) mutableState.value = AuthState.ConnectionError
            }
        }
    }

    @OptIn(SupabaseInternal::class)
    private suspend fun invalidate(next: AuthState, expected: Long? = null) = withContext(NonCancellable) {
        stateMutex.withLock {
            if (expected != null) ensureCurrent(expected)
            val owner = (mutableState.value as? AuthState.Authorized)?.owner
                ?: client.auth.currentUserOrNull()?.id?.let { OwnerKey(projectUrl, it) }
            version++
            mutableState.value = AuthState.Checking
            var cleanupFailed = false
            try {
                onInvalidated(owner)
            } catch (_: Exception) {
                cleanupFailed = true
            } finally {
                try {
                    storage.erase()
                } catch (_: Exception) {
                    cleanupFailed = true
                } finally {
                    // SDK clearSession touches storage first; clear memory even when that storage fails.
                    client.auth.stopAutoRefreshForCurrentSession()
                    client.auth.setSessionStatus(SessionStatus.NotAuthenticated(true))
                    mutableState.value = if (cleanupFailed) AuthState.StorageError else next
                }
            }
        }
    }
    private fun ensureCurrent(expected: Long) {
        if (expected != version) throw CancellationException("Session changed")
    }
    private fun status(error: Exception): Int? = when (error) {
        is RestException -> error.statusCode
        is ApiException -> error.status
        else -> null
    }
    companion object { const val CALLBACK = "tw.houselens.viewer://auth/callback" }
}
