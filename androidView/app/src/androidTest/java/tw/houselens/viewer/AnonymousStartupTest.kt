package tw.houselens.viewer

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import tw.houselens.viewer.auth.OwnerKey
import tw.houselens.viewer.saved.FavoriteEntry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tw.houselens.viewer.data.*
import java.io.File
import tw.houselens.viewer.support.ViewerTestActivity
import tw.houselens.viewer.support.anonymousScreenshot

@OptIn(ExperimentalTestApi::class)
class AnonymousStartupTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun production_root_opens_find_without_login_and_information_has_local_disclosure() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val project = "https://anonymous-${System.nanoTime()}.supabase.co"
        val directory = File(context.cacheDir, "anonymous-${System.nanoTime()}")
        val reads = java.util.Collections.synchronizedList(mutableListOf<String>())
        val sdk = createViewerClient(project, "sb_publishable_fixture", MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertTrue(request.headers[HttpHeaders.Authorization] in listOf(null, "Bearer sb_publishable_fixture"))
            reads += request.url.encodedPath.substringAfterLast('/')
            respond("[]", HttpStatusCode.OK, headersOf("Content-Type" to listOf("application/json"), "Content-Range" to listOf("0-0/0")))
        })
        val graph = DefaultAppGraph(context, sdk, directory)
        val formerOwner = OwnerKey(project, "11111111-1111-1111-1111-111111111111")
        runBlocking { graph.favorites.put(formerOwner, FavoriteEntry("former", 1, null)) }
        ViewerTestActivity.graph = graph
        val scenario = ActivityScenario.launch<ViewerTestActivity>(Intent(context, ViewerTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            compose.waitUntilAtLeastOneExists(hasText("篩選"), 10_000)
            compose.onNodeWithText("篩選").assertIsDisplayed()
            InstrumentationRegistry.getArguments().getString("expectedOrientation")?.let { expected ->
                val orientation = if (expected == "landscape") Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                compose.waitUntil(5000) { context.resources.configuration.orientation == orientation }
                assertEquals(orientation, context.resources.configuration.orientation)
            }
            compose.onNodeWithText("找房", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("資訊").performClick()
            compose.onNodeWithText("資訊").assertExists()
            compose.onNodeWithText("帳號").assertDoesNotExist()
            compose.onNodeWithText("收藏僅儲存在此裝置，不跨裝置同步").assertExists()
            compose.onNodeWithText("登入").assertDoesNotExist()
            compose.onNodeWithText("登出").assertDoesNotExist()
            compose.waitForIdle()
            anonymousScreenshot("info")
            assertTrue(reads.contains("viewer_properties"))
            assertFalse(reads.contains("ViewerMembers"))
            compose.onNodeWithText("返回").performClick()
            compose.onNodeWithText("收藏", useUnmergedTree = true).performClick()
            compose.onNodeWithText("尚無收藏").assertExists()
            assertEquals(listOf("former"), runBlocking { graph.favorites.observe(formerOwner).first().map { it.id } })
        } finally {
            scenario.close()
            graph.session.close()
            runBlocking { graph.scope.coroutineContext[Job]!!.cancelAndJoin() }
            runBlocking { sdk.close() }
            ViewerTestActivity.graph = null
            directory.deleteRecursively()
        }
    }
}
