package tw.houselens.viewer.saved

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.math.BigDecimal
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tw.houselens.viewer.auth.OwnerKey
import tw.houselens.viewer.data.*
import tw.houselens.viewer.ui.*

class SavedScreenTest {
    @get:Rule val compose=createComposeRule()
    private val property=PropertyDto("1",BigDecimal("1200"),title="測試房源",city="台北市",district="中正區")
    @Test fun save_button_changes_heart_without_opening_card() {
        var opened=0;var history=0
        compose.setContent { HouseLensTheme {
            var saved by remember { mutableStateOf(false) }
            PropertyCard(property.toSnapshot(),saved,{opened++},{saved=!saved},{history++})
        } }
        compose.onNodeWithContentDescription("加入收藏").performClick()
        compose.onNodeWithContentDescription("取消收藏").assertExists()
        compose.runOnIdle { assertEquals(0,opened) }
        compose.onNodeWithText("測試房源").performClick()
        compose.runOnIdle { assertEquals(1,opened);assertEquals(0,history) }
    }
    @Test fun remove_then_undo_restores_saved_card_and_original_time() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.cacheDir,"saved-test-${System.nanoTime()}")
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val owner=OwnerKey("https://test.supabase.co","member")
        val store=FavoriteStore(directory,scope)
        var opened: String?=null
        try {
            runBlocking { store.put(owner,FavoriteEntry("1",7,property.toSnapshot())) }
            val api=object : HouseLensData {
                override suspend fun property(id:String)=property
                override suspend fun detail(id:String):PropertyDetails?=error("No details")
                override suspend fun properties(query:PropertyQuery,page:Int):PropertyPage=error("No scan")
                override suspend fun topRated():List<TopGroupDto> = error("unused")
                override suspend fun bigDrops(page:Int,district:String?):DropPage=error("unused")
                override suspend fun districtStats():List<DistrictStatsDto> = error("unused")
                override suspend fun districtOptions():List<DistrictOptionDto> = error("unused")
                override suspend fun latestRun():CrawlRunDto?=error("unused")
            }
            val vm=SavedViewModel(owner,api,store,scope)
            compose.setContent { HouseLensTheme { SavedScreen(vm) { id,history -> opened="$id:$history" } } }
            compose.waitUntil(5000) { vm.state.value.entries.isNotEmpty() }
            compose.onNodeWithContentDescription("取消收藏").performClick()
            compose.onNodeWithText("復原").performClick()
            compose.waitUntil(5000) { vm.state.value.entries.size==1 && vm.state.value.undoEntry==null }
            compose.onNodeWithContentDescription("取消收藏").assertExists()
            compose.runOnIdle { assertNull(opened);assertEquals(7L,vm.state.value.entries.single().savedAtEpochMillis) }
        } finally { scope.cancel();directory.deleteRecursively() }
    }
}