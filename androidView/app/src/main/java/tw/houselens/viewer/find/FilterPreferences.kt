package tw.houselens.viewer.find

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toPath
import tw.houselens.viewer.auth.OwnerKey
import tw.houselens.viewer.data.*

class FilterPreferences(private val directory: File, private val scope: CoroutineScope) {
    private val stores = mutableMapOf<OwnerKey, DataStore<PropertyQuery>>()
    private fun data(owner: OwnerKey): DataStore<PropertyQuery> = synchronized(stores) {
        stores.getOrPut(owner) {
            val key=MessageDigest.getInstance("SHA-256").digest((owner.projectUrl+"\u0000"+owner.userId).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            DataStoreFactory.create(storage=OkioStorage(FileSystem.SYSTEM,QuerySerializer) {
                File(directory,"$key.json").absolutePath.toPath()
            },scope=CoroutineScope(scope.coroutineContext+Dispatchers.IO))
        }
    }
    fun observe(owner: OwnerKey): Flow<PropertyQuery> = data(owner).data
    suspend fun save(owner: OwnerKey, query: PropertyQuery) {
        require(validateQuery(query)==null) { validateQuery(query).orEmpty() }
        currentCoroutineContext().ensureActive()
        data(owner).updateData { query }
    }
}
private object QuerySerializer: OkioSerializer<PropertyQuery> {
    override val defaultValue=PropertyQuery()
    override suspend fun readFrom(source: BufferedSource): PropertyQuery = try {
        viewerJson.decodeFromString<PropertyQuery>(source.readUtf8()).also { require(validateQuery(it)==null) }
    } catch(e: IllegalArgumentException) { throw CorruptionException("篩選條件無法讀取",e) }
    override suspend fun writeTo(t: PropertyQuery,sink: BufferedSink) { sink.writeUtf8(viewerJson.encodeToString(t)) }
}
