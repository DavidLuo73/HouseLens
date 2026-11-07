package tw.houselens.viewer.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import tw.houselens.viewer.data.*

data class DataStatusState(val run:CrawlRunDto?=null,val loading:Boolean=false,val error:String?=null,val fetchedAt:String?=null)
class DataStatusViewModel(private val api:HouseLensData,scope:CoroutineScope?=null):ViewModel() {
 private val workScope=scope ?: viewModelScope
 private val mutableState=MutableStateFlow(DataStatusState())
 val state:StateFlow<DataStatusState> = mutableState.asStateFlow()
 private var request:Job?=null
 private var generation=0L
 init { refresh() }
 @Synchronized fun refresh() {
  val version=++generation;request?.cancel();mutableState.update { it.copy(loading=true,error=null) }
  request=workScope.launch {
   try {
    val run=withTimeout(15_000) { api.latestRun() };currentCoroutineContext().ensureActive()
    synchronized(this@DataStatusViewModel) { if(version==generation) mutableState.update { it.copy(run=run,loading=false,error=null,fetchedAt=Instant.now().toString()) } }
   } catch(_:TimeoutCancellationException) { failure(version) }
   catch(e:CancellationException) { throw e }
   catch(_:Exception) { currentCoroutineContext().ensureActive();failure(version) }
  }
 }
 @Synchronized private fun failure(version:Long) { if(version==generation) mutableState.update { it.copy(loading=false,error="暫時無法取得資料狀態，請重試") } }
}
internal fun runSummary(run:CrawlRunDto?):List<String> {
 if(run==null) return listOf("尚無紀錄")
 val status=when(run.status) { 0->"進行中";1->"已完成";2->"失敗";else->"未知狀態" }
 return listOf("最近資料擷取作業：$status","開始：${formatTaipeiTime(run.startedAt)}",run.finishedAt?.let { "結束：${formatTaipeiTime(it)}" } ?: "尚無結束時間","新增 ${run.newCount} 筆 · 下架 ${run.delistedCount} 筆 · 大降價 ${run.bigDropCount} 筆","僅代表此輪擷取作業；各平台結果如下") +
  if(run.sources.isEmpty()) listOf("尚無來源結果") else run.sources.map { source -> "${listOf("591","樂屋網","信義房屋","永慶房屋","好房網","住商不動產","中信房屋").getOrElse(source.sourceSite) { "來源 ${source.sourceSite}" }}：${if(source.success) "成功" else "失敗"}（${source.fetchedCount} 筆）" }
}
