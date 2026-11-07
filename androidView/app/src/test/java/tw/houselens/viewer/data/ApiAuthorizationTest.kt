package tw.houselens.viewer.data

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ApiAuthorizationTest {
    @Test fun concurrent_401_responses_share_one_refresh_and_retry_once() = runTest {
        var refreshes=0
        var attempts=0
        var denied=0
        val bothArrived=CompletableDeferred<Unit>()
        val client=createViewerClient("https://project.supabase.co","sb_publishable_test", MockEngine { r ->
            if (r.url.encodedPath.endsWith("ViewerMembers")) {
                respond("""[{"UserId":"member"}]""",HttpStatusCode.OK,headersOf("Content-Type","application/json"))
            } else {
                attempts++
                if (refreshes==0) {
                    if(attempts==2) bothArrived.complete(Unit)
                    bothArrived.await()
                    respond("""{"message":"expired"}""",HttpStatusCode.Unauthorized,headersOf("Content-Type","application/json"))
                } else respond("""[{"id":"p","currentTotalPrice":100}]""",HttpStatusCode.OK,headersOf("Content-Type","application/json"))
            }
        })
        try {
            val data=SupabaseHouseLensData(client,{ refreshes++ },{ denied++ },{0})
            val results=awaitAll(async { data.property("p") }, async { data.property("p") })
            assertEquals(listOf("p","p"),results.map { it?.id })
            assertEquals(1,refreshes)
            assertEquals(4,attempts)
            assertEquals(0,denied)
        } finally { client.close() }
    }
    @Test fun persistent_401_stops_after_one_retry() = runTest {
        var refreshes=0
        var attempts=0
        val client=createViewerClient("https://project.supabase.co","sb_publishable_test",MockEngine {
            attempts++
            respond("""{"message":"expired"}""",HttpStatusCode.Unauthorized,headersOf("Content-Type","application/json"))
        })
        try {
            val data=SupabaseHouseLensData(client,{refreshes++},{},{0})
            try { data.property("p"); fail() } catch(e:ApiException) { assertEquals(401,e.status) }
            assertEquals(1,refreshes)
            assertEquals(2,attempts)
        } finally { client.close() }
    }
    @Test fun forbidden_does_not_refresh() = runTest {
        var refreshes=0
        var denied=0
        val client=createViewerClient("https://project.supabase.co","sb_publishable_test",MockEngine {
            respond("""{"message":"denied"}""",HttpStatusCode.Forbidden,headersOf("Content-Type","application/json"))
        })
        try {
            val data=SupabaseHouseLensData(client,{refreshes++},{denied++},{0})
            try { data.property("p"); fail() } catch(e:ApiException) { assertEquals(403,e.status) }
            assertEquals(0,refreshes)
            assertEquals(1,denied)
        } finally { client.close() }
    }
    @Test fun logout_during_refresh_does_not_retry_previous_account() = runTest {
        var version=0L
        var attempts=0
        val client=createViewerClient("https://project.supabase.co","sb_publishable_test",MockEngine {
            attempts++
            respond("""{"message":"expired"}""",HttpStatusCode.Unauthorized,headersOf("Content-Type","application/json"))
        })
        try {
            val data=SupabaseHouseLensData(client,{version++},{},{version})
            try { data.property("p"); fail() } catch(_:CancellationException) {}
            assertEquals(1,attempts)
        } finally { client.close() }
    }
}
