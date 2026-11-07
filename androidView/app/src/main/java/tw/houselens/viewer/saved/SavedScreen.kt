package tw.houselens.viewer.saved

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import tw.houselens.viewer.ui.PropertyCard

@Composable
fun SavedScreen(viewModel: SavedViewModel,onOpen: (id: String,showHistory: Boolean) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val currentEntries by rememberUpdatedState(state.entries)
    LaunchedEffect(viewModel,list) {
        snapshotFlow {
            val visible = list.layoutInfo.visibleItemsInfo
            if(visible.isEmpty()) emptyList() else {
                val start = (visible.first().index - 1).coerceAtLeast(0)
                val end = (visible.last().index + 2).coerceAtMost(currentEntries.size)
                if(start < end) currentEntries.subList(start,end).map { it.id } else emptyList()
            }
        }.distinctUntilChanged().collect { viewModel.refreshVisible(it) }
    }
    LaunchedEffect(state.undoEntry) {
        if(state.undoEntry != null) {
            val result=snackbar.showSnackbar("已取消收藏",actionLabel="復原",duration=SnackbarDuration.Short)
            if(result==SnackbarResult.ActionPerformed) viewModel.undo() else viewModel.dismissUndo()
        }
    }
    Scaffold(snackbarHost={ SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text("收藏",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(16.dp))
            if(state.storageError != null) Text(state.storageError!!,modifier=Modifier.padding(horizontal=16.dp))
            if(state.entries.isEmpty() && state.storageError == null) Text("尚無收藏",modifier=Modifier.padding(16.dp))
            LazyColumn(state=list,modifier=Modifier.testTag("saved-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                items(state.entries,key={ it.id }) { entry ->
                    Column {
                        val status=state.statuses[entry.id] ?: RefreshStatus.NOT_REFRESHED
                        Text(when(status) {
                            RefreshStatus.NOT_REFRESHED -> "尚未刷新"
                            RefreshStatus.UPDATED -> "已更新"
                            RefreshStatus.REMOVED -> "此物件已移除"
                            RefreshStatus.TEMPORARY_FAILURE -> "暫時無法更新"
                        },style=MaterialTheme.typography.bodyMedium)
                        val snapshot=entry.snapshot
                        if(snapshot != null) PropertyCard(snapshot,true,{ onOpen(entry.id,false) },{ viewModel.remove(entry.id) },{ onOpen(entry.id,true) })
                        else Card {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("房源摘要尚未取得")
                                TextButton(onClick={ onOpen(entry.id,false) }) { Text("開啟房源") }
                                TextButton(onClick={ viewModel.remove(entry.id) }) { Text("取消收藏") }
                            }
                        }
                        if(status==RefreshStatus.TEMPORARY_FAILURE || status==RefreshStatus.NOT_REFRESHED)
                            TextButton(onClick={ viewModel.retry(entry.id) }) { Text("重試更新") }
                    }
                }
            }
        }
    }
}