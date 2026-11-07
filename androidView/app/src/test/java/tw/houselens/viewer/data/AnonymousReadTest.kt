package tw.houselens.viewer.data

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AnonymousReadTest {
    @Test fun first_read_uses_only_anonymous_get_without_membership_or_auth() = runTest {
        val endpoints = mutableListOf<String>()
        val client = createViewerClient("https://project.supabase.co", "sb_publishable_test", MockEngine { request ->
            endpoints += request.url.encodedPath
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("sb_publishable_test", request.headers["apikey"])
            assertEquals("houselens", request.headers["Accept-Profile"])
            assertTrue(request.headers[HttpHeaders.Authorization] in listOf(null, "Bearer sb_publishable_test"))
            respond("[]", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        })
        try {
            val data = SupabaseHouseLensData(client)
            assertNull(data.property("missing"))
            assertEquals(listOf("/rest/v1/viewer_properties"), endpoints)
        } finally { client.close() }
    }
}
