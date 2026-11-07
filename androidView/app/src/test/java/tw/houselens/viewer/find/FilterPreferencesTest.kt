package tw.houselens.viewer.find

import java.math.BigDecimal
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import tw.houselens.viewer.auth.OwnerKey
import tw.houselens.viewer.data.PropertyQuery

class FilterPreferencesTest {
    @Test fun explicit_sort_choices_survive_reopening() = runBlocking {
        for(sort in listOf("score","price","priceDesc","unitPrice","unitPriceDesc","newest")) {
            val directory=Files.createTempDirectory("sort-filters").toFile()
            val owner=OwnerKey("https://one.supabase.co","a")
            var scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
            try {
                val query=PropertyQuery(districts=listOf("中正區"),sortBy=sort)
                FilterPreferences(directory,scope).save(owner,query)
                scope.coroutineContext[Job]!!.cancelAndJoin()
                scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
                assertEquals(query,FilterPreferences(directory,scope).observe(owner).first())
            } finally { scope.coroutineContext[Job]!!.cancelAndJoin();directory.deleteRecursively() }
        }
    }

    @Test fun reopening_restores_only_applied_query_with_owner_isolation_and_decimal_precision() = runBlocking {
        val directory=Files.createTempDirectory("filters").toFile();val owner=OwnerKey("https://one.supabase.co","a")
        var scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            val preferences=FilterPreferences(directory,scope)
            assertEquals("unitPrice",preferences.observe(owner).first().sortBy)
            val q=PropertyQuery(districts=listOf("中正區"),sourceSites=listOf(0,1,2,3,4,5,6),minPrice=BigDecimal("9007199254740993.01"),sortBy="price")
            preferences.save(owner,q);scope.coroutineContext[Job]!!.cancelAndJoin()
            scope=CoroutineScope(SupervisorJob()+Dispatchers.IO);val reopened=FilterPreferences(directory,scope)
            assertEquals(q,reopened.observe(owner).first());assertEquals(PropertyQuery(),reopened.observe(owner.copy(userId="b")).first())
            assertEquals(PropertyQuery(),reopened.observe(owner.copy(projectUrl="https://two.supabase.co")).first())
            try { reopened.save(owner,PropertyQuery(minPrice=BigDecimal("-1")));fail("invalid query saved") } catch(_:IllegalArgumentException) { }
            assertEquals(q,reopened.observe(owner).first())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin();directory.deleteRecursively() }
    }
}
