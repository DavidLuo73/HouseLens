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
    private fun mount(host:ComponentActivity,graph:TestGraph) { host.setContent { HouseLensTheme { HouseLensNav(graph) } } }
    private fun fixture(block:(TestGraph,ActivityScenario<ComponentActivity>)->Unit) {
        val graph=TestGraph()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val scenario=ActivityScenario.launch<ComponentActivity>(Intent(context,ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        scenario.onActivity { mount(it,graph) }
        try { block(graph,scenario) } finally { scenario.close();graph.dispose() }
    }
    @Test fun all_sort_directions_apply_without_losing_district_filter()=fixture { graph,_ ->
        compose.onNodeWithText("篩選").performScrollTo().performClick()
        compose.onAllNodesWithText("中正區").onLast().performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("套用").performClick()
        compose.waitUntil(5000) { graph.requests.lastOrNull()?.districts==listOf("中正區") }
        compose.runOnIdle { assertEquals("unitPrice",graph.requests.first().sortBy) }
        var current="單價低到高"
        listOf(
            "price" to "總價低到高",
            "priceDesc" to "總價高到低",
            "unitPrice" to "單價低到高",
            "unitPriceDesc" to "單價高到低",
            "newest" to "最近收錄",
            "score" to "系統評分優先"
        ).forEachIndexed { index,(sort,label) ->
            compose.onNodeWithText(current).performScrollTo().performClick()
            if(index==0) { compose.waitForIdle();captureScreenshot("sort-menu.png") }
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(5000) { graph.requests.lastOrNull()?.sortBy==sort }
            compose.runOnIdle { assertEquals(listOf("中正區"),graph.requests.last().districts) }
            compose.onNodeWithText(label).assertIsDisplayed()
            if(sort!="unitPrice") compose.onNodeWithContentDescription("移除${label}條件").assertExists()
            else compose.onNodeWithContentDescription("移除${label}條件").assertDoesNotExist()
            current=label
        }
        compose.onNodeWithContentDescription("移除系統評分優先條件").performScrollTo().performClick()
        compose.waitUntil(5000) { graph.requests.lastOrNull()?.sortBy=="unitPrice" }
        compose.runOnIdle { assertEquals(listOf("中正區"),graph.requests.last().districts) }
        compose.onNodeWithContentDescription("移除單價低到高條件").assertDoesNotExist()
        compose.onNodeWithText("清除全部").performScrollTo().performClick()
        compose.waitUntil(5000) { graph.requests.lastOrNull()?.let { it.districts.isEmpty() && it.sortBy=="unitPrice" }==true }
        compose.onNodeWithText("單價低到高").assertIsDisplayed()
    }
    @Test fun find_detail_return_keeps_query_scroll_and_loaded_data_across_rotation()=fixture { graph,scenario ->
        compose.onNodeWithText("HouseLens").assertIsDisplayed()
        captureScreenshot("icons-find.png")
        compose.onNodeWithText("篩選").performScrollTo().performClick()
        compose.onAllNodesWithText("中正區").onLast().performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("套用").performClick()
        compose.waitUntil(5000) { graph.requests.lastOrNull()?.districts==listOf("中正區") }
        compose.onNodeWithTag("find-list").performScrollToIndex(8)
        compose.onNodeWithText("房源 8").performScrollTo().performClick()
        compose.onNodeWithText("返回").assertExists()
        captureScreenshot("icons-detail.png")
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
        compose.onNodeWithTag("saved-list").assertIsDisplayed()
        captureScreenshot("icons-saved.png")
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
    @Test fun information_discloses_local_favorites_without_account_actions()=fixture { graph,_ ->
        compose.onNodeWithText("資訊").performClick()
        compose.onNodeWithText("收藏僅儲存在此裝置，不跨裝置同步").assertExists()
        compose.onNodeWithText("清除 App 資料或解除安裝會移除收藏",substring=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("登出").assertDoesNotExist()
        compose.onNodeWithText("登入").assertDoesNotExist()
    }
    @Test fun four_tabs_keep_explore_position_and_district_browse_can_undo()=fixture { graph,_ ->
        compose.onNodeWithText("找房",useUnmergedTree=true).assertExists()
        compose.onNodeWithText("精選",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("explore-top-list").performScrollToNode(hasText("精選房源 3"))
        compose.onNodeWithText("精選房源 3").assertIsDisplayed()
        captureScreenshot("icons-explore.png")
        compose.onNodeWithText("收藏",useUnmergedTree=true).performClick()
        compose.onNodeWithText("精選",useUnmergedTree=true).performClick()
        compose.onNodeWithText("精選房源 3").assertIsDisplayed()
        compose.onNodeWithText("精選房源 3").performClick()
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithText("精選房源 3").assertIsDisplayed()
        compose.onNodeWithText("區域",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("district-list").assertIsDisplayed()
        captureScreenshot("icons-districts.png")
        compose.onNodeWithTag("district-list").performScrollToNode(hasText("查看本區房源"))
        compose.onNodeWithText("查看本區房源").performClick()
        compose.waitUntil(5000) { graph.requests.lastOrNull()?.districts==listOf("中正區") }
        compose.onNodeWithText("復原行政區變更").performScrollTo().performClick()
        compose.waitUntil(5000) { graph.requests.lastOrNull()?.districts==emptyList<String>() }
        compose.onNodeWithText("區域",useUnmergedTree=true).performClick()
        compose.onNodeWithText("查看本區房源").assertIsDisplayed()
        captureScreenshot("b6-four-tabs-360-font200.png")
    }
    @Test fun information_shows_source_failure_and_refreshes_only_on_request()=fixture { graph,_ ->
        compose.onNodeWithText("資訊").performClick()
        compose.onNodeWithText("資料狀態").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("信義房屋：失敗（0 筆）").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1,graph.latestRequests) }
        captureScreenshot("b6-account-status-360-font200.png")
        compose.onNodeWithText("更新資料狀態").performScrollTo().performClick()
        compose.waitUntil(5000) { graph.latestRequests==2 }
        compose.onNodeWithText("返回").performClick()
        compose.runOnIdle { assertEquals(2,graph.latestRequests) }
    }
}