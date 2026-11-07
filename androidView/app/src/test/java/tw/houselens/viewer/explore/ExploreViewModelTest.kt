package tw.houselens.viewer.explore
import java.io.IOException
import java.math.BigDecimal
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import tw.houselens.viewer.data.*
open class BrowseApi:HouseLensData {
 val detailRequests=mutableListOf<String>()
 val dropRequests=mutableListOf<Pair<Int,String?>>()
 var topFails=false;var statsFails=false;var runFails=false
 var topCalls=0;var statsCalls=0;var runCalls=0
 val ranked=listOf("b","a","e","c","d").mapIndexed { i,id -> TopItemDto("中正區",i+1,id,BigDecimal("1200"),BigDecimal("0.9")) }
 var groups=listOf(TopGroupDto("中正區",ranked),TopGroupDto("空區",emptyList()))
 var stats=listOf(DistrictStatsDto("中正區",1,BigDecimal("42"),BigDecimal("1000"),BigDecimal("1000"),listOf(PriceBucketDto("伺服器分箱",1)),listOf(TrendPointDto("2026-11-07",BigDecimal("42"))),false),DistrictStatsDto("空區",0,BigDecimal("99"),BigDecimal("10"),BigDecimal("20")))
 var drops:suspend (Int,String?)->DropPage={ p,d -> DropPage(45,listOf(DropDto("$d-$p",BigDecimal("800"),BigDecimal("0.2"),"2026-01-01T00:00:00Z",1,originalPrice=BigDecimal("1000"),dropAmount=BigDecimal("200")))) }
 var run:CrawlRunDto?=null
 override suspend fun topRated():List<TopGroupDto> { topCalls++;if(topFails) throw IOException("RAW SECRET");return groups }
 override suspend fun bigDrops(page:Int,district:String?):DropPage { dropRequests+=page to district;return drops(page,district) }
 override suspend fun districtStats():List<DistrictStatsDto> { statsCalls++;if(statsFails) throw IOException("RAW SECRET");return stats }
 override suspend fun latestRun():CrawlRunDto? { runCalls++;if(runFails) throw IOException("RAW SECRET");return run }
 override suspend fun detail(id:String):PropertyDetails? { detailRequests+=id;error("No per-card reads") }
 override suspend fun property(id:String):PropertyDto?=error("unused")
 override suspend fun properties(query:PropertyQuery,page:Int):PropertyPage=error("unused")
 override suspend fun districtOptions()=listOf(DistrictOptionDto("臺北市","中正區"),DistrictOptionDto("基隆市","中正區"))
}
@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {
 @Test fun rank_order_null_unit_empty_group_and_no_details()=runTest {
  val api=BrowseApi();val vm=ExploreViewModel(api,backgroundScope);runCurrent()
  assertEquals(listOf("b","a","e","c","d"),vm.state.value.top.groups.first().items.map { it.id })
  assertNull(vm.state.value.top.groups.first().items.first().unitPrice)
  assertTrue(vm.state.value.top.groups.last().items.isEmpty());assertEquals(0,api.detailRequests.size)
 }
 @Test fun tabs_keep_independent_filters_positions_and_events_with_server_paging()=runTest {
  val api=BrowseApi();val vm=ExploreViewModel(api,backgroundScope);runCurrent()
  vm.selectDistrict("中正區");vm.rememberPosition(ExploreTab.TopRated,7,13)
  vm.selectTab(ExploreTab.BigDrop);runCurrent();vm.selectDistrict("空區");runCurrent()
  vm.loadNextDrops();vm.loadNextDrops();runCurrent()
  assertEquals(listOf(1 to null,1 to "空區",2 to "空區"),api.dropRequests)
  assertEquals(2,vm.state.value.drops.page);assertEquals(listOf("空區-1","空區-2"),vm.state.value.visibleDrops.map { it.id })
  val event=vm.state.value.visibleDrops.first();assertEquals(BigDecimal("800"),event.newPrice)
  assertEquals("2026-01-01T00:00:00Z",event.capturedAt);assertEquals(1,event.status)
  vm.rememberPosition(ExploreTab.BigDrop,4,6);vm.selectTab(ExploreTab.TopRated)
  assertEquals("中正區",vm.state.value.top.district);assertEquals(7,vm.state.value.top.scrollIndex)
  vm.selectTab(ExploreTab.BigDrop);assertEquals("空區",vm.state.value.drops.district);assertEquals(4,vm.state.value.drops.scrollIndex)
  vm.selectDistrict("中正區");runCurrent();assertEquals(1,vm.state.value.drops.page);assertEquals(1 to "中正區",api.dropRequests.last())
  assertEquals(0,api.detailRequests.size)
 }
 @Test fun append_and_refresh_errors_preserve_data_and_retry_without_raw_errors()=runTest {
  val api=BrowseApi();val vm=ExploreViewModel(api,backgroundScope);vm.selectTab(ExploreTab.BigDrop);runCurrent()
  val old=vm.state.value.visibleDrops;api.drops={ _,_->throw IOException("RAW SECRET") }
  vm.loadNextDrops();runCurrent();assertEquals(old,vm.state.value.visibleDrops);assertNotNull(vm.state.value.drops.appendError)
  assertFalse(vm.state.value.drops.appendError!!.contains("RAW"))
  vm.refresh();runCurrent();assertEquals(old,vm.state.value.visibleDrops);assertNotNull(vm.state.value.drops.error)
  api.drops={ _,_->DropPage(1,listOf(old.first().copy(id="fresh"))) };vm.refresh();runCurrent()
  assertEquals("fresh",vm.state.value.visibleDrops.single().id);assertEquals(1,vm.state.value.drops.page);assertNull(vm.state.value.drops.error)
  api.topFails=true;vm.selectTab(ExploreTab.TopRated);vm.refresh();runCurrent();assertNotNull(vm.state.value.top.error);assertFalse(vm.state.value.top.groups.isEmpty())
 }
 @Test fun cancelled_old_district_cannot_overwrite_new_response()=runTest {
  val api=BrowseApi();val vm=ExploreViewModel(api,backgroundScope);vm.selectTab(ExploreTab.BigDrop);runCurrent()
  val release=CompletableDeferred<Unit>();api.drops={ _,d -> if(d=="slow") withContext(NonCancellable) { release.await();DropPage(1,listOf(DropDto("slow",BigDecimal.ONE,BigDecimal.ONE,"2026-01-01T00:00:00Z",0))) } else DropPage(0,emptyList()) }
  vm.selectDistrict("slow");runCurrent();vm.selectDistrict("fast");runCurrent();release.complete(Unit);runCurrent()
  assertEquals("fast",vm.state.value.drops.resultDistrict);assertTrue(vm.state.value.visibleDrops.isEmpty())
 }
}
