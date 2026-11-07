@file:kotlinx.serialization.UseSerializers(tw.houselens.viewer.data.DecimalSerializer::class)
package tw.houselens.viewer.saved

import java.io.File
import java.math.BigDecimal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import okio.Path.Companion.toPath
import kotlinx.serialization.Serializable
import tw.houselens.viewer.auth.OwnerKey
import tw.houselens.viewer.data.PropertyDto

@Serializable data class PropertySnapshot(
    val id: String, val title: String, val city: String, val district: String,
    val currentTotalPrice: BigDecimal, val currentUnitPrice: BigDecimal? = null,
    val areaPing: BigDecimal? = null, val ageYears: Int? = null, val floor: String? = null,
    val hasParking: Boolean = false, val score: BigDecimal? = null, val status: Int = 0,
    val imageUrl: String? = null, val isNew: Boolean = false, val hasPriceDrop: Boolean = false,
    val latestChangeFlag: Int? = null, val latestChangePercent: BigDecimal? = null,
    val latestIsBigDrop: Boolean = false
)
fun PropertyDto.toSnapshot() = PropertySnapshot(id,title,city,district,currentTotalPrice,currentUnitPrice,
    areaPing,ageYears,floor,hasParking,score,status,imageUrl,isNew,hasPriceDrop,latestChangeFlag,
    latestChangePercent,latestIsBigDrop)
@Serializable data class FavoriteEntry(val id: String, val savedAtEpochMillis: Long, val snapshot: PropertySnapshot?)
class FavoriteStore(private val directory: File, private val scope: CoroutineScope) {
    private val stores = mutableMapOf<String, androidx.datastore.core.DataStore<List<FavoriteEntry>>>()
    private val generations = mutableMapOf<OwnerKey, Long>()
    private val writes = kotlinx.coroutines.sync.Mutex()
    private fun data(owner: OwnerKey): androidx.datastore.core.DataStore<List<FavoriteEntry>> {
        val key = java.security.MessageDigest.getInstance("SHA-256")
            .digest((owner.projectUrl + "\u0000" + owner.userId).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return synchronized(stores) {
            stores.getOrPut(key) {
                androidx.datastore.core.DataStoreFactory.create(
                    storage = androidx.datastore.core.okio.OkioStorage(okio.FileSystem.SYSTEM, FavoriteSerializer) {
                        File(directory, "$key.json").absolutePath.toPath()
                    },
                    scope = CoroutineScope(scope.coroutineContext + kotlinx.coroutines.Dispatchers.IO)
                )
            }
        }
    }
    fun observe(owner: OwnerKey): Flow<List<FavoriteEntry>> = data(owner).data.map { entries ->
        entries.sortedByDescending { it.savedAtEpochMillis }
    }
    suspend fun put(owner: OwnerKey, entry: FavoriteEntry) = writes.withLock {
        require(entry.id.isNotBlank() && (entry.snapshot == null || entry.snapshot.id == entry.id))
        data(owner).updateData { entries -> entries.filterNot { it.id == entry.id } + entry }
        Unit
    }
    suspend fun remove(owner: OwnerKey, id: String): FavoriteEntry? = writes.withLock {
        var removed: FavoriteEntry? = null
        data(owner).updateData { entries -> removed = entries.find { it.id == id }; entries.filterNot { it.id == id } }
        removed
    }
    suspend fun purgeSnapshots(owner: OwnerKey) = writes.withLock {
        generations[owner] = (generations[owner] ?: 0) + 1
        data(owner).updateData { entries -> entries.map { it.copy(snapshot = null) } }
        Unit
    }
    internal suspend fun generation(owner: OwnerKey): Long = writes.withLock { generations[owner] ?: 0 }
    internal suspend fun updateSnapshot(owner: OwnerKey, id: String, snapshot: PropertySnapshot?, generation: Long): Boolean = writes.withLock {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if ((generations[owner] ?: 0) != generation) return@withLock false
        var updated = false
        data(owner).updateData { entries -> entries.map {
            if (it.id == id) { updated = true; it.copy(snapshot = snapshot) } else it
        } }
        updated
    }
}

private object FavoriteSerializer : androidx.datastore.core.okio.OkioSerializer<List<FavoriteEntry>> {
    override val defaultValue = emptyList<FavoriteEntry>()
    override suspend fun readFrom(source: okio.BufferedSource): List<FavoriteEntry> {
        try {
            return tw.houselens.viewer.data.viewerJson.decodeFromString<List<FavoriteEntry>>(source.readUtf8()).also { entries ->
                require(entries.all { it.id.isNotBlank() && (it.snapshot == null || it.snapshot.id == it.id) })
                require(entries.map { it.id }.distinct().size == entries.size)
            }
        } catch (e: kotlinx.serialization.SerializationException) {
            throw androidx.datastore.core.CorruptionException("收藏資料損壞，請保留原檔以供修復", e)
        } catch (e: IllegalArgumentException) {
            throw androidx.datastore.core.CorruptionException("收藏資料損壞，請保留原檔以供修復", e)
        }
    }
    override suspend fun writeTo(t: List<FavoriteEntry>, sink: okio.BufferedSink) {
        sink.write(tw.houselens.viewer.data.viewerJson.encodeToString(t).toByteArray(Charsets.UTF_8))
    }
}