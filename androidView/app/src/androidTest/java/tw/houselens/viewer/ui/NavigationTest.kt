package tw.houselens.viewer.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tw.houselens.viewer.support.TestGraph
import tw.houselens.viewer.support.captureScreenshot
import tw.houselens.viewer.saved.*

class NavigationTest {
    @get:Rule val compose=createEmptyComposeRule()
    private fun mount(host:ComponentActivity,graph:TestGraph) { host.setContent { HouseLensTheme { if(graph.active) HouseLensNav(graph,graph.owner) else androidx.compose.material3.Text("已登出") } } }
    private fun fixture(block:(TestGraph,ActivityScenario<ComponentActivity>)->Unit) {
        val graph=TestGraph()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val scenario=ActivityScenario.launch<ComponentActivity>(Intent(context,ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        scenario.onActivity { mount(it,graph) }
        try { block(graph,scenario) } finally { scenario.close();graph.dispose() }
    }
    @Test fun find_detail_return_keeps_query_scroll_and_loaded_data_across_rotation()=fixture { graph,scenario ->
        compose.onNodeWithText("篩選").performScrollTo().performClick()
        compose.onAllNodesWithText("中正區").onLast().performScrollTo().performClick()
        compose.onNodeWithText("套用").performClick()
        compose.waitUntil(5000) { graph.requests.lastOrNull()?.districts==listOf("中正區") }
        compose.onNodeWithTag("find-list").performScrollToIndex(8)
        compose.onNodeWithText("房源 8").performScrollTo().performClick()
        compose.onNodeWithText("返回").assertExists()
        val requests=graph.requests.size
        scenario.recreate()
        scenario.onActivity { mount(it,graph) }
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithText("房源 8").assertIsDisplayed()
        compose.onNodeWithText("收藏",useUnmergedTree=true).performClick()
        compose.onNodeWithText("找房",useUnmergedTree=true).performClick()
        compose.onNodeWithText("房源 8").assertIsDisplayed()
        compose.runOnIdle { assertEquals(requests,graph.requests.size);assertEquals(listOf("中正區"),graph.requests.last().districts) }
        captureScreenshot("b5-find-return-360-font200.png")
    }
    @Test fun saved_history_target_returns_same_position_and_root_back_reaches_system()=fixture { graph,scenario ->
        runBlocking { graph.properties.forEachIndexed { index,p -> graph.favorites.put(graph.owner,FavoriteEntry(p.id,index.toLong(),p.toSnapshot())) } }
        compose.onNodeWithText("收藏",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("saved-list").performScrollToIndex(7)
        compose.onAllNodesWithText("價格歷史").onFirst().performScrollTo().assertIsDisplayed().performClick()
        try { compose.waitUntil(5000) { compose.onNodeWithText("系統擷取價格紀錄").isDisplayed() } }
        catch(e:Throwable) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /sdcard/b5-history-target-failure.png").close()
            throw AssertionError(compose.onRoot().printToString(),e)
        }
        compose.onNodeWithText("系統擷取價格紀錄").assertIsDisplayed()
        captureScreenshot("b5-history-target-360-font200.png")
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithTag("saved-list").assertExists()
        compose.onNodeWithText("房源 23").assertIsDisplayed()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("篩選").assertExists()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5000) { scenario.state==androidx.lifecycle.Lifecycle.State.DESTROYED }
        assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED,scenario.state)
    }
    @Test fun logout_disposes_old_navigation_and_account_contains_local_scope()=fixture { graph,_ ->
        compose.onNodeWithText("帳號").performClick()
        compose.onNodeWithText("member@example.com").assertExists()
        compose.onNodeWithText("收藏僅儲存在此裝置、此帳號，不跨裝置同步").assertExists()
        compose.onNodeWithText("清除 App 資料或解除安裝會移除收藏",substring=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("登出").performScrollTo().performClick()
        compose.onNodeWithText("已登出").assertExists()
        compose.runOnIdle { assertTrue(graph.signedOut);assertFalse(graph.session.scope.coroutineContext[kotlinx.coroutines.Job]!!.isActive) }
    }
}