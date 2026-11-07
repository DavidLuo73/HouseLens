package tw.houselens.viewer.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import tw.houselens.viewer.auth.OwnerKey
import tw.houselens.viewer.data.*
import tw.houselens.viewer.saved.*

data class DetailState(
    val details:PropertyDetails?=null,val loading:Boolean=true,val error:String?=null,
    val notFound:Boolean=false,val saved:Boolean=false,val saving:Boolean=false,
    val storageError:String?=null,val fetchedAt:String?=null
)
class DetailViewModel(private val id:String,private val api:HouseLensData,private val owner:OwnerKey,private val favorites:FavoriteStore):ViewModel() {
    private val mutableState=MutableStateFlow(DetailState())
    val state=mutableState.asStateFlow()
    private var request:Job?=null
    init {
        viewModelScope.launch {
            try { favorites.observe(owner).collect { entries -> mutableState.update { it.copy(saved=entries.any { entry -> entry.id==id }) } } }
            catch(e:CancellationException) { throw e }
            catch(_:Exception) { mutableState.update { it.copy(storageError="收藏讀取失敗，請重試") } }
        }
        refresh()
    }
    fun refresh() {
        request?.cancel()
        mutableState.update { it.copy(loading=true,error=null,notFound=false) }
        request=viewModelScope.launch {
            try {
                val details=withTimeout(15_000) { api.detail(id) }
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(details=details,loading=false,notFound=details==null,error=null,fetchedAt=Instant.now().toString()) }
            } catch(_:TimeoutCancellationException) { failed() }
            catch(e:CancellationException) { throw e }
            catch(_:Exception) { currentCoroutineContext().ensureActive();failed() }
        }
    }
    private fun failed() { mutableState.update { it.copy(loading=false,error="暫時無法取得詳情、來源或價格紀錄，請檢查連線後重試") } }
    fun toggleFavorite() {
        val current=state.value
        if(current.saving || (!current.saved && current.details==null)) return
        mutableState.update { it.copy(saving=true,storageError=null) }
        viewModelScope.launch {
            try {
                if(current.saved) favorites.remove(owner,id)
                else favorites.put(owner,FavoriteEntry(id,System.currentTimeMillis(),current.details!!.property.toSnapshot()))
            } catch(e:CancellationException) { throw e }
            catch(_:Exception) { mutableState.update { it.copy(storageError="收藏儲存失敗，請重試") } }
            finally { mutableState.update { it.copy(saving=false) } }
        }
    }
}