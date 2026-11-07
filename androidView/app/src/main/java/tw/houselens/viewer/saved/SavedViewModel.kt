package tw.houselens.viewer.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tw.houselens.viewer.auth.OwnerKey
import tw.houselens.viewer.data.HouseLensData

enum class RefreshStatus { NOT_REFRESHED, UPDATED, REMOVED, TEMPORARY_FAILURE }
data class SavedState(
    val entries: List<FavoriteEntry> = emptyList(),
    val statuses: Map<String, RefreshStatus> = emptyMap(),
    val undoEntry: FavoriteEntry? = null,
    val storageError: String? = null
)

class SavedViewModel(
    private val owner: OwnerKey, private val api: HouseLensData, private val store: FavoriteStore,
    scope: CoroutineScope? = null
) : ViewModel() {
    private val workScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(SavedState())
    val state: StateFlow<SavedState> = mutableState.asStateFlow()
    private val requests = Semaphore(3)
    private val inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val removing = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    init {
        workScope.launch {
            try {
                store.observe(owner).collect { entries ->
                    mutableState.update { it.copy(entries = entries.filterNot { entry -> entry.id in removing }) }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { storageFailure() }
        }
    }
    fun refreshVisible(ids: List<String>) {
        val savedIds = state.value.entries.map { it.id }.toSet()
        ids.distinct().filter { it in savedIds && inFlight.add(it) }.forEach { id ->
            workScope.launch {
                try {
                    val generation = store.generation(owner)
                    requests.withPermit {
                        val property = try { api.property(id) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) {
                            currentCoroutineContext().ensureActive()
                            status(id, RefreshStatus.TEMPORARY_FAILURE)
                            return@withPermit
                        }
                        currentCoroutineContext().ensureActive()
                        if (property == null) status(id, RefreshStatus.REMOVED)
                        else if (store.updateSnapshot(owner,id,property.toSnapshot(),generation)) status(id,RefreshStatus.UPDATED)
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { storageFailure(); status(id,RefreshStatus.TEMPORARY_FAILURE) }
                finally { inFlight.remove(id) }
            }
        }
    }
    fun remove(id: String) {
        val entry = state.value.entries.find { it.id == id } ?: return
        if (!removing.add(id)) return
        mutableState.update { it.copy(entries = it.entries.filterNot { entry -> entry.id == id }, undoEntry = null, storageError = null) }
        workScope.launch {
            try {
                val removed = store.remove(owner,id)
                mutableState.update { it.copy(undoEntry = removed) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                mutableState.update { it.copy(entries = (it.entries.filterNot { item -> item.id == id } + entry)
                    .sortedByDescending { item -> item.savedAtEpochMillis }, storageError = "收藏儲存失敗，請重試") }
            } finally { removing.remove(id) }
        }
    }
    fun undo() {
        val entry = state.value.undoEntry ?: return
        workScope.launch {
            try {
                store.put(owner,entry)
                mutableState.update { it.copy(undoEntry = null, storageError = null) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { storageFailure() }
        }
    }
    fun dismissUndo() { mutableState.update { it.copy(undoEntry = null) } }
    private fun status(id: String,status: RefreshStatus) {
        mutableState.update { it.copy(statuses = it.statuses + (id to status)) }
    }
    private fun storageFailure() { mutableState.update { it.copy(storageError = "收藏儲存失敗，請重試") } }
}