package tw.houselens.viewer.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import tw.houselens.viewer.data.*

enum class ExploreTab { TopRated, BigDrop }
data class TopState(val groups:List<TopGroupDto> = emptyList(),val district:String?=null,val loading:Boolean=false,val error:String?=null,val fetchedAt:String?=null,val scrollIndex:Int=0,val scrollOffset:Int=0)
data class DropsState(val items:List<DropDto> = emptyList(),val district:String?=null,val resultDistrict:String?=null,val loading:Boolean=false,val appending:Boolean=false,val error:String?=null,val appendError:String?=null,val page:Int=0,val total:Int=0,val fetchedAt:String?=null,val scrollIndex:Int=0,val scrollOffset:Int=0)
data class ExploreState(val tab:ExploreTab=ExploreTab.TopRated,val top:TopState=TopState(),val drops:DropsState=DropsState()) {
 val visibleDrops get()=drops.items
}
class ExploreViewModel(private val api:HouseLensData,scope:CoroutineScope?=null):ViewModel() {
 private val workScope=scope ?: viewModelScope
 private val mutableState=MutableStateFlow(ExploreState())
 val state:StateFlow<ExploreState> = mutableState.asStateFlow()
 private var topJob:Job?=null
 private var dropsJob:Job?=null
 private var appendJob:Job?=null
 private var topVersion=0L
 private var dropsVersion=0L
 init { loadTop() }
 @Synchronized fun selectTab(tab:ExploreTab) {
  mutableState.update { it.copy(tab=tab) }
  if(tab==ExploreTab.BigDrop && state.value.drops.page==0 && !state.value.drops.loading && state.value.drops.error==null) loadDrops()
 }
 @Synchronized fun selectDistrict(name:String?) {
  if(state.value.tab==ExploreTab.TopRated) {
   if(name==state.value.top.district) return
   mutableState.update { it.copy(top=it.top.copy(district=name,scrollIndex=0,scrollOffset=0)) }
  } else {
   if(name==state.value.drops.district) return
   mutableState.update { it.copy(drops=it.drops.copy(district=name,scrollIndex=0,scrollOffset=0)) };loadDrops()
  }
 }
 @Synchronized fun refresh() { if(state.value.tab==ExploreTab.TopRated) loadTop() else loadDrops() }
 private fun loadTop() {
  val version=++topVersion;topJob?.cancel()
  mutableState.update { it.copy(top=it.top.copy(loading=true,error=null)) }
  topJob=workScope.launch {
   try {
    val groups=withTimeout(15_000) { api.topRated() };currentCoroutineContext().ensureActive()
    synchronized(this@ExploreViewModel) { if(version==topVersion) mutableState.update { it.copy(top=it.top.copy(groups=groups,loading=false,error=null,fetchedAt=Instant.now().toString())) } }
   } catch(_:TimeoutCancellationException) { topFailure(version) }
   catch(e:CancellationException) { throw e }
   catch(_:Exception) { currentCoroutineContext().ensureActive();topFailure(version) }
  }
 }
 @Synchronized private fun topFailure(version:Long) { if(version==topVersion) mutableState.update { it.copy(top=it.top.copy(loading=false,error="暫時無法取得精選，請重試")) } }
 private fun loadDrops() {
  val version=++dropsVersion;val district=state.value.drops.district;dropsJob?.cancel();appendJob?.cancel()
  mutableState.update { it.copy(drops=it.drops.copy(loading=true,appending=false,error=null,appendError=null)) }
  dropsJob=workScope.launch {
   try {
    val result=withTimeout(15_000) { api.bigDrops(1,district) };currentCoroutineContext().ensureActive()
    synchronized(this@ExploreViewModel) { if(version==dropsVersion) mutableState.update { it.copy(drops=it.drops.copy(items=result.items.distinctBy { e->e.id },page=1,total=result.total,resultDistrict=district,loading=false,error=null,fetchedAt=Instant.now().toString())) } }
   } catch(_:TimeoutCancellationException) { dropsFailure(version,false) }
   catch(e:CancellationException) { throw e }
   catch(_:Exception) { currentCoroutineContext().ensureActive();dropsFailure(version,false) }
  }
 }
 @Synchronized fun loadNextDrops() {
  val drops=state.value.drops
  if(drops.loading || drops.appending || drops.page==0 || drops.resultDistrict!=drops.district || drops.page*20>=drops.total) return
  val version=dropsVersion;val next=drops.page+1
  mutableState.update { it.copy(drops=it.drops.copy(appending=true,appendError=null)) }
  appendJob=workScope.launch {
   try {
    val result=withTimeout(15_000) { api.bigDrops(next,drops.district) };currentCoroutineContext().ensureActive()
    synchronized(this@ExploreViewModel) { if(version==dropsVersion) mutableState.update { it.copy(drops=it.drops.copy(items=(it.drops.items+result.items).distinctBy { e->e.id },page=next,total=result.total,appending=false,appendError=null,fetchedAt=Instant.now().toString())) } }
   } catch(_:TimeoutCancellationException) { dropsFailure(version,true) }
   catch(e:CancellationException) { throw e }
   catch(_:Exception) { currentCoroutineContext().ensureActive();dropsFailure(version,true) }
  }
 }
 @Synchronized private fun dropsFailure(version:Long,append:Boolean) {
  if(version!=dropsVersion) return
  mutableState.update { it.copy(drops=if(append) it.drops.copy(appending=false,appendError="下一頁載入失敗") else it.drops.copy(loading=false,error="暫時無法取得大降價，請重試")) }
 }
 fun rememberPosition(tab:ExploreTab,index:Int,offset:Int) {
  mutableState.update { if(tab==ExploreTab.TopRated) it.copy(top=it.top.copy(scrollIndex=index,scrollOffset=offset)) else it.copy(drops=it.drops.copy(scrollIndex=index,scrollOffset=offset)) }
 }
}
