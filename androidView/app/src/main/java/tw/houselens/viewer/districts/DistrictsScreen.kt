package tw.houselens.viewer.districts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import tw.houselens.viewer.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.houselens.viewer.data.*
import tw.houselens.viewer.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DistrictsScreen(viewModel:DistrictsViewModel,onBrowseDistrict:(String)->Unit) {
 val state by viewModel.state.collectAsStateWithLifecycle()
 val list=rememberLazyListState()
 var menu by remember { mutableStateOf(false) }
 val stats=state.selected
 PullToRefreshBox(isRefreshing=state.loading && state.stats.isNotEmpty(),onRefresh=viewModel::refresh,modifier=Modifier.fillMaxSize()) {
  LazyColumn(state=list,modifier=Modifier.fillMaxSize().testTag("district-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
   item {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
     Text("區域分析",style=MaterialTheme.typography.headlineSmall)
     Text("刊登資料分析，非實價登錄")
     Text("依行政區名稱統計，同名區可能包含不同縣市",style=MaterialTheme.typography.bodyMedium)
     Box {
      TextButton(onClick={ menu=true },enabled=state.stats.isNotEmpty(),modifier=Modifier.heightIn(min=48.dp)) { Icon(painterResource(R.drawable.ic_district),contentDescription=null,modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("行政區：${state.selectedDistrict ?: "尚無資料"}") }
      DropdownMenu(expanded=menu,onDismissRequest={ menu=false }) { state.stats.map { it.district }.distinct().forEach { district ->
       DropdownMenuItem(text={ Text(district) },onClick={ menu=false;viewModel.selectDistrict(district) })
      } }
     }
     TextButton(onClick=viewModel::refresh,enabled=!state.loading,modifier=Modifier.heightIn(min=48.dp)) { Icon(painterResource(R.drawable.ic_refresh),contentDescription=null,modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("更新區域分析") }
     if(state.stats.isNotEmpty() && (state.loading || state.error!=null)) Text("保留上次結果（舊資料）")
     state.error?.let {
      Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
      TextButton(onClick=viewModel::refresh,modifier=Modifier.heightIn(min=48.dp)) { Text("重試區域分析") }
     }
    }
   }
   if(state.loading && state.stats.isEmpty()) item { Box(Modifier.fillMaxWidth().height(200.dp).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp)) { Text("區域分析載入中") } }
   if(!state.loading && state.error==null && state.stats.isEmpty()) item { Text("尚無區域分析資料") }
   if(stats!=null) {
    item {
     Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
       Text(stats.district,style=MaterialTheme.typography.titleLarge)
       Text("物件數：${stats.propertyCount} 筆")
       Text("平均單價：${districtAverage(stats)}")
       Text("總價範圍：${districtRange(stats)}")
       if(stats.insufficientData) Text("樣本資料不足，僅供參考")
      }
     }
    }
    item { Text("價格分布",style=MaterialTheme.typography.titleLarge) }
    item { PriceBucketChart(stats.priceBuckets) }
    items(stats.priceBuckets) { Text("${it.range}：${it.count} 筆") }
    item { Text("平均單價趨勢",style=MaterialTheme.typography.titleLarge) }
    item {
     val points=remember(stats) { trendPoints(stats) }
     var selected by remember(stats) { mutableStateOf(emptyList<Int>()) }
     Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
      if(points.isEmpty()) Text("尚無趨勢資料") else {
       if(points.size==1) Text("僅一筆趨勢資料") else Text("共 ${points.size} 筆；${java.time.LocalDate.ofEpochDay(points.first().x)} 至 ${java.time.LocalDate.ofEpochDay(points.last().x)}")
       TimePriceChart(points) { selected=it }
       selected.forEach { index -> Text("選取：${java.time.LocalDate.ofEpochDay(points[index].x)}：${points[index].y.stripTrailingZeros().toPlainString()} 萬／坪") }
      }
     }
    }
    items(stats.trend.sortedBy { it.date }) { point -> Text("${formatDateOnly(point.date)}：${point.avgUnitPrice?.let { "${it.stripTrailingZeros().toPlainString()} 萬／坪" } ?: "—"}") }
    item { Button(onClick={ onBrowseDistrict(stats.district) },modifier=Modifier.heightIn(min=48.dp)) { Icon(painterResource(R.drawable.ic_search),contentDescription=null,modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("查看本區房源") } }
   }
   state.fetchedAt?.let { item { Text("本次取得資料時間：${formatTaipeiTime(it)}",style=MaterialTheme.typography.bodyMedium) } }
  }
 }
}
