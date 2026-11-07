package tw.houselens.viewer.districts
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tw.houselens.viewer.support.*
import tw.houselens.viewer.ui.HouseLensTheme
class DistrictsScreenTest {
 @get:Rule val compose=createComposeRule()
 @Test fun charts_text_zero_insufficient_and_browse_are_reachable_at_large_font() {
  val config=InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
  assertEquals(360,config.screenWidthDp);assertEquals(2f,config.fontScale)
  assertEquals(160,config.densityDpi);assertTrue(config.screenHeightDp>=700)
  val graph=TestGraph();val vm=DistrictsViewModel(graph.api,graph.scope);var target:String?=null
  try {
   compose.setContent { HouseLensTheme { DistrictsScreen(vm) { target=it } } }
   compose.onNodeWithText("刊登資料分析，非實價登錄").assertExists()
   compose.onNodeWithTag("district-list").performScrollToNode(hasText("1000–1500萬：1 筆"))
   compose.onNodeWithText("1000–1500萬：1 筆").performScrollTo().assertIsDisplayed()
   compose.onNodeWithTag("district-list").performScrollToNode(hasText("2026-11-07：42 萬／坪"))
   compose.onNodeWithText("2026-11-07：42 萬／坪").performScrollTo().assertIsDisplayed()
   compose.mainClock.advanceTimeBy(1000);compose.waitForIdle()
   captureScreenshot("b6-district-trend-360-font200.png")
   compose.onNodeWithTag("district-list").performScrollToNode(hasText("查看本區房源"))
   compose.onNodeWithText("查看本區房源").performScrollTo().performClick()
   compose.runOnIdle { assertEquals("中正區",target) }
   compose.onNodeWithTag("district-list").performScrollToIndex(0)
   compose.onNodeWithText("行政區：中正區").performClick();compose.onNodeWithText("空區").performClick()
   compose.onNodeWithTag("district-list").performScrollToNode(hasText("平均單價：—"))
   compose.onNodeWithText("平均單價：—").performScrollTo().assertIsDisplayed()
   compose.onNodeWithTag("district-list").performScrollToNode(hasText("總價範圍：—"))
   compose.onNodeWithText("總價範圍：—").performScrollTo().assertIsDisplayed()
   compose.onNodeWithTag("district-list").performScrollToNode(hasText("樣本資料不足，僅供參考"))
   compose.onNodeWithText("樣本資料不足，僅供參考").performScrollTo().assertIsDisplayed()
   compose.mainClock.advanceTimeBy(1000);compose.waitForIdle()
   captureScreenshot("b6-district-empty-360-font200.png")
  } finally { graph.dispose() }
 }
 @Test fun retry_recovers_without_exposing_raw_exception() {
  val graph=TestGraph();graph.browseFail=true;val vm=DistrictsViewModel(graph.api,graph.scope)
  try {
   compose.setContent { HouseLensTheme { DistrictsScreen(vm) {} } }
   compose.onNodeWithText("重試區域分析").performScrollTo().assertExists()
   compose.onNodeWithText("RAW SECRET").assertDoesNotExist()
   compose.runOnIdle { graph.browseFail=false }
   compose.onNodeWithText("重試區域分析").performClick()
   compose.onNodeWithTag("district-list").performScrollToNode(hasText("物件數：1 筆"))
   compose.onNodeWithText("物件數：1 筆").performScrollTo().assertIsDisplayed()
  } finally { graph.dispose() }
 }
}
