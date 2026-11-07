package tw.houselens.viewer.find

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.math.BigDecimal
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tw.houselens.viewer.auth.OwnerKey
import tw.houselens.viewer.data.*
import tw.houselens.viewer.saved.FavoriteStore
import tw.houselens.viewer.ui.*

class FindScreenTest {
    @get:Rule val compose=createComposeRule()
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        // The connected-test runner uninstalls the APK and removes its external files afterward.
        instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/$name").use {
            java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() }
        }
    }
    @Test fun large_font_filter_scrolls_applies_and_keeps_cancel_reset_as_drafts() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        assertEquals(360,instrumentation.targetContext.resources.configuration.screenWidthDp)
        assertEquals(2f,instrumentation.targetContext.resources.configuration.fontScale)
        var applied: PropertyQuery?=null;var dismissed=0
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) { HouseLensTheme {
                Box(Modifier.width(360.dp)) { FilterSheet(PropertyQuery(districts=listOf("原區")),listOf(DistrictChoice("中正區","所有同名行政區")),{ applied=it },{ dismissed++ }) }
            } }
        }
        compose.onNodeWithText("中正區（所有同名行政區）").performScrollTo().performClick()
        compose.onNodeWithText("最低總價（萬元）").performScrollTo().performTextInput("-1")
        compose.onNodeWithText("套用").performClick()
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithText("總價不能為負數").performScrollTo().assertExists()
        compose.onNodeWithText("重設").performClick()
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithText("套用").assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(PropertyQuery(),applied);assertEquals(0,dismissed) }
        screenshot("b4-filter-360-font200.png")
    }
    @Test fun closing_filter_never_applies_and_all_platforms_can_be_selected() {
        var applied:PropertyQuery?=null;var dismissed=0
        compose.setContent { HouseLensTheme { FilterSheet(PropertyQuery(),emptyList(),{applied=it},{dismissed++}) } }
        listOf("591","樂屋網","信義房屋","永慶房屋","好房網","住商不動產","中信房屋").forEach {
            compose.onNodeWithText(it).performScrollTo().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        }
        compose.onNodeWithText("套用").performClick()
        compose.runOnIdle { assertEquals(listOf(0,1,2,3,4,5,6),applied!!.sourceSites.sorted());applied=null }
        compose.onNodeWithText("關閉篩選").performClick()
        compose.runOnIdle { assertNull(applied);assertEquals(1,dismissed) }
    }
    @Test fun list_card_favorite_history_body_are_independent_and_empty_query_can_clear() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.cacheDir,"find-ui-${System.nanoTime()}")
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val owner=OwnerKey("https://test.supabase.co","member")
        val p=PropertyDto("1",BigDecimal("1200"),title="測試房源",district="中正區",score=BigDecimal("0.9"),sourceSites=listOf(0,99))
        var open:String?=null
        var requests=0
        val api=object:HouseLensData {
            override suspend fun properties(query:PropertyQuery,page:Int):PropertyPage { requests++;return PropertyPage(if(query.districts.isEmpty()) 1 else 0,page,20,if(query.districts.isEmpty()) listOf(p) else emptyList()) }
            override suspend fun districtOptions()=emptyList<DistrictOptionDto>()
            override suspend fun districtStats()=emptyList<DistrictStatsDto>()
            override suspend fun property(id:String):PropertyDto?=error("unused")
            override suspend fun detail(id:String):PropertyDetails?=error("unused")
            override suspend fun topRated():List<TopGroupDto> = error("unused")
            override suspend fun bigDrops(page:Int,district:String?):DropPage=error("unused")
            override suspend fun latestRun():CrawlRunDto?=error("unused")
        }
        try {
            val vm=FindViewModel(owner,api,FavoriteStore(directory.resolve("saved"),scope),FilterPreferences(directory.resolve("filters"),scope),SavedStateHandle(),scope)
            compose.setContent { HouseLensTheme { Box(Modifier.width(360.dp)) { FindScreen(vm) { id,history -> open="$id:$history" } } } }
            compose.waitUntil(5000) { vm.state.value.items.isNotEmpty() }
            compose.onNodeWithText("篩選").performScrollTo().performClick()
            compose.onNodeWithText("有車位").performScrollTo().performClick()
            compose.onNodeWithText("重設").performClick()
            compose.onNodeWithText("關閉篩選").performClick()
            compose.runOnIdle { assertEquals(1,requests) }
            compose.onNodeWithContentDescription("加入收藏").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            compose.waitUntil(5000) { "1" in vm.state.value.favoriteIds }
            compose.runOnIdle { assertNull(open) }
            compose.onNodeWithText("價格歷史").performScrollTo().performClick()
            compose.runOnIdle { assertEquals("1:true",open);open=null }
            compose.onNodeWithText("系統評分 4.5",substring=true).performScrollTo().assertExists()
            compose.onNodeWithText("未標示車位",substring=true).performScrollTo().assertExists()
            compose.onNodeWithText("591、來源 99",substring=true).performScrollTo().assertExists()
            compose.onNodeWithText("測試房源").performScrollTo().performClick()
            compose.runOnIdle { assertEquals("1:false",open);vm.apply(PropertyQuery(districts=listOf("空區"))) }
            compose.waitUntil(5000) { !vm.state.value.refreshing && vm.state.value.items.isEmpty() }
            compose.onNodeWithText("清除條件").performScrollTo().performClick()
            compose.waitUntil(5000) { vm.state.value.items.isNotEmpty() }
            screenshot("b4-find-360-font200.png")
        } finally { scope.cancel();directory.deleteRecursively() }
    }
}
