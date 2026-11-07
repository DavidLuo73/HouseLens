package tw.houselens.viewer.data

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ApiAuthorizationTest {
    @Test fun anonymous_401_and_403_are_finite_errors_without_auth_or_membership_retry() = runTest {
        for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden)) {
            val paths = mutableListOf<String>()
            val client = createViewerClient("https://project.supabase.co", "sb_publishable_test", MockEngine { request ->
                paths += request.url.encodedPath
                respond("{\"message\":\"denied\"}", status, headersOf("Content-Type", "application/json"))
            })
            try {
                try { SupabaseHouseLensData(client).property("p"); fail() }
                catch (e: ApiException) { assertEquals(status.value, e.status) }
                assertEquals(listOf("/rest/v1/viewer_properties"), paths)
            } finally { client.close() }
        }
    }
    @Test fun cancellation_is_not_reported_as_a_network_error() = runTest {
        val client = createViewerClient("https://project.supabase.co", "sb_publishable_test", MockEngine { throw CancellationException("cancelled") })
        try {
            try { SupabaseHouseLensData(client).property("p"); fail() }
            catch (_: CancellationException) { }
        } finally { client.close() }
    }
}
