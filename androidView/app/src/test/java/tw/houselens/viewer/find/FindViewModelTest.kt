package tw.houselens.viewer.find

import androidx.lifecycle.SavedStateHandle
import java.io.IOException
import java.math.BigDecimal
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import tw.houselens.viewer.auth.OwnerKey
import tw.houselens.viewer.data.*
import tw.houselens.viewer.saved.*

private fun testProperty(id: String) = PropertyDto(id, BigDecimal("1200.25"), title="房源 $id")
private open class FindApi : HouseLensData {
    val requests = java.util.Collections.synchronizedList(mutableListOf<Pair<PropertyQuery,Int>>())
    var fetch: suspend (PropertyQuery,Int) -> PropertyPage = { _,page -> PropertyPage(60,page,20,listOf(testProperty("$page"))) }
    var optionsFail = false
    override suspend fun properties(query: PropertyQuery,page: Int): PropertyPage { requests += query to page; return fetch(query,page) }
    override suspend fun districtOptions(): List<DistrictOptionDto> {
        if(optionsFail) throw IOException()
        return listOf(DistrictOptionDto("臺北市","中正區"),DistrictOptionDto("基隆市","中正區"),DistrictOptionDto("臺北市","大安區",false))
    }
    override suspend fun districtStats() = listOf(DistrictStatsDto("未知區"))
    override suspend fun property(id: String): PropertyDto? = error("unused")
    override suspend fun detail(id: String): PropertyDetails? = error("unused")
    override suspend fun topRated(): List<TopGroupDto> = error("unused")
    override suspend fun bigDrops(page: Int,district: String?): DropPage = error("unused")
    override suspend fun latestRun(): CrawlRunDto? = error("unused")
}
class FindViewModelTest {
    private val owner=OwnerKey("https://one.supabase.co","member")
    private suspend fun waitFor(predicate: () -> Boolean) { withTimeout(3000) { while(!predicate()) delay(10) } }
    private suspend fun fixture(block: suspend (FindApi, FindViewModel, FilterPreferences, SavedStateHandle, FavoriteStore) -> Unit) {
        val directory=Files.createTempDirectory("find-test").toFile()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val api=FindApi();val preferences=FilterPreferences(directory.resolve("filters"),scope)
            val favorites=FavoriteStore(directory.resolve("favorites"),scope);val handle=SavedStateHandle()
            val vm=FindViewModel(owner,api,favorites,preferences,handle,scope)
            waitFor { api.requests.isNotEmpty() && !vm.state.value.initialLoading }
            block(api,vm,preferences,handle,favorites)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin();directory.deleteRecursively() }
    }
    @Test fun slow_cancel_ignoring_A_cannot_replace_fast_B() = runBlocking { fixture { api,vm,_,_,_ ->
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        api.fetch={ q,p -> if(q.districts==listOf("A")) withContext(NonCancellable) { entered.complete(Unit);release.await();PropertyPage(1,p,20,listOf(testProperty("A"))) }
            else PropertyPage(1,p,20,listOf(testProperty("B"))) }
        try {
            vm.apply(PropertyQuery(districts=listOf("A")));entered.await()
            val b=PropertyQuery(districts=listOf("B"));vm.apply(b)
            waitFor { vm.state.value.items.firstOrNull()?.id=="B" };release.complete(Unit);delay(100)
            assertEquals(b,vm.state.value.query);assertEquals(listOf("B"),vm.state.value.items.map { it.id })
        } finally { release.complete(Unit) }
    } }
    @Test fun append_is_single_flight_deduplicates_three_pages_and_failure_keeps_items() = runBlocking { fixture { api,vm,_,_,_ ->
        val release=CompletableDeferred<Unit>()
        api.fetch={ _,p -> if(p==2) release.await(); PropertyPage(100,p,20,listOf(testProperty("1"),testProperty("$p"))) }
        vm.loadNext();vm.loadNext();waitFor { api.requests.any { it.second==2 } };release.complete(Unit)
        waitFor { vm.state.value.loadedPage==2 };vm.loadNext();waitFor { vm.state.value.loadedPage==3 }
        assertEquals(listOf("1","2","3"),vm.state.value.items.map { it.id })
        assertEquals(1,api.requests.count { it.second==2 })
        api.fetch={ _,_ -> throw IOException() };vm.loadNext();waitFor { vm.state.value.appendError!=null }
        assertEquals(listOf("1","2","3"),vm.state.value.items.map { it.id });assertEquals(3,vm.state.value.loadedPage)
    } }
    @Test fun refresh_failure_retains_all_pages_and_success_replaces_later_pages() = runBlocking { fixture { api,vm,_,_,_ ->
        vm.loadNext();waitFor { vm.state.value.loadedPage==2 };val previous=vm.state.value.items
        api.fetch={ _,_ -> throw IOException() };vm.refresh();waitFor { vm.state.value.error!=null }
        assertEquals(previous,vm.state.value.items);assertEquals(2,vm.state.value.loadedPage)
        api.fetch={ _,p -> PropertyPage(1,p,20,listOf(testProperty("fresh"))) };vm.refresh()
        waitFor { vm.state.value.items.firstOrNull()?.id=="fresh" };assertEquals(1,vm.state.value.loadedPage)
    } }
    @Test fun query_switch_marks_retained_results_and_saves_applied_query_even_on_network_failure() = runBlocking { fixture { api,vm,prefs,handle,_ ->
        val old=vm.state.value.items;val q=PropertyQuery(sourceSites=listOf(0,1,2,3,4,5,6),minPrice=BigDecimal("9007199254740993.01"),hasParking=false,priceDropped=true,status=1,sortBy="unitPrice")
        api.fetch={ _,_ -> throw IOException() };vm.apply(q);waitFor { vm.state.value.error!=null }
        assertEquals(q,vm.state.value.query);assertEquals(old,vm.state.value.items)
        assertEquals(PropertyQuery(),vm.state.value.resultQuery);assertEquals(q,prefs.observe(owner).first())
        assertNotNull(handle.get<String>("find.query"))
        val count=api.requests.size;vm.apply(q.copy(minPrice=BigDecimal("-1")));delay(100)
        assertEquals(count,api.requests.size);assertEquals(q,vm.state.value.query)
        api.fetch={ _,p -> PropertyPage(0,p,20,emptyList()) };vm.apply(PropertyQuery())
        waitFor { vm.state.value.resultQuery==PropertyQuery() && !vm.state.value.refreshing }
        assertEquals(1,api.requests.last().second);assertTrue(vm.state.value.items.isEmpty())
    } }
    @Test fun options_failure_does_not_block_properties_and_retry_merges_same_names_and_disabled_district() = runBlocking { fixture { api,vm,_,_,_ ->
        api.optionsFail=true;vm.reloadOptions();waitFor { vm.state.value.optionsError!=null }
        assertFalse(vm.state.value.items.isEmpty())
        api.optionsFail=false;vm.reloadOptions();waitFor { vm.state.value.optionsError==null && vm.state.value.districtChoices.size==3 }
        assertEquals("所有同名行政區",vm.state.value.districtChoices.first { it.name=="中正區" }.cityLabel)
        assertTrue(vm.state.value.districtChoices.any { it.name=="大安區" })
    } }
    @Test fun analytics_overwrites_only_districts_and_undo_restores_once() = runBlocking { fixture { _,vm,_,_,_ ->
        val q=PropertyQuery(sourceSites=listOf(3),districts=listOf("大安區"),sortBy="newest")
        vm.apply(q);waitFor { vm.state.value.resultQuery==q }
        vm.setDistrictFromAnalytics("中正區");assertEquals(q.copy(districts=listOf("中正區")),vm.state.value.query)
        vm.undoDistrictChange();assertEquals(q,vm.state.value.query);assertFalse(vm.state.value.canUndoDistrict)
        vm.undoDistrictChange();assertEquals(q,vm.state.value.query)
    } }
    @Test fun favorite_toggle_keeps_exact_snapshot_and_return_target() = runBlocking { fixture { _,vm,_,handle,store ->
        val p=testProperty("saved");vm.toggleFavorite(p);waitFor { "saved" in vm.state.value.favoriteIds }
        assertEquals(BigDecimal("1200.25"),store.observe(owner).first().single().snapshot!!.currentTotalPrice)
        vm.rememberTarget("saved",true);assertEquals("saved",handle.get<String>("find.target"));assertEquals(true,handle.get<Boolean>("find.history"))
        vm.toggleFavorite(p);waitFor { vm.state.value.favoriteIds.isEmpty() }
    } }
    @Test fun removing_each_platform_chip_uses_first_page_and_keeps_other_filters() = runBlocking { fixture { api,vm,_,_,_ ->
        val q=PropertyQuery(sourceSites=listOf(0,1,2,3,4,5,6),districts=listOf("歷史區"),hasParking=false)
        vm.apply(q);waitFor { vm.state.value.resultQuery==q };vm.loadNext();waitFor { vm.state.value.loadedPage==2 }
        val chips=queryChips(q)
        assertEquals(9,chips.size)
        chips.filter { it.label in listOf("591","樂屋網","信義房屋","永慶房屋","好房網","住商不動產","中信房屋") }.forEach { chip ->
            vm.apply(chip.removed);waitFor { vm.state.value.resultQuery==chip.removed && !vm.state.value.refreshing }
            assertEquals(1,vm.state.value.loadedPage);assertEquals(1,api.requests.last().second)
            assertEquals(listOf("歷史區"),vm.state.value.query.districts);assertEquals(false,vm.state.value.query.hasParking)
            assertEquals(6,vm.state.value.query.sourceSites.size)
        }
    } }
    @Test fun process_recreation_restores_query_and_target_but_restarts_first_page() = runBlocking {
        val directory=Files.createTempDirectory("find-restore").toFile();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val q=PropertyQuery(districts=listOf("中正區"),sortBy="price")
            val handle=SavedStateHandle(mapOf("find.query" to viewerJson.encodeToString(PropertyQuery.serializer(),q),"find.target" to "detail-id","find.history" to true))
            val api=FindApi();val vm=FindViewModel(owner,api,FavoriteStore(directory.resolve("saved"),scope),FilterPreferences(directory.resolve("filters"),scope),handle,scope)
            waitFor { vm.state.value.loadedPage==1 }
            assertEquals(q,vm.state.value.query);assertEquals(listOf(1),api.requests.map { it.second });assertTrue(vm.state.value.restored)
            assertEquals("detail-id",handle.get<String>("find.target"));assertEquals(0,vm.scrollIndex)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin();directory.deleteRecursively() }
    }
    @Test fun immediate_refresh_cannot_cancel_saving_the_applied_query() = runBlocking {
        val directory=Files.createTempDirectory("find-persist-race").toFile()
        val dispatcher=java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope=CoroutineScope(SupervisorJob()+dispatcher)
        try {
            val preferences=FilterPreferences(directory.resolve("filters"),scope);val api=FindApi()
            val vm=FindViewModel(owner,api,FavoriteStore(directory.resolve("saved"),scope),preferences,SavedStateHandle(),scope)
            waitFor { vm.state.value.loadedPage==1 }
            val q=PropertyQuery(districts=listOf("套用後立即刷新"))
            api.fetch={ _,_ -> throw IOException() }
            withContext(dispatcher) { vm.apply(PropertyQuery(districts=listOf("舊條件")));vm.apply(q);vm.refresh() }
            waitFor { vm.state.value.error!=null }
            assertEquals(q,preferences.observe(owner).first())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin();dispatcher.close();directory.deleteRecursively() }
    }
}

