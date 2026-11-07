package tw.houselens.viewer.districts
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import tw.houselens.viewer.data.*
import tw.houselens.viewer.explore.BrowseApi
import tw.houselens.viewer.auth.*
@OptIn(ExperimentalCoroutinesApi::class)
class DistrictsViewModelTest {
 @Test fun zero_count_is_dash_insufficient_and_same_names_merge()=runTest {
  val api=BrowseApi();val vm=DistrictsViewModel(api,backgroundScope);runCurrent()
  assertEquals("中正區",vm.state.value.selectedDistrict);vm.selectDistrict("空區")
  assertEquals("—",districtAverage(vm.state.value.selected!!));assertEquals("—",districtRange(vm.state.value.selected!!));assertTrue(vm.state.value.selected!!.insufficientData)
  assertEquals(listOf("中正區"),mergeDistrictOptions(api.districtOptions(),emptyList()).map { it.name })
 }
 @Test fun date_only_single_point_and_same_price_keep_server_buckets()=runTest {
  val api=BrowseApi();val vm=DistrictsViewModel(api,backgroundScope);runCurrent();val stats=vm.state.value.selected!!
  assertEquals("2026-11-07",formatDateOnly(stats.trend.single().date));assertEquals(LocalDate.parse("2026-11-07").toEpochDay(),trendPoints(stats).single().x)
  assertEquals(BigDecimal("42"),trendPoints(stats).single().y);assertEquals(listOf(PriceBucketDto("伺服器分箱",1)),stats.priceBuckets)
  val equal=stats.copy(trend=listOf(TrendPointDto("2026-11-08",BigDecimal("42")),TrendPointDto("2026-11-07",BigDecimal("42"))))
  assertEquals(listOf(LocalDate.parse("2026-11-07").toEpochDay(),LocalDate.parse("2026-11-08").toEpochDay()),trendPoints(equal).map { it.x })
 }
 @Test fun refresh_retains_selection_and_data_on_failure_and_recovers()=runTest {
  val api=BrowseApi();val vm=DistrictsViewModel(api,backgroundScope);runCurrent();vm.selectDistrict("空區");val old=vm.state.value.stats
  api.statsFails=true;vm.refresh();runCurrent();assertEquals(old,vm.state.value.stats);assertNotNull(vm.state.value.error);assertFalse(vm.state.value.error!!.contains("RAW"))
  api.statsFails=false;vm.refresh();runCurrent();assertEquals("空區",vm.state.value.selectedDistrict);assertNull(vm.state.value.error)
 }
 @Test fun latest_null_and_completed_source_failure_are_explicit_without_polling()=runTest {
  val api=BrowseApi();val vm=DataStatusViewModel(api,backgroundScope);runCurrent()
  assertTrue(runSummary(vm.state.value.run).contains("尚無紀錄"));assertEquals(1,api.runCalls)
  api.run=CrawlRunDto("r","2026-11-07T00:00:00Z",status=1,newCount=1,delistedCount=0,bigDropCount=0,sources=listOf(SourceResultDto(2,false,0)))
  vm.refresh();runCurrent();val text=runSummary(vm.state.value.run).joinToString()
  assertTrue(text.contains("信義房屋：失敗"));assertFalse(text.contains("所有平台已同步"));assertEquals(2,api.runCalls)
  api.runFails=true;vm.refresh();runCurrent();assertNotNull(vm.state.value.error);assertEquals("r",vm.state.value.run!!.id)
 }
}
