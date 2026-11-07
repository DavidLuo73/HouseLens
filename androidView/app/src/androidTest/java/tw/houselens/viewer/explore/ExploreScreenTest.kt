package tw.houselens.viewer.explore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tw.houselens.viewer.support.*
import tw.houselens.viewer.ui.HouseLensTheme
class ExploreScreenTest {
 @get:Rule val compose=createComposeRule()
 @Test fun ranking_empty_district_and_historical_drop_open_details_at_large_font() {
  val config=InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
  assertEquals(360,config.screenWidthDp);assertEquals(2f,config.fontScale)
  assertEquals(160,config.densityDpi);assertTrue(config.screenHeightDp>=700)
  val graph=TestGraph();val vm=ExploreViewModel(graph.api,graph.scope);var target:Pair<String,Boolean>?=null
  try {
   compose.setContent { HouseLensTheme { ExploreScreen(vm) { id,h -> target=id to h } } }
   compose.onNodeWithText("精選房源 1").performScrollTo().assertIsDisplayed()
   compose.mainClock.advanceTimeBy(1000);compose.waitForIdle()
   captureScreenshot("b6-ranking-360-font200.png")
   compose.onNodeWithText("精選房源 1").performClick()
   compose.runOnIdle { assertEquals("1" to false,target);assertEquals(0,graph.detailRequests) }
   compose.onNodeWithTag("explore-top-list").performScrollToIndex(0)
   compose.onNodeWithText("行政區：全部").performClick();compose.onNodeWithText("空區").performClick()
   compose.onNodeWithText("尚無可評分房源").performScrollTo().assertIsDisplayed()
   compose.onNodeWithText("大降價",useUnmergedTree=true).performClick()
   compose.onNodeWithText("曾出現的大降價紀錄",substring=true).performScrollTo().assertIsDisplayed()
   compose.onAllNodesWithText("當次降價後：800 萬").onFirst().performScrollTo().assertIsDisplayed()
   compose.onAllNodesWithText("觀測：2026-01-01 08:00").onFirst().performScrollTo().assertIsDisplayed()
   compose.onAllNodesWithText("目前狀態：已下架").onFirst().performScrollTo().assertIsDisplayed()
   compose.onNodeWithText("降幅未提供 · 降額 200 萬").performScrollTo().assertIsDisplayed()
   compose.mainClock.advanceTimeBy(1000);compose.waitForIdle()
   captureScreenshot("b6-explore-drop-360-font200.png")
   compose.runOnIdle { target=null }
   compose.onNodeWithTag("explore-drop-list").performScrollToNode(hasText("降價房源 1"))
   compose.onNodeWithText("降價房源 1").performClick()
   compose.runOnIdle { assertEquals("1" to false,target);assertEquals(0,graph.detailRequests) }
  } finally { graph.dispose() }
 }
 @Test fun refresh_failure_retry_button_recovers() {
  val graph=TestGraph();graph.browseFail=true;val vm=ExploreViewModel(graph.api,graph.scope)
  try {
   compose.setContent { HouseLensTheme { ExploreScreen(vm) { _,_-> } } }
   compose.onNodeWithText("重試精選").performScrollTo().assertExists()
   compose.runOnIdle { graph.browseFail=false }
   compose.onNodeWithText("重試精選").performClick()
   compose.onNodeWithText("精選房源 1").performScrollTo().assertIsDisplayed()
  } finally { graph.dispose() }
 }
}
