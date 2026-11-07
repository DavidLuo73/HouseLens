package tw.houselens.viewer.detail

import java.io.IOException
import java.math.BigDecimal
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import tw.houselens.viewer.auth.OwnerKey
import tw.houselens.viewer.data.*
import tw.houselens.viewer.saved.*

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {
    private val owner=OwnerKey("https://test.supabase.co","member")
    private val models=mutableListOf<DetailViewModel>()
    private fun model(api:HouseLensData,store:FavoriteStore)=DetailViewModel("1",api,owner,store).also { models+=it }
    private val p=PropertyDto("1",BigDecimal("1200.25"),title="房源")
    private class Api:HouseLensData {
        var fetch:suspend ()->PropertyDetails?={null}
        override suspend fun detail(id:String)=fetch()
        override suspend fun property(id:String):PropertyDto?=error("unused")
        override suspend fun properties(query:PropertyQuery,page:Int):PropertyPage=error("unused")
        override suspend fun districtOptions()=emptyList<DistrictOptionDto>()
        override suspend fun districtStats()=emptyList<DistrictStatsDto>()
        override suspend fun topRated()=emptyList<TopGroupDto>()
        override suspend fun bigDrops(page:Int,district:String?):DropPage=error("unused")
        override suspend fun latestRun():CrawlRunDto?=null
    }
    private fun fixture(block:suspend (Api,FavoriteStore,CoroutineScope)->Unit)=runBlocking {
        val dispatcher=java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(dispatcher)
        val scope=CoroutineScope(SupervisorJob()+dispatcher)
        val dir=Files.createTempDirectory("detail-test").toFile()
        try { block(Api(),FavoriteStore(dir,scope),scope) }
        finally {
            withContext(dispatcher) { models.forEach { vm -> androidx.lifecycle.ViewModelStore().apply { put("detail",vm);clear() } };models.clear() }
            scope.coroutineContext[Job]!!.cancelAndJoin();Dispatchers.resetMain();dispatcher.close();dir.deleteRecursively()
        }
    }
    private suspend fun await(predicate:()->Boolean) { withTimeout(3000) { while(!predicate()) delay(10) } }
    @Test fun not_found_is_distinct_from_network_failure_and_can_retry()=fixture { api,store,_ ->
        val vm=model(api,store);await { !vm.state.value.loading }
        assertTrue(vm.state.value.notFound);assertNull(vm.state.value.error)
        api.fetch={throw IOException()};vm.refresh();await { vm.state.value.error!=null }
        assertFalse(vm.state.value.notFound)
        api.fetch={PropertyDetails(p,emptyList(),emptyList())};vm.refresh();await { vm.state.value.details!=null }
        assertNull(vm.state.value.error);assertFalse(vm.state.value.notFound)
    }
    @Test fun failed_sources_or_history_retains_complete_old_data_until_retry_succeeds()=fixture { api,store,_ ->
        val old=PropertyDetails(p,listOf(SourceDto(0,"https://example.com/1")),listOf(PriceEntryDto("e","2026-01-01T00:00:00Z",BigDecimal.TEN)))
        api.fetch={old};val vm=model(api,store);await { vm.state.value.details!=null }
        api.fetch={throw IOException("history failed")};vm.refresh();await { vm.state.value.error!=null }
        assertEquals(old,vm.state.value.details)
        api.fetch={old.copy(sources=emptyList())};vm.refresh();await { !vm.state.value.loading && vm.state.value.error==null }
        assertTrue(vm.state.value.details!!.sources.isEmpty())
    }
    @Test fun favorites_use_shared_exact_snapshot_and_removed_detail_can_unsave()=fixture { api,store,_ ->
        api.fetch={PropertyDetails(p,emptyList(),emptyList())};val vm=model(api,store)
        await { vm.state.value.details!=null };vm.toggleFavorite();await { vm.state.value.saved && !vm.state.value.saving }
        assertEquals(BigDecimal("1200.25"),store.observe(owner).first().single().snapshot!!.currentTotalPrice)
        api.fetch={null};vm.refresh();await { vm.state.value.notFound };vm.toggleFavorite();await { !vm.state.value.saved && !vm.state.value.saving }
        assertTrue(store.observe(owner).first().isEmpty())
    }
}