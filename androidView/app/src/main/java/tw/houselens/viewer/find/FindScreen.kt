package tw.houselens.viewer.find

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import tw.houselens.viewer.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import tw.houselens.viewer.data.*
import tw.houselens.viewer.saved.toSnapshot
import tw.houselens.viewer.ui.PropertyCard

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
fun FindScreen(viewModel: FindViewModel,onOpen: (String,Boolean) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filters by remember { mutableStateOf(false) }
    var sorts by remember { mutableStateOf(false) }
    // A recreated process has no list data; restore position only while this list exists in memory.
    val list=remember(viewModel) { LazyListState(viewModel.scrollIndex,viewModel.scrollOffset) }
    var resultQuery by remember(viewModel) { mutableStateOf(state.resultQuery) }
    LaunchedEffect(state.resultQuery) {
        if(resultQuery!=null && resultQuery!=state.resultQuery) list.scrollToItem(0)
        resultQuery=state.resultQuery
    }
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.collect { (index,offset) ->
            if(viewModel.state.value.items.isNotEmpty()) { viewModel.scrollIndex=index;viewModel.scrollOffset=offset }
        }
    }
    val current by rememberUpdatedState(state)
    LaunchedEffect(list,state.loadedPage,state.query) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it>=list.layoutInfo.totalItemsCount-3 } ?: false }
            .distinctUntilChanged().collect { nearEnd -> if(nearEnd && current.appendError==null) viewModel.loadNext() }
    }
    fun open(id: String,history: Boolean) { viewModel.rememberTarget(id,history);onOpen(id,history) }
    Scaffold { padding ->
        PullToRefreshBox(isRefreshing=state.refreshing,onRefresh=viewModel::refresh,modifier=Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(state=list,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp),modifier=Modifier.fillMaxSize().testTag("find-list")) {
                item {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Icon(painterResource(R.drawable.ic_house),contentDescription=null,modifier=Modifier.size(28.dp),tint=Color.Unspecified)
                            Text("HouseLens",style=MaterialTheme.typography.headlineSmall)
                        }
                        Text(state.query.districts.joinToString("、").ifEmpty { "全部行政區" })
                        Button(onClick={ filters=true },modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) { Icon(painterResource(R.drawable.ic_filter),contentDescription=null,modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("篩選") }
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            queryChips(state.query).forEach { chip ->
                                InputChip(selected=true,onClick={ viewModel.apply(chip.removed) },label={ Text("${chip.label} ×") },modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).semantics { contentDescription="移除${chip.label}條件" })
                            }
                        }
                        if(queryChips(state.query).isNotEmpty()) TextButton(onClick={ viewModel.apply(PropertyQuery()) },modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) { Text("清除全部") }
                        if(state.canUndoDistrict) TextButton(onClick=viewModel::undoDistrictChange,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) { Text("復原行政區變更") }
                        if(state.optionsError!=null) {
                            Text(state.optionsError!!)
                            TextButton(onClick=viewModel::reloadOptions,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) { Text("重試行政區選項") }
                        }
                        val pending=state.items.isNotEmpty() && (state.resultQuery!=state.query || state.refreshing || state.error!=null)
                        Text(if(pending) "保留上次結果（舊資料），等待目前條件的新結果" else "共 ${state.total} 筆",modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
                        Box {
                            TextButton(onClick={ sorts=true },modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) { Icon(painterResource(R.drawable.ic_sort),contentDescription=null,modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(sortLabels.getValue(state.query.sortBy)) }
                            DropdownMenu(expanded=sorts,onDismissRequest={ sorts=false }) {
                                sortLabels.forEach { (value,label) -> DropdownMenuItem(text={ Text(label) },onClick={ sorts=false;viewModel.apply(state.query.copy(sortBy=value)) }) }
                            }
                        }
                        TextButton(onClick=viewModel::refresh,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) { Icon(painterResource(R.drawable.ic_refresh),contentDescription=null,modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("重新整理") }
                        if(state.restored) Text("已恢復條件，列表從第一頁重新載入")
                        if(state.storageError!=null) Text(state.storageError!!,color=MaterialTheme.colorScheme.error)
                        if(state.error!=null) {
                            Text(state.error!!,color=MaterialTheme.colorScheme.error)
                            TextButton(onClick=viewModel::refresh,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) { Text("重試房源") }
                        }
                    }
                }
                if(state.initialLoading) item {
                    Column(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().aspectRatio(4f/3f).background(MaterialTheme.colorScheme.surfaceVariant))
                        Text("房源載入中",modifier=Modifier.padding(16.dp))
                        Spacer(Modifier.height(96.dp))
                    }
                }
                if(!state.initialLoading && state.error==null && state.items.isEmpty()) item {
                    Column {
                        Text("目前條件沒有房源")
                        TextButton(onClick={ viewModel.apply(PropertyQuery()) },modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) { Text("清除條件") }
                    }
                }
                items(state.items,key={ it.id }) { property ->
                    PropertyCard(property.toSnapshot(),property.id in state.favoriteIds,{ open(property.id,false) },{ viewModel.toggleFavorite(property) },{ open(property.id,true) },saveEnabled=property.id !in state.pendingFavoriteIds)
                }
                item {
                    Column {
                        if(state.appending) Text("正在載入下一頁")
                        if(state.appendError!=null) Text(state.appendError!!,color=MaterialTheme.colorScheme.error)
                        if(state.resultQuery==state.query && state.loadedPage>0 && state.loadedPage*20<state.total)
                            TextButton(onClick=viewModel::loadNext,enabled=!state.appending && !state.refreshing,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) { Text(if(state.appendError!=null) "重試下一頁" else "載入更多") }
                        else if(state.loadedPage>0 && state.items.isNotEmpty() && state.resultQuery==state.query) Text("已到底")
                        state.fetchedAt?.let { Text("本次取得資料時間：${formatTaipeiTime(it)}",style=MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
    if(filters) FilterSheet(state.query,state.districtChoices,{ filters=false;viewModel.apply(it) },{ filters=false })
}
