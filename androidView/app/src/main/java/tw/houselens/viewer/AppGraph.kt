package tw.houselens.viewer

import android.content.Context
import android.net.Uri
import coil3.ImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject
import tw.houselens.viewer.auth.*
import tw.houselens.viewer.data.*

class AppGraph(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val storage = SecureAuthStorage(context, BuildConfig.SUPABASE_URL)
    private val client = createViewerClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY, storage = storage)
    private var resources: SessionResources? = null
    private var initialized = false
    val sessions = SessionController(client, storage, { token -> verifyMembership(token) }, {
        // B3 adds owner-scoped favorite snapshot purging to this same cleanup path.
        synchronized(this) { resources?.close(); resources = null }
    })

    @OptIn(SupabaseExperimental::class)
    private suspend fun verifyMembership(token: String) {
        val userId = client.auth.currentUserOrNull()?.id ?: throw CancellationException("Signed out")
        val members = client.from("ViewerMembers").select(Columns.list("UserId")) {
            headers["Authorization"] = "Bearer $token"
            filter { eq("UserId", userId) }
            limit(1)
        }.decodeList<JsonObject>()
        if (members.isEmpty()) throw ApiException(403, null, "此帳號尚未獲得使用權限")
    }

    fun initialize(callback: Uri?) {
        if (!initialized) {
            initialized = true
            scope.launch {
                if (callback == null) sessions.restore() else {
                    sessions.handleCallback(callback)
                    if (sessions.state.value == AuthState.Checking) sessions.restore()
                }
            }
        } else if (callback != null) scope.launch { sessions.handleCallback(callback) }
    }

    @Synchronized fun authorizedSession(owner: OwnerKey): SessionResources? {
        val current = sessions.state.value as? AuthState.Authorized ?: return null
        if (current.owner != owner) return null
        val version = sessions.version
        return resources?.takeIf { it.owner == owner && it.version == version }
            ?: SessionResources(context, owner, version, SupabaseHouseLensData(
                client, { sessions.accessToken(true); Unit }, sessions::deny, { sessions.version },
                sessions::signOut, {
                    if (sessions.version != version) throw CancellationException("Session changed")
                    sessions.accessToken()
                    Unit
                }
            )).also { resources = it }
    }
}

@OptIn(coil3.annotation.ExperimentalCoilApi::class)
class SessionResources(context: Context, val owner: OwnerKey, val version: Long, val data: HouseLensData) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val imageClient = createImageClient()
    // Keep images uncached so a late response cannot refill another session's cache.
    val imageLoader = ImageLoader.Builder(context).components {
        add(KtorNetworkFetcherFactory(httpClient = imageClient))
    }.memoryCache(null).diskCache(null).build()

    fun close() {
        scope.cancel()
        imageLoader.shutdown()
        imageClient.close()
    }
}
