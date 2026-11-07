package tw.houselens.viewer.saved

import java.io.File
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

fun property(id: String, status: Int = 0) = PropertyDto(id,BigDecimal("1200"),title="房源 $id",city="台北市",district="中正區",status=status)
class SummaryApi(private val read: suspend (String) -> PropertyDto?) : HouseLensData {
    override suspend fun property(id: String) = read(id)
    override suspend fun detail(id: String): PropertyDetails? = error("Saved must never fetch detail")
    override suspend fun properties(query: PropertyQuery,page: Int): PropertyPage = error("No scan")
    override suspend fun topRated(): List<TopGroupDto> = error("unused")
    override suspend fun bigDrops(page: Int,district: String?): DropPage = error("unused")
    override suspend fun districtStats(): List<DistrictStatsDto> = error("unused")
    override suspend fun districtOptions(): List<DistrictOptionDto> = error("unused")
    override suspend fun latestRun(): CrawlRunDto? = error("unused")
}
class SavedViewModelTest {
    private val owner = OwnerKey("https://one.supabase.co","a")
    private suspend fun waitFor(predicate: () -> Boolean) { withTimeout(5000) { while (!predicate()) delay(10) } }
    @Test fun refreshes_only_requested_saved_ids_with_at_most_three_summary_requests() = runBlocking {
        val directory = Files.createTempDirectory("saved-vm").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = FavoriteStore(directory,scope)
            (1..21).forEach { store.put(owner,FavoriteEntry("$it",it.toLong(),property("$it").toSnapshot())) }
            val active = java.util.concurrent.atomic.AtomicInteger()
            val max = java.util.concurrent.atomic.AtomicInteger()
            val seen = java.util.Collections.synchronizedList(mutableListOf<String>())
            val api = SummaryApi { id ->
                seen += id; val count=active.incrementAndGet(); max.accumulateAndGet(count,::maxOf)
                delay(30); active.decrementAndGet(); property(id)
            }
            val vm=SavedViewModel(owner,api,store,scope)
            waitFor { vm.state.value.entries.size == 21 }
            assertTrue(seen.isEmpty())
            vm.refreshVisible(listOf("1","2","3","4","5","6","missing"))
            waitFor { vm.state.value.statuses.values.count { it == RefreshStatus.UPDATED } == 6 }
            assertEquals(setOf("1","2","3","4","5","6"),seen.toSet())
            assertTrue(max.get() <= 3); assertEquals(3,max.get())
            vm.refreshVisible(listOf("7")); waitFor { vm.state.value.statuses["7"] == RefreshStatus.UPDATED }
            assertEquals(7,seen.size)
        } finally { scope.cancel(); directory.deleteRecursively() }
    }
    @Test fun missing_offline_and_delisted_have_distinct_states_and_keep_snapshots() = runBlocking {
        val directory = Files.createTempDirectory("saved-vm").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store=FavoriteStore(directory,scope)
            (1..3).forEach { store.put(owner,FavoriteEntry("$it",it.toLong(),property("$it").toSnapshot())) }
            val vm=SavedViewModel(owner,SummaryApi { when(it) { "1" -> null; "2" -> throw IOException(); else -> property(it,1) } },store,scope)
            waitFor { vm.state.value.entries.size == 3 }
            vm.refreshVisible(listOf("1","2","3"))
            waitFor { vm.state.value.statuses.size == 3 }
            assertEquals(RefreshStatus.REMOVED,vm.state.value.statuses["1"])
            assertEquals(RefreshStatus.TEMPORARY_FAILURE,vm.state.value.statuses["2"])
            assertEquals(RefreshStatus.UPDATED,vm.state.value.statuses["3"])
            assertNotNull(vm.state.value.entries.single { it.id=="1" }.snapshot)
            assertEquals(1,store.observe(owner).first().single { it.id=="3" }.snapshot!!.status)
        } finally { scope.cancel(); directory.deleteRecursively() }
    }
    @Test fun slow_old_session_cannot_refill_same_or_other_owner_after_purge() = runBlocking {
        val directory = Files.createTempDirectory("saved-vm").toFile()
        val storeScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val sessionScope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>()
        try {
            val store=FavoriteStore(directory,storeScope)
            store.put(owner,FavoriteEntry("1",7,property("1").toSnapshot()))
            val vm=SavedViewModel(owner,SummaryApi { withContext(NonCancellable) { entered.complete(Unit);release.await();property(it) } },store,sessionScope)
            waitFor { vm.state.value.entries.size==1 }; vm.refreshVisible(listOf("1"));entered.await()
            sessionScope.cancel(); store.purgeSnapshots(owner); release.complete(Unit)
            sessionScope.coroutineContext[Job]!!.join()
            assertNull(store.observe(owner).first().single().snapshot)
            assertTrue(store.observe(owner.copy(userId="b")).first().isEmpty())
        } finally { release.complete(Unit);sessionScope.cancel();storeScope.cancel();directory.deleteRecursively() }
    }
    @Test fun remove_and_undo_restore_original_time() = runBlocking {
        val directory=Files.createTempDirectory("saved-vm").toFile();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val store=FavoriteStore(directory,scope);store.put(owner,FavoriteEntry("1",7,null))
            val vm=SavedViewModel(owner,SummaryApi { property(it) },store,scope)
            waitFor { vm.state.value.entries.size==1 };vm.remove("1")
            waitFor { vm.state.value.undoEntry!=null };assertTrue(vm.state.value.entries.isEmpty())
            vm.undo();waitFor { vm.state.value.entries.size==1 && vm.state.value.undoEntry==null }
            assertEquals(7L,store.observe(owner).first().single().savedAtEpochMillis)
        } finally { scope.cancel();directory.deleteRecursively() }
    }
    @Test fun failed_remove_restores_heart_and_reports_storage_error() = runBlocking {
        val directory=Files.createTempDirectory("saved-vm").toFile();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val store=FavoriteStore(directory,scope);store.put(owner,FavoriteEntry("1",7,null))
            val vm=SavedViewModel(owner,SummaryApi { property(it) },store,scope)
            waitFor { vm.state.value.entries.size==1 }
            val file=directory.listFiles()!!.single { it.isFile };val blocker=File(file.path+".tmp")
            blocker.mkdirs();File(blocker,"block").writeText("blocked")
            vm.remove("1");waitFor { vm.state.value.storageError!=null }
            assertEquals(listOf("1"),vm.state.value.entries.map { it.id });assertNull(vm.state.value.undoEntry)
        } finally { scope.cancel();directory.deleteRecursively() }
    }
    @Test fun corrupted_store_surfaces_storage_error_instead_of_empty_success() = runBlocking {
        val directory=Files.createTempDirectory("saved-vm").toFile();val firstScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val first=FavoriteStore(directory,firstScope);first.put(owner,FavoriteEntry("1",7,null));firstScope.coroutineContext[Job]!!.cancelAndJoin()
        directory.listFiles()!!.single { it.isFile }.writeText("bad")
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try { val vm=SavedViewModel(owner,SummaryApi { property(it) },FavoriteStore(directory,scope),scope)
            waitFor { vm.state.value.storageError!=null };assertNull(vm.state.value.undoEntry)
        } finally { scope.cancel();directory.deleteRecursively() }
    }
    @Test fun failed_refresh_keeps_old_snapshot_and_never_marks_success() = runBlocking {
        val directory=Files.createTempDirectory("saved-vm").toFile();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val store=FavoriteStore(directory,scope);store.put(owner,FavoriteEntry("1",7,property("1").toSnapshot()))
            val vm=SavedViewModel(owner,SummaryApi { property(it).copy(title="new title") },store,scope)
            waitFor { vm.state.value.entries.size==1 }
            val file=directory.listFiles()!!.single { it.isFile };val blocker=File(file.path+".tmp")
            blocker.mkdirs();File(blocker,"block").writeText("blocked")
            vm.refreshVisible(listOf("1"));waitFor { vm.state.value.statuses["1"]==RefreshStatus.TEMPORARY_FAILURE }
            assertNotNull(vm.state.value.storageError)
            assertEquals("房源 1",vm.state.value.entries.single().snapshot!!.title)
            assertEquals("房源 1",store.observe(owner).first().single().snapshot!!.title)
        } finally { scope.cancel();directory.deleteRecursively() }
    }
    @Test fun failed_undo_keeps_entry_available_for_retry_without_claiming_it_was_saved() = runBlocking {
        val directory=Files.createTempDirectory("saved-vm").toFile();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val store=FavoriteStore(directory,scope);store.put(owner,FavoriteEntry("1",7,null))
            val vm=SavedViewModel(owner,SummaryApi { property(it) },store,scope)
            waitFor { vm.state.value.entries.size==1 };vm.remove("1");waitFor { vm.state.value.undoEntry!=null }
            val file=directory.listFiles()!!.single { it.isFile };val blocker=File(file.path+".tmp")
            blocker.mkdirs();File(blocker,"block").writeText("blocked")
            vm.undo();waitFor { vm.state.value.storageError!=null }
            assertTrue(vm.state.value.entries.isEmpty());assertEquals(7L,vm.state.value.undoEntry!!.savedAtEpochMillis)
            assertTrue(store.observe(owner).first().isEmpty())
            blocker.deleteRecursively();vm.undo()
            waitFor { vm.state.value.undoEntry==null && vm.state.value.entries.size==1 }
            assertNull(vm.state.value.storageError)
            assertEquals(7L,store.observe(owner).first().single().savedAtEpochMillis)
        } finally { scope.cancel();directory.deleteRecursively() }
    }
    @Test fun scrolling_skips_obsolete_queued_requests_but_allows_them_when_visible_again() = runBlocking {
        val directory=Files.createTempDirectory("saved-scroll").toFile()
        val scope=CoroutineScope(coroutineContext + SupervisorJob())
        val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val seen=mutableListOf<String>()
        try {
            val store=FavoriteStore(directory,scope)
            (1..9).forEach { store.put(owner,FavoriteEntry("$it",it.toLong(),null)) }
            val vm=SavedViewModel(owner,SummaryApi { id ->
                seen += id
                if(id in listOf("1","2","3")) {
                    if(seen.size==3) started.complete(Unit)
                    release.await()
                }
                property(id)
            },store,scope)
            waitFor { vm.state.value.entries.size==9 }
            vm.refreshVisible(listOf("1","2","3","4","5","6"))
            withTimeout(5000) { started.await() }
            assertEquals(listOf("1","2","3"),seen)
            vm.refreshVisible(listOf("7","8","9"))
            release.complete(Unit)
            waitFor { listOf("7","8","9").all { vm.state.value.statuses[it]==RefreshStatus.UPDATED } }
            assertEquals(setOf("1","2","3","7","8","9"),seen.toSet())
            vm.refreshVisible(listOf("4","5","6"))
            waitFor { listOf("4","5","6").all { vm.state.value.statuses[it]==RefreshStatus.UPDATED } }
            assertEquals(9,seen.size)
            assertEquals(setOf("1","2","3","4","5","6","7","8","9"),seen.toSet())
        } finally { release.complete(Unit);scope.coroutineContext[Job]!!.cancelAndJoin();directory.deleteRecursively() }
    }
    @Test fun retrying_one_card_keeps_other_visible_requests_queued() = runBlocking {
        val directory=Files.createTempDirectory("saved-retry").toFile()
        val scope=CoroutineScope(coroutineContext + SupervisorJob())
        val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val seen=mutableListOf<String>()
        try {
            val store=FavoriteStore(directory,scope)
            (1..6).forEach { store.put(owner,FavoriteEntry("$it",it.toLong(),null)) }
            val vm=SavedViewModel(owner,SummaryApi { id ->
                seen += id
                if(id=="1" && seen.count { it=="1" }==1) throw IOException("retryable")
                if(id in listOf("2","3","4")) {
                    if(seen.containsAll(listOf("2","3","4"))) started.complete(Unit)
                    release.await()
                }
                property(id)
            },store,scope)
            waitFor { vm.state.value.entries.size==6 }
            vm.refreshVisible(listOf("1","2","3","4","5","6"))
            withTimeout(5000) { started.await() }
            assertEquals(RefreshStatus.TEMPORARY_FAILURE,vm.state.value.statuses["1"])
            assertEquals(listOf("1","2","3","4"),seen)
            vm.retry("1")
            release.complete(Unit)
            waitFor { vm.state.value.statuses["1"]==RefreshStatus.UPDATED }
            assertEquals(RefreshStatus.UPDATED,vm.state.value.statuses["5"])
            assertEquals(RefreshStatus.UPDATED,vm.state.value.statuses["6"])
            assertEquals(setOf("1","2","3","4","5","6"),seen.toSet())
            assertEquals(2,seen.count { it=="1" });assertEquals(7,seen.size)
        } finally { release.complete(Unit);scope.coroutineContext[Job]!!.cancelAndJoin();directory.deleteRecursively() }
    }
    @Test fun returning_from_detail_retains_loaded_summaries_until_explicit_retry() = runBlocking {
        val directory=Files.createTempDirectory("saved-return").toFile()
        val scope=CoroutineScope(coroutineContext+SupervisorJob())
        try {
            val store=FavoriteStore(directory,scope)
            store.put(owner,FavoriteEntry("1",1,null))
            var reads=0
            val vm=SavedViewModel(owner,SummaryApi { reads++;property(it) },store,scope)
            waitFor { vm.state.value.entries.size==1 }
            vm.refreshVisible(listOf("1"));waitFor { vm.state.value.statuses["1"]==RefreshStatus.UPDATED }
            vm.refreshVisible(emptyList());vm.refreshVisible(listOf("1"));yield()
            assertEquals("Returning to the retained list must not fetch again",1,reads)
            assertEquals("房源 1",vm.state.value.entries.single().snapshot!!.title)
            vm.retry("1");yield();assertEquals(2,reads)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin();directory.deleteRecursively() }
    }
}