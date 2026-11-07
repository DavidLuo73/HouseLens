package tw.houselens.viewer

import androidx.compose.ui.test.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import java.io.File
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tw.houselens.viewer.data.*
import tw.houselens.viewer.detail.*
import tw.houselens.viewer.find.*
import tw.houselens.viewer.saved.*
import tw.houselens.viewer.support.TestGraph
import tw.houselens.viewer.ui.*

class FinalReviewTest {
    @get:Rule val compose=createComposeRule()

    @Test fun pending_favorite_button_disables_until_write_and_heart_settle() {
        val graph=TestGraph();val queue=java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
        val dispatcher=object:CoroutineDispatcher() { override fun dispatch(context:CoroutineContext,block:Runnable) { queue.add(block) } }
        fun drain() { while(true) { val next=queue.poll() ?: break;next.run() } }
        val scope=CoroutineScope(SupervisorJob()+dispatcher)
        try {
            val vm=FindViewModel(graph.owner,graph.api,graph.favorites,FilterPreferences(graph.directory.resolve("pending-filters"),graph.scope),SavedStateHandle(),scope)
            compose.setContent { HouseLensTheme { FindScreen(vm) { _,_ -> } } }
            compose.waitUntil(5000) { drain();vm.state.value.loadedPage==1 }
            compose.onNodeWithTag("property-1").performScrollTo()
            compose.runOnIdle { vm.toggleFavorite(graph.properties.first()) }
            compose.onAllNodesWithContentDescription("加入收藏").onFirst().assertIsNotEnabled()
            compose.runOnIdle { vm.toggleFavorite(graph.properties.first()) }
            compose.waitUntil(5000) { drain();"1" in vm.state.value.favoriteIds }
            compose.waitUntil(5000) {
                drain()
                runCatching { compose.onNodeWithContentDescription("取消收藏").assertIsEnabled() }.isSuccess
            }
            compose.onNodeWithContentDescription("取消收藏").performClick()
            compose.waitUntil(5000) { drain();"1" !in vm.state.value.favoriteIds }
        } finally { scope.cancel();drain();graph.dispose() }
    }

    @Test fun failed_undo_has_persistent_retry_and_restores_original_entry() {
        val graph=TestGraph()
        try {
            runBlocking { graph.favorites.put(graph.owner,FavoriteEntry("1",7,graph.properties.first().toSnapshot())) }
            val vm=SavedViewModel(graph.owner,graph.api,graph.favorites)
            graph.session.viewModelStore.put("undo-retry",vm)
            compose.setContent { HouseLensTheme { SavedScreen(vm) { _,_ -> } } }
            compose.waitUntil(5000) { vm.state.value.entries.size==1 }
            compose.onNodeWithContentDescription("取消收藏").performClick()
            compose.waitUntil(5000) { vm.state.value.undoEntry!=null }
            val file=graph.directory.resolve("saved").listFiles()!!.single { it.isFile }
            val blocker=File(file.path+".tmp");blocker.mkdirs();File(blocker,"block").writeText("blocked")
            compose.onNodeWithText("復原").performClick()
            compose.waitUntil(5000) { vm.state.value.storageError!=null }
            compose.onNodeWithText("重試復原").assertIsDisplayed()
            blocker.deleteRecursively()
            compose.onNodeWithText("重試復原").performClick()
            compose.waitUntil(5000) { vm.state.value.undoEntry==null && vm.state.value.entries.size==1 }
            compose.runOnIdle { assertNull(vm.state.value.storageError);assertEquals(7L,vm.state.value.entries.single().savedAtEpochMillis) }
        } finally { graph.dispose() }
    }

    @Test fun merged_saved_card_overrides_new_drop_badges_and_names_source_four() {
        val graph=TestGraph()
        try {
            val snapshot=graph.properties.first().copy(status=2,isNew=true,latestIsBigDrop=true,sourceSites=listOf(4)).toSnapshot()
            compose.setContent { HouseLensTheme { androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) { PropertyCard(snapshot,true,{},{},{}) } } }
            compose.onNodeWithText("已合併").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("台灣房屋").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("新上架").assertDoesNotExist()
            compose.onNodeWithText("▼ 大降價").assertDoesNotExist()
        } finally { graph.dispose() }
    }

    @Test fun merged_detail_is_not_presented_as_for_sale() {
        val graph=TestGraph()
        try {
            val api=object:HouseLensData by graph.api {
                override suspend fun detail(id:String)=graph.api.detail(id)?.let { it.copy(property=it.property.copy(status=2)) }
            }
            val vm=DetailViewModel("1",api,graph.owner,graph.favorites)
            graph.session.viewModelStore.put("merged",vm)
            compose.setContent { HouseLensTheme { DetailScreen(vm,false,{}) } }
            compose.waitUntil(5000) { vm.state.value.details!=null }
            compose.onNodeWithText("中正區 · 已合併").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("中正區 · 在售").assertDoesNotExist()
        } finally { graph.dispose() }
    }
}
