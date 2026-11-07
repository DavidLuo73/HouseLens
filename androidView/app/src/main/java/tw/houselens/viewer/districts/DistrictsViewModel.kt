package tw.houselens.viewer.districts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import tw.houselens.viewer.data.*
import tw.houselens.viewer.ui.ChartPoint

data class DistrictsState(val stats:List<DistrictStatsDto> = emptyList(),val selectedDistrict:String?=null,val loading:Boolean=false,val error:String?=null,val fetchedAt:String?=null) { val selected get()=stats.firstOrNull { it.district==selectedDistrict } }
class DistrictsViewModel(private val api:HouseLensData,scope:CoroutineScope?=null):ViewModel() {
 private val workScope=scope ?: viewModelScope
 private val mutableState=MutableStateFlow(DistrictsState())
 val state:StateFlow<DistrictsState> = mutableState.asStateFlow()
 private var request:Job?=null
 private var generation=0L
 init { refresh() }
 @Synchronized fun refresh() {
  val version=++generation;request?.cancel();mutableState.update { it.copy(loading=true,error=null) }
  request=workScope.launch {
   try {
    val stats=withTimeout(15_000) { api.districtStats() };currentCoroutineContext().ensureActive()
    synchronized(this@DistrictsViewModel) { if(version==generation) mutableState.update { old -> old.copy(stats=stats,selectedDistrict=old.selectedDistrict?.takeIf { d->stats.any { it.district==d } } ?: stats.firstOrNull()?.district,loading=false,error=null,fetchedAt=Instant.now().toString()) } }
   } catch(_:TimeoutCancellationException) { failure(version) }
   catch(e:CancellationException) { throw e }
   catch(_:Exception) { currentCoroutineContext().ensureActive();failure(version) }
  }
 }
 @Synchronized private fun failure(version:Long) { if(version==generation) mutableState.update { it.copy(loading=false,error="暫時無法取得區域分析，請重試") } }
 fun selectDistrict(name:String) { if(state.value.stats.any { it.district==name }) mutableState.update { it.copy(selectedDistrict=name) } }
}
internal fun trendPoints(stats:DistrictStatsDto):List<ChartPoint> = stats.trend.mapNotNull { point -> point.avgUnitPrice?.let { ChartPoint(LocalDate.parse(point.date).toEpochDay(),it) } }.sortedBy { it.x }
internal fun districtAverage(stats:DistrictStatsDto):String = if(stats.propertyCount==0) "—" else stats.avgUnitPrice?.let { "${it.stripTrailingZeros().toPlainString()} 萬／坪" } ?: "—"
internal fun districtRange(stats:DistrictStatsDto):String = if(stats.propertyCount==0 || stats.minTotalPrice==null || stats.maxTotalPrice==null) "—" else "${stats.minTotalPrice.stripTrailingZeros().toPlainString()}–${stats.maxTotalPrice.stripTrailingZeros().toPlainString()} 萬"
