package tw.houselens.viewer.detail

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tw.houselens.viewer.support.TestGraph
import tw.houselens.viewer.ui.HouseLensTheme

class DetailScreenTest {
    @get:Rule val compose=createComposeRule()
    @Test fun history_expands_twenty_at_a_time_has_text_and_safe_source_choices() {
        val graph=TestGraph()
        try {
            val vm=DetailViewModel("1",graph.api,graph.owner,graph.favorites)
            graph.session.viewModelStore.put("direct-detail",vm)
            compose.setContent { HouseLensTheme { DetailScreen(vm,true,{}, { graph.opened=it }) } }
            compose.waitUntil(5000) { vm.state.value.details!=null }
            compose.onNodeWithText("系統擷取價格紀錄").assertIsDisplayed()
            compose.onNodeWithText("收合紀錄（20／45）").performScrollTo().assertExists()
            compose.onNodeWithTag("history-e45").performScrollTo().assertExists()
            compose.onNodeWithTag("history-e26").performScrollTo().assertExists()
            compose.onNodeWithTag("history-e25").assertDoesNotExist()
            compose.onNodeWithText("顯示更多").performScrollTo().performClick()
            compose.onNodeWithTag("history-e6").performScrollTo().assertExists()
            compose.onNodeWithTag("history-e5").assertDoesNotExist()
            compose.onNodeWithText("查看原始刊登").assertIsDisplayed().performClick()
            compose.onAllNodesWithText("591 · 無效網址").onLast().assertExists().assertIsNotEnabled()
            compose.onAllNodesWithText("信義房屋 · example.com").onLast().performClick()
            compose.runOnIdle { assertEquals("https://example.com/listing/1",graph.opened) }
            compose.onNodeWithTag("history-e6").assertExists()
        } finally { graph.dispose() }
    }
    @Test fun large_text_long_address_and_retry_remain_reachable() {
        val config=InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        assertEquals(360,config.screenWidthDp);assertEquals(2f,config.fontScale)
        val graph=TestGraph();graph.fail=true
        try {
            val vm=DetailViewModel("1",graph.api,graph.owner,graph.favorites)
            graph.session.viewModelStore.put("direct-detail",vm)
            compose.setContent { HouseLensTheme { DetailScreen(vm,false,{}, { graph.opened=it }) } }
            compose.waitUntil(5000) { vm.state.value.error!=null }
            compose.onNodeWithText("重試詳情").performClick()
            compose.runOnIdle { graph.fail=false;vm.refresh() }
            compose.waitUntil(5000) { vm.state.value.details!=null }
            compose.onNodeWithText("地址：臺北市中正區很長很長的完整地址一二三四五六七八九十號").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("最後觀測：2026-11-01 08:00").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("查看原始刊登").assertIsDisplayed()
            tw.houselens.viewer.support.captureScreenshot("b5-detail-360-font200.png")
        } finally { graph.dispose() }
    }
}