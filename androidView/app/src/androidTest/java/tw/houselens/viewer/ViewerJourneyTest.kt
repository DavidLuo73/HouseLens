package tw.houselens.viewer

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tw.houselens.viewer.auth.*
import tw.houselens.viewer.data.*
import java.io.File
import tw.houselens.viewer.support.ViewerTestActivity
import tw.houselens.viewer.support.anonymousScreenshot
import java.util.Collections

/** Synthetic HTTP only: production anonymous data, persistence and navigation remain real. */
@OptIn(ExperimentalTestApi::class)
class ViewerJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private inner class Fixture {
        val context = instrumentation.targetContext
        val project = "https://journey-${System.nanoTime()}.supabase.co"
        val directory = File(context.cacheDir, "journey-${System.nanoTime()}")
        val reads = Collections.synchronizedList(mutableListOf<String>())
        val pages = Collections.synchronizedList(mutableListOf<Int>())
        val sdk = createViewerClient(project, "sb_publishable_journey_fixture", MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertTrue(request.headers[HttpHeaders.Authorization] in listOf(null, "Bearer sb_publishable_journey_fixture"))
            val endpoint = request.url.encodedPath.substringAfterLast('/')
            reads += endpoint
            var total = 1
            val body = when (endpoint) {
                "DistrictConfigs" -> """[{"City":"臺北市","District":"大安區","IsEnabled":true},{"City":"臺北市","District":"中正區","IsEnabled":true}]"""
                "viewer_properties" -> {
                    val id = request.url.parameters["id"]?.removePrefix("eq.")?.toInt()
                    val district = request.url.parameters["district"].orEmpty()
                    val ids = if (id != null) listOf(id) else if (district.contains("大安區")) (1..60).toList() else if (district.contains("中正區")) (61..120).toList() else (1..120).toList()
                    total = ids.size
                    val offset = request.url.parameters["offset"]?.toInt() ?: 0
                    if (id == null) pages += offset / 20 + 1
                    ids.drop(offset).take(if (id == null) 20 else 1).joinToString(",", "[", "]") { n ->
                        """{"id":"$n","title":"房源 $n","city":"臺北市","district":"${if(n<=60) "大安區" else "中正區"}","currentTotalPrice":1200,"score":0.9,"status":0} """
                    }
                }
                "Listings" -> """[{"SourceSite":2,"Url":"https://example.test/listing","Title":"合成刊登"}]"""
                "PriceHistoryEntries" -> { total = 100; (1..100).joinToString(",", "[", "]") { n -> """{"Id":"e$n","CapturedAt":"2026-11-07T00:00:00Z","TotalPrice":${1300-n},"ChangeFlag":0}""" } }
                "viewer_top_properties" -> """[{"district":"中正區","rank":1,"id":"61","totalPrice":1200,"score":0.9,"title":"精選房源 61"}]"""
                "viewer_district_stats" -> """[{"district":"中正區","propertyCount":60,"avgUnitPrice":42,"minTotalPrice":1200,"maxTotalPrice":1200,"priceBuckets":[{"range":"1000–1500萬","count":60}],"trend":[{"date":"2026-11-07","avgUnitPrice":42}],"insufficientData":false}]"""
                "CrawlRuns" -> "[]"
                else -> error("Unexpected business endpoint: $endpoint")
            }
            respond(body, HttpStatusCode.OK, headersOf("Content-Type" to listOf("application/json"), "Content-Range" to listOf("0-${total-1}/$total")))
        })
        var graph = DefaultAppGraph(context, sdk, directory)
        val owner get() = graph.owner
        fun start(): ActivityScenario<ViewerTestActivity> {
            ViewerTestActivity.graph = graph
            val scenario = ActivityScenario.launch<ViewerTestActivity>(Intent(context, ViewerTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            InstrumentationRegistry.getArguments().getString("expectedOrientation")?.let { expected ->
                val orientation = when (expected) {
                    "landscape" -> Configuration.ORIENTATION_LANDSCAPE
                    "portrait" -> Configuration.ORIENTATION_PORTRAIT
                    else -> error("Unsupported requested test orientation")
                }
                compose.waitUntil(10_000) {
                    var ready = false
                    scenario.onActivity { activity ->
                        val view = activity.window.decorView
                        val measured = view.width > 0 && view.height > 0 && (view.width > view.height) == (orientation == Configuration.ORIENTATION_LANDSCAPE)
                        ready = activity.resources.configuration.orientation == orientation && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && measured
                    }
                    ready
                }
                scenario.onActivity { activity ->
                    assertEquals(orientation, activity.resources.configuration.orientation)
                    assertTrue(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                }
                compose.waitForIdle()
            }
            return scenario
        }
        fun close() {
            graph.session.close()
            runBlocking { graph.scope.coroutineContext[Job]!!.cancelAndJoin() }
            runBlocking { sdk.close() }
            ViewerTestActivity.graph = null
            directory.deleteRecursively()
        }
    }
    private fun index() = compose.onNodeWithTag("find-list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value().toInt()
    private fun waitFind() {
        compose.waitUntilAtLeastOneExists(hasTestTag("find-list"), 10_000)
        compose.onNodeWithTag("find-list").assertIsDisplayed()
    }
    private fun failureEvidence(label: String, failure: Throwable): Nothing {
        runCatching { anonymousScreenshot("$label-failure") }.onFailure { println("Screenshot failed: ${it.javaClass.simpleName}") }
        val tree = runCatching { compose.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = 30) }.getOrElse { "Semantics unavailable: ${it.javaClass.simpleName}" }
        throw AssertionError("Synthetic failure evidence: $label\n$tree", failure)
    }
    private fun assertUsable(node: SemanticsNodeInteraction): SemanticsNodeInteraction {
        node.assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val visible = node.fetchSemanticsNode().boundsInRoot
        val minimum = 48 * instrumentation.targetContext.resources.displayMetrics.density
        assertTrue("Visible touch width ${visible.width}px must be at least $minimum px", visible.width >= minimum)
        assertTrue("Visible touch height ${visible.height}px must be at least $minimum px", visible.height >= minimum)
        return node
    }
    private fun rootTab(label: String) = compose.onNodeWithText(label, useUnmergedTree = true).performClick()

    @Test fun anonymous_journey_keeps_position_and_favorites_across_recreation_and_new_graph() {
        val f = Fixture()
        val scenario = f.start()
        try {
            waitFind()
            compose.onNodeWithText("篩選").performScrollTo().performClick()
            val district = compose.onAllNodesWithText("大安區").onLast().performScrollTo()
            assertUsable(district).performClick()
            district.assertIsSelected()
            assertUsable(compose.onNodeWithText("套用")).performClick()
            compose.onNodeWithText("大安區", useUnmergedTree = true).assertExists()
            compose.onNodeWithTag("find-list").performScrollToIndex(19)
            compose.waitUntil(10_000) { 2 in f.pages }
            compose.onNodeWithTag("find-list").performScrollToIndex(39)
            compose.waitUntil(10_000) { 3 in f.pages }
            compose.onNodeWithTag("find-list").performScrollToIndex(21)
            compose.onNode(hasContentDescription("加入收藏") and hasAnyAncestor(hasTestTag("property-21")), useUnmergedTree = true).performScrollTo().performClick()
            compose.waitUntil(5000) { runBlocking { f.graph.favorites.observe(f.owner).first().map { it.id } == listOf("21") } }
            compose.onNodeWithText("房源 21").performScrollTo()
            compose.waitForIdle()
            anonymousScreenshot("find")
            val position = index()
            compose.onNodeWithText("房源 21").performClick()
            compose.waitUntil(5000) { f.reads.contains("PriceHistoryEntries") && compose.onAllNodesWithText("詳情載入中").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("detail-list").performScrollToNode(hasText("系統擷取價格紀錄"))
            compose.onNodeWithText("系統擷取價格紀錄").assertIsDisplayed()
            compose.onNodeWithText("展開紀錄（20／100）").performScrollTo().performClick()
            compose.onNodeWithTag("history-e1").performScrollTo().assertExists()
            val historyReads = f.reads.count { it == "PriceHistoryEntries" }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            compose.onNodeWithText("返回").performClick()
            waitFind()
            assertEquals(position, index())
            compose.onNodeWithText("房源 21").assertIsDisplayed()
            assertEquals(historyReads, f.reads.count { it == "PriceHistoryEntries" })
            rootTab("收藏")
            compose.onNodeWithText("房源 21").assertExists()
            rootTab("精選")
            compose.onNodeWithTag("explore-top-list").performScrollToNode(hasText("精選房源 61"))
            compose.onNodeWithText("精選房源 61").performClick()
            compose.onNodeWithText("返回").performClick()
            rootTab("區域")
            compose.onNodeWithTag("district-list").performScrollToNode(hasText("查看本區房源"))
            compose.onNodeWithText("查看本區房源").performClick()
            compose.onNodeWithTag("find-list").performScrollToIndex(0)
            compose.onNodeWithText("中正區", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("復原行政區變更").performScrollTo().performClick()
            compose.onNodeWithText("大安區", useUnmergedTree = true).assertExists()
            assertFalse(f.reads.contains("ViewerMembers"))
            scenario.onActivity { f.graph.session.close(); f.graph.scope.cancel() }
            runBlocking { f.graph.scope.coroutineContext[Job]!!.join() }
            f.graph = DefaultAppGraph(f.context, f.sdk, f.directory)
            ViewerTestActivity.graph = f.graph
            scenario.recreate()
            compose.waitUntil(5000) { runBlocking { f.graph.favorites.observe(f.owner).first().map { it.id } == listOf("21") } }
            rootTab("收藏")
            compose.onNodeWithText("房源 21").assertExists()
        } catch (failure: Throwable) { failureEvidence("journey", failure) } finally { scenario.close(); f.close() }
    }

    @Test fun filter_apply_and_system_back_are_reachable_with_keyboard() {
        val f = Fixture()
        val scenario = f.start()
        try {
            waitFind()
            compose.onNodeWithText("篩選").performScrollTo().performClick()
            val focusPrice=compose.onNodeWithText("最低總價（萬元）").performScrollTo().assert(hasSetTextAction())
            compose.waitUntil(5000) {
                val node=focusPrice.fetchSemanticsNode()
                val full=focusPrice.getUnclippedBoundsInRoot()
                val visible=node.boundsInRoot
                val density=instrumentation.targetContext.resources.displayMetrics.density
                val fullyVisible=visible.height>=(full.bottom-full.top).value*density-1f && visible.width>=(full.right-full.left).value*density-1f
                node.config[SemanticsProperties.Focused] || fullyVisible
            }
            // Dialog focus can already open the IME; avoid a redundant tap during its resize.
            if(!focusPrice.fetchSemanticsNode().config[SemanticsProperties.Focused]) focusPrice.performClick()
            compose.waitUntil(5000) {
                var keyboard = false
                scenario.onActivity { keyboard = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
                keyboard
            }
            val price = compose.onNodeWithText("最低總價（萬元）")
            price.assert(hasSetTextAction()).assertIsFocused()
            assertUsable(price).performTextInput("1100")
            compose.waitForIdle()
            listOf(price,compose.onNodeWithText("最低總價（萬元）",useUnmergedTree=true)).forEach { node ->
                node.assertIsDisplayed()
                val full=node.getUnclippedBoundsInRoot()
                val visible=node.fetchSemanticsNode().boundsInRoot
                val density=instrumentation.targetContext.resources.displayMetrics.density
                assertTrue("Entire price field/label height must be visible: $full versus $visible",visible.height>=(full.bottom-full.top).value*density-1f)
                assertTrue("Entire price field/label width must be visible: $full versus $visible",visible.width>=(full.right-full.left).value*density-1f)
            }
            anonymousScreenshot("keyboard")
            assertUsable(compose.onNodeWithText("重設"))
            assertUsable(compose.onNodeWithText("套用")).performClick()
            compose.onNodeWithContentDescription("移除最低 1100 萬條件").assertExists()
            compose.onNodeWithText("資訊").performClick()
            compose.onNodeWithText("收藏僅儲存在此裝置，不跨裝置同步").assertExists()
            compose.waitForIdle()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            waitFind()
        } catch (failure: Throwable) { failureEvidence("keyboard", failure) } finally { scenario.close(); f.close() }
    }
}
