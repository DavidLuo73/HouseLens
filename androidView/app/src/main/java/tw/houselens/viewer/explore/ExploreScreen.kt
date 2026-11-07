package tw.houselens.viewer.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import tw.houselens.viewer.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import tw.houselens.viewer.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ExploreScreen(viewModel:ExploreViewModel,onOpen:(String,Boolean)->Unit) {
 val state by viewModel.state.collectAsStateWithLifecycle()
 val top=state.tab==ExploreTab.TopRated
 val district=if(top) state.top.district else state.drops.district
 val loading=if(top) state.top.loading else state.drops.loading
 val error=if(top) state.top.error else state.drops.error
 val fetched=if(top) state.top.fetchedAt else state.drops.fetchedAt
 val hasData=if(top) state.top.groups.isNotEmpty() else state.drops.items.isNotEmpty()
 val list=remember(viewModel,state.tab,district) { LazyListState(if(top) state.top.scrollIndex else state.drops.scrollIndex,if(top) state.top.scrollOffset else state.drops.scrollOffset) }
 val current by rememberUpdatedState(state)
 var menu by remember { mutableStateOf(false) }
 LaunchedEffect(list) {
  snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.collect { (index,offset)->viewModel.rememberPosition(state.tab,index,offset) }
 }
 LaunchedEffect(list,state.drops.page) {
  if(!top) snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it>=list.layoutInfo.totalItemsCount-2 } ?: false }
   .distinctUntilChanged().collect { if(it && current.drops.appendError==null) viewModel.loadNextDrops() }
 }
 Column(Modifier.fillMaxSize()) {
  Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   FilterChip(selected=top,onClick={ viewModel.selectTab(ExploreTab.TopRated) },label={ Text("優質排行") },modifier=Modifier.heightIn(min=48.dp))
   FilterChip(selected=!top,onClick={ viewModel.selectTab(ExploreTab.BigDrop) },label={ Text("大降價") },modifier=Modifier.heightIn(min=48.dp))
  }
  PullToRefreshBox(isRefreshing=loading && hasData,onRefresh=viewModel::refresh,modifier=Modifier.weight(1f)) {
   LazyColumn(state=list,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp),modifier=Modifier.fillMaxSize().testTag(if(top) "explore-top-list" else "explore-drop-list")) {
    item {
     Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
      Box {
       TextButton(onClick={ menu=true },modifier=Modifier.heightIn(min=48.dp)) { Icon(painterResource(R.drawable.ic_district),contentDescription=null,modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("行政區：${district ?: "全部"}") }
       DropdownMenu(expanded=menu,onDismissRequest={ menu=false }) {
        DropdownMenuItem(text={ Text("全部") },onClick={ menu=false;viewModel.selectDistrict(null) })
        (state.top.groups.map { it.district }+listOfNotNull(district)).distinct().forEach { name -> DropdownMenuItem(text={ Text(name) },onClick={ menu=false;viewModel.selectDistrict(name) }) }
       }
      }
      TextButton(onClick=viewModel::refresh,enabled=!loading,modifier=Modifier.heightIn(min=48.dp)) { Icon(painterResource(R.drawable.ic_refresh),contentDescription=null,modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("重新整理精選") }
      Text("依行政區名稱統計，同名區可能包含不同縣市",style=MaterialTheme.typography.bodyMedium)
      if(!top) Text("曾出現的大降價紀錄；可能包含已下架物件，最新售價與狀態請查看詳情。")
      if(hasData && (loading || error!=null || (!top && state.drops.district!=state.drops.resultDistrict))) Text("保留上次結果（舊資料）",modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
      if(error!=null) {
       Text(error,color=MaterialTheme.colorScheme.error,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
       TextButton(onClick=viewModel::refresh,modifier=Modifier.heightIn(min=48.dp)) { Text("重試精選") }
      }
     }
    }
    if(loading && !hasData) item {
     Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp).heightIn(min=200.dp)) { Text("精選載入中") }
    }
    if(top) {
     val groups=state.top.groups.filter { district==null || it.district==district }
     groups.forEach { group ->
      item(key="district-${group.district}") { Text(group.district,style=MaterialTheme.typography.titleLarge) }
      if(group.items.isEmpty()) item(key="empty-${group.district}") { Text("尚無可評分房源") }
      items(group.items,key={ "top-${group.district}-${it.id}" }) { property ->
       Card(onClick={ onOpen(property.id,false) },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
         Text("第 ${property.rank} 名 · ${property.district}")
         Text(property.title.ifBlank { "未提供標題" },style=MaterialTheme.typography.titleMedium)
         Text("${property.totalPrice.stripTrailingZeros().toPlainString()} 萬",fontSize=26.sp)
         Text("單價 ${property.unitPrice?.stripTrailingZeros()?.toPlainString() ?: "—"} 萬／坪")
         Text("屋齡 ${property.ageYears?.let { "$it 年" } ?: "—"}")
         Text(if(property.hasParking) "有車位" else "未標示車位")
         Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
          Icon(painterResource(R.drawable.ic_star_filled),contentDescription=null,modifier=Modifier.size(20.dp),tint=Color(0xFFFF385C))
          Text("系統評分 ${property.score.multiply(java.math.BigDecimal(5)).setScale(1,java.math.RoundingMode.HALF_UP).toPlainString()}")
         }
         TextButton(onClick={ onOpen(property.id,true) },modifier=Modifier.heightIn(min=48.dp)) { Icon(painterResource(R.drawable.ic_history),contentDescription=null,modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("價格歷史") }
        }
       }
      }
     }
     if(!loading && error==null && groups.isEmpty()) item { Text("尚無可評分房源") }
    } else {
     items(state.drops.items,key={ "drop-${it.id}" }) { event ->
      Card(onClick={ onOpen(event.id,false) },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
       Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(event.district)
        Text(event.title.ifBlank { "未提供標題" },style=MaterialTheme.typography.titleMedium)
        event.address?.let { Text(it) }
        Text("當次原價：${event.originalPrice?.stripTrailingZeros()?.toPlainString() ?: "—"} 萬")
        Text("當次降價後：${event.newPrice.stripTrailingZeros().toPlainString()} 萬",fontSize=26.sp)
        Text("${event.dropPercent?.let { "降幅 ${formatDrop(it.abs().negate())}" } ?: "降幅未提供"} · 降額 ${event.dropAmount?.stripTrailingZeros()?.toPlainString() ?: "—"} 萬",color=Color(0xFFC13515))
        Text("觀測：${formatTaipeiTime(event.capturedAt)}")
        Text("目前狀態：${statusLabel(event.status)}")
        TextButton(onClick={ onOpen(event.id,true) },modifier=Modifier.heightIn(min=48.dp)) { Icon(painterResource(R.drawable.ic_history),contentDescription=null,modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("價格歷史") }
       }
      }
     }
     if(!loading && error==null && state.drops.items.isEmpty()) item { Text("目前行政區沒有大降價紀錄") }
     item {
      Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
       if(state.drops.appending) Text("正在載入下一頁")
       state.drops.appendError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
       if(state.drops.page>0 && state.drops.page*20<state.drops.total && state.drops.district==state.drops.resultDistrict)
        TextButton(onClick=viewModel::loadNextDrops,enabled=!loading && !state.drops.appending,modifier=Modifier.heightIn(min=48.dp)) { Text(if(state.drops.appendError==null) "載入更多降價" else "重試下一頁") }
       else if(state.drops.page>0 && state.drops.district==state.drops.resultDistrict) Text("已到底，共 ${state.drops.total} 筆")
      }
     }
    }
    fetched?.let { item { Text("本次取得資料時間：${formatTaipeiTime(it)}",style=MaterialTheme.typography.bodyMedium) } }
   }
  }
 }
}
