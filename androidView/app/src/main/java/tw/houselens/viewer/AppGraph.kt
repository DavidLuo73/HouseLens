package tw.houselens.viewer

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import coil3.ImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.*
import tw.houselens.viewer.auth.OwnerKey
import tw.houselens.viewer.data.*
import tw.houselens.viewer.saved.*
import tw.houselens.viewer.find.*

interface AppGraph {
    val session: SessionResources
}

class DefaultAppGraph(
    context: Context,
    private val client: SupabaseClient = createViewerClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY),
    directory: java.io.File = context.filesDir
) : AppGraph {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val favorites = FavoriteStore(java.io.File(directory, "favorites"), scope)
    private val filterPreferences = FilterPreferences(java.io.File(directory, "filters"), scope)
    // A reserved local namespace never imports an earlier authenticated owner's files.
    val owner = OwnerKey(client.supabaseHttpUrl.trimEnd('/'), "local-device-v1")
    override val session = SessionResources(context, owner, SupabaseHouseLensData(client), favorites, filterPreferences)
}

@OptIn(coil3.annotation.ExperimentalCoilApi::class)
class SessionResources(context: Context, val owner: OwnerKey, val data: HouseLensData, val favorites: FavoriteStore, private val filterPreferences: FilterPreferences) : androidx.lifecycle.ViewModelStoreOwner {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override val viewModelStore = androidx.lifecycle.ViewModelStore()
    val saved = SavedViewModel(owner, data, favorites, scope)
    fun find(savedStateHandle: SavedStateHandle) = FindViewModel(owner, data, favorites, filterPreferences, savedStateHandle, scope)
    private val imageClient = createImageClient()
    val imageLoader = ImageLoader.Builder(context).components {
        add(KtorNetworkFetcherFactory(httpClient = imageClient))
    }.memoryCache(null).diskCache(null).build()

    fun close() {
        viewModelStore.clear()
        scope.cancel()
        imageLoader.shutdown()
        imageClient.close()
    }
}
