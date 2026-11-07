package tw.houselens.viewer.saved

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import tw.houselens.viewer.auth.OwnerKey

class FavoriteStoreTest {
    private val owner = OwnerKey("https://one.supabase.co", "a")
    @Test fun restart_preserves_order_and_owner_origin_isolation() = runBlocking {
        val directory = Files.createTempDirectory("favorites").toFile()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = FavoriteStore(directory, firstScope)
        store.put(owner, FavoriteEntry("old", 10, null))
        store.put(owner, FavoriteEntry("new", 20, null))
        firstScope.coroutineContext[Job]!!.cancelAndJoin()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val recreated = FavoriteStore(directory, scope)
            assertEquals(listOf("new", "old"), recreated.observe(owner).first().map { it.id })
            assertTrue(recreated.observe(owner.copy(userId="b")).first().isEmpty())
            assertTrue(recreated.observe(owner.copy(projectUrl="https://two.supabase.co")).first().isEmpty())
        } finally { scope.cancel(); directory.deleteRecursively() }
    }
    @Test fun concurrent_writes_and_remove_preserve_unrelated_entries_and_undo_time() = runBlocking {
        val directory = Files.createTempDirectory("favorites").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = FavoriteStore(directory, scope)
            store.put(owner, FavoriteEntry("remove", 7, null))
            coroutineScope { (1..30).map { launch { store.put(owner, FavoriteEntry("$it", it.toLong(), null)) } } + launch { store.remove(owner,"remove") } }
            assertEquals(30, store.observe(owner).first().size)
            val removed = store.remove(owner,"1")!!
            store.put(owner, removed)
            assertEquals(1L, store.observe(owner).first().single { it.id == "1" }.savedAtEpochMillis)
        } finally { scope.cancel(); directory.deleteRecursively() }
    }
    @Test fun purge_keeps_ids_but_rejects_late_same_owner_snapshot_write() = runBlocking {
        val directory = Files.createTempDirectory("favorites").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = FavoriteStore(directory, scope)
            store.put(owner, FavoriteEntry("1", 7, property("1").toSnapshot()))
            val generation = store.generation(owner)
            store.purgeSnapshots(owner)
            assertFalse(store.updateSnapshot(owner,"1",property("1").toSnapshot(),generation))
            assertEquals(FavoriteEntry("1",7,null),store.observe(owner).first().single())
        } finally { scope.cancel(); directory.deleteRecursively() }
    }
    @Test fun corrupted_file_is_not_overwritten() = runBlocking {
        val directory = Files.createTempDirectory("favorites").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = FavoriteStore(directory, scope)
        store.put(owner, FavoriteEntry("1", 7, null))
        scope.coroutineContext[Job]!!.cancelAndJoin()
        val file = directory.listFiles()!!.single { it.isFile }
        file.writeText("broken favorites")
        val nextScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val recreated = FavoriteStore(directory,nextScope)
            assertTrue(runCatching { recreated.observe(owner).first() }.isFailure)
            assertTrue(runCatching { recreated.put(owner,FavoriteEntry("2",8,null)) }.isFailure)
            assertEquals("broken favorites",file.readText())
        } finally { nextScope.cancel(); directory.deleteRecursively() }
    }
    @Test fun persistence_failure_does_not_publish_success() = runBlocking {
        val directory = Files.createTempDirectory("favorites").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = FavoriteStore(directory, scope)
            store.put(owner, FavoriteEntry("1", 7, null))
            val file = directory.listFiles()!!.single { it.isFile }
            val blocker = File(file.path + ".tmp")
            blocker.mkdirs(); File(blocker,"block").writeText("force atomic write failure")
            assertTrue(runCatching { store.put(owner,FavoriteEntry("2",8,null)) }.isFailure)
            assertEquals(listOf("1"),store.observe(owner).first().map { it.id })
        } finally { scope.cancel(); directory.deleteRecursively() }
    }
}