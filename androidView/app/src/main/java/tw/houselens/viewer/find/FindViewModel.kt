package tw.houselens.viewer.find

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import tw.houselens.viewer.auth.OwnerKey
import tw.houselens.viewer.data.*
import tw.houselens.viewer.saved.*

data class FindState(
    val query: PropertyQuery = PropertyQuery(), val items: List<PropertyDto> = emptyList(),
    val total: Int = 0, val loadedPage: Int = 0, val initialLoading: Boolean = true,
    val refreshing: Boolean = false, val appending: Boolean = false,
    val error: String? = null, val appendError: String? = null,
    val districtChoices: List<DistrictChoice> = emptyList(), val optionsError: String? = null,
    val fetchedAt: String? = null, val resultQuery: PropertyQuery? = null,
    val favoriteIds: Set<String> = emptySet(), val storageError: String? = null,
    val canUndoDistrict: Boolean = false, val restored: Boolean = false,
    val pendingFavoriteIds: Set<String> = emptySet()
)
class FindViewModel(
    private val owner: OwnerKey, private val api: HouseLensData, private val favorites: FavoriteStore,
    private val preferences: FilterPreferences, private val savedStateHandle: SavedStateHandle,
    scope: CoroutineScope? = null
) : ViewModel() {
    private val workScope=scope ?: viewModelScope
    private val restoredQuery=savedStateHandle.get<String>("find.query")?.let {
        runCatching { viewerJson.decodeFromString<PropertyQuery>(it) }.getOrNull()?.takeIf { q -> validateQuery(q)==null }
    }
    private val mutableState=MutableStateFlow(FindState(query=restoredQuery ?: PropertyQuery(),restored=restoredQuery!=null))
    val state: StateFlow<FindState> = mutableState.asStateFlow()
    @Volatile private var generation=0L
    internal var scrollIndex=0
    internal var scrollOffset=0
    private var request: Job?=null
    private var preferencesWrite: Job?=null
    private var append: Job?=null
    private var options: Job?=null
    private var districtUndo: PropertyQuery?=null
    private val persistence=Mutex()
    init {
        workScope.launch {
            val stored=try { preferences.observe(owner).first() }
            catch(e: CancellationException) { throw e }
            catch(_: Exception) { storageFailure("篩選條件讀取失敗，使用預設條件");PropertyQuery() }
            synchronized(this@FindViewModel) {
                if(generation==0L) {
                    mutableState.update { it.copy(query=restoredQuery ?: stored) }
                    startFirst(persist=false)
                }
            }
        }
        workScope.launch {
            try { favorites.observe(owner).collect { entries -> mutableState.update { it.copy(favoriteIds=entries.map { entry -> entry.id }.toSet()) } } }
            catch(e: CancellationException) { throw e }
            catch(_: Exception) { storageFailure("收藏讀取失敗，請重試") }
        }
        reloadOptions()
    }
    @Synchronized fun apply(query: PropertyQuery) {
        val error=validateQuery(query)
        if(error!=null) { mutableState.update { it.copy(error=error) };return }
        districtUndo=null
        mutableState.update { it.copy(query=query,canUndoDistrict=false) }
        startFirst(persist=true)
    }
    @Synchronized fun refresh() { if(generation!=0L) startFirst(persist=false) }
    private fun startFirst(persist: Boolean) {
        val version=++generation;val query=state.value.query
        request?.cancel();append?.cancel()
        savedStateHandle["find.query"]=viewerJson.encodeToString(query)
        mutableState.update { it.copy(initialLoading=it.items.isEmpty(),refreshing=it.items.isNotEmpty(),appending=false,error=null,appendError=null) }
        if(persist) preferencesWrite=workScope.launch {
            persistence.withLock {
                if(query==state.value.query) try {
                    preferences.save(owner,query)
                    mutableState.update { if(it.storageError=="篩選條件儲存失敗，請重新套用") it.copy(storageError=null) else it }
                }
                catch(e: CancellationException) { throw e }
                catch(_: Exception) { storageFailure("篩選條件儲存失敗，請重新套用") }
            }
        }
        val pendingWrite=preferencesWrite
        request=workScope.launch {
            pendingWrite?.join()
            try {
                val page=withTimeout(15_000) { api.properties(query,1) }
                currentCoroutineContext().ensureActive()
                synchronized(this@FindViewModel) {
                    if(version==generation) mutableState.update { it.copy(items=page.items.distinctBy { item -> item.id },total=page.total,
                        loadedPage=1,initialLoading=false,refreshing=false,error=null,resultQuery=query,fetchedAt=Instant.now().toString()) }
                }
            } catch(_: TimeoutCancellationException) { firstFailure(version) }
            catch(e: CancellationException) { throw e }
            catch(_: Exception) { currentCoroutineContext().ensureActive();firstFailure(version) }
        }
    }
    @Synchronized private fun firstFailure(version: Long) {
        if(version==generation) mutableState.update { it.copy(initialLoading=false,refreshing=false,error="暫時無法取得房源，請重試") }
    }
    @Synchronized fun loadNext() {
        val state=state.value
        if(state.initialLoading || state.refreshing || state.appending || state.resultQuery!=state.query || state.loadedPage==0 || state.loadedPage*20>=state.total) return
        val version=generation;val next=state.loadedPage+1
        mutableState.update { it.copy(appending=true,appendError=null) }
        append=workScope.launch {
            try {
                val page=withTimeout(15_000) { api.properties(state.query,next) }
                currentCoroutineContext().ensureActive()
                synchronized(this@FindViewModel) {
                    if(version==generation) mutableState.update { it.copy(items=(it.items+page.items).distinctBy { item -> item.id },
                        total=page.total,loadedPage=next,appending=false,appendError=null,fetchedAt=Instant.now().toString()) }
                }
            } catch(_: TimeoutCancellationException) { appendFailure(version) }
            catch(e: CancellationException) { throw e }
            catch(_: Exception) { currentCoroutineContext().ensureActive();appendFailure(version) }
        }
    }
    @Synchronized private fun appendFailure(version: Long) {
        if(version==generation) mutableState.update { it.copy(appending=false,appendError="下一頁載入失敗") }
    }
    @Synchronized fun reloadOptions() {
        options?.cancel()
        options=workScope.launch {
            try {
                supervisorScope {
                    var failed=false
                    val config=async { try { withTimeout(15_000) { api.districtOptions() } }
                        catch(_: TimeoutCancellationException) { failed=true;emptyList() }
                        catch(e: CancellationException) { throw e }
                        catch(_: Exception) { failed=true;emptyList() } }
                    val stats=async { try { withTimeout(15_000) { api.districtStats() } }
                        catch(_: TimeoutCancellationException) { failed=true;emptyList() }
                        catch(e: CancellationException) { throw e }
                        catch(_: Exception) { failed=true;emptyList() } }
                    val choices=mergeDistrictOptions(config.await(),stats.await())
                    currentCoroutineContext().ensureActive()
                    mutableState.update { it.copy(districtChoices=choices,optionsError=if(failed) "部分行政區選項無法取得" else null) }
                }
            } catch(e: CancellationException) { throw e }
        }
    }
    @Synchronized fun setDistrictFromAnalytics(name: String) {
        if(name.isBlank()) return
        val previous=state.value.query
        apply(PropertyQuery(districts=listOf(name)))
        districtUndo=previous;mutableState.update { it.copy(canUndoDistrict=true) }
    }
    @Synchronized fun undoDistrictChange() {
        val previous=districtUndo ?: return
        apply(previous)
    }
    @Synchronized fun toggleFavorite(property: PropertyDto) {
        if(property.id in state.value.pendingFavoriteIds) return
        mutableState.update { it.copy(pendingFavoriteIds=it.pendingFavoriteIds+property.id) }
        workScope.launch {
            try {
                if(property.id in state.value.favoriteIds) favorites.remove(owner,property.id)
                else favorites.put(owner,FavoriteEntry(property.id,System.currentTimeMillis(),property.toSnapshot()))
                val ids=favorites.observe(owner).first().map { it.id }.toSet()
                mutableState.update { it.copy(favoriteIds=ids,storageError=if(it.storageError=="收藏儲存失敗，請重試") null else it.storageError) }
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { storageFailure("收藏儲存失敗，請重試") }
            finally { mutableState.update { it.copy(pendingFavoriteIds=it.pendingFavoriteIds-property.id) } }
        }
    }
    fun rememberTarget(id: String,history: Boolean) { savedStateHandle["find.target"]=id;savedStateHandle["find.history"]=history }
    private fun storageFailure(message: String) { mutableState.update { it.copy(storageError=message) } }
}
