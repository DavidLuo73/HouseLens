package tw.houselens.viewer.detail

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import tw.houselens.viewer.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import java.net.URI
import tw.houselens.viewer.data.*
import tw.houselens.viewer.ui.LocalPropertyImageLoader

private fun sourceLabel(source:SourceDto):String = "${sourceName(source.sourceSite)} · ${externalListingUrl(source.url)?.let { URI(it).host } ?: "無效網址"}"

@Composable
fun DetailScreen(viewModel:DetailViewModel,initiallyExpanded:Boolean,onBack:()->Unit,onOpenSource:((String)->Unit)?=null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val details=state.details
    val context=LocalContext.current
    val list=rememberLazyListState()
    var targeted by rememberSaveable { mutableStateOf(false) }
    var chooseSource by rememberSaveable { mutableStateOf(false) }
    var browserError by remember { mutableStateOf(false) }
    val sources=details?.let { it.sources.ifEmpty { it.property.listingUrl?.let { url -> listOf(SourceDto(it.property.sourceSites.firstOrNull() ?: -1,url)) } ?: emptyList() } }.orEmpty()
    val valid=sources.filter { externalListingUrl(it.url)!=null }
    fun open(source:SourceDto) {
        val url=externalListingUrl(source.url) ?: return
        chooseSource=false
        try {
            if(onOpenSource!=null) onOpenSource(url) else CustomTabsIntent.Builder().build().launchUrl(context,Uri.parse(url))
        } catch(_:ActivityNotFoundException) { browserError=true }
    }
    LaunchedEffect(details?.property?.id,initiallyExpanded) {
        if(details!=null && initiallyExpanded && !targeted) {
            list.scrollToItem(5);targeted=true
        }
    }
    Scaffold(topBar={
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=8.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick=onBack,modifier=Modifier.heightIn(min=48.dp)) { Icon(painterResource(R.drawable.ic_back),contentDescription=null,modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("返回") }
            TextButton(onClick=viewModel::toggleFavorite,enabled=!state.saving && (state.saved || details!=null),modifier=Modifier.heightIn(min=48.dp)) { Icon(painterResource(if(state.saved) R.drawable.ic_heart_filled else R.drawable.ic_heart),contentDescription=null,modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(if(state.saved) "取消收藏" else "加入收藏") }
        }
    },bottomBar={
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            if(valid.isEmpty()) Text("沒有可開啟的來源網址",style=MaterialTheme.typography.bodyMedium)
            Button(onClick={ if(sources.size>1) chooseSource=true else valid.firstOrNull()?.let(::open) },enabled=valid.isNotEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Icon(painterResource(R.drawable.ic_external),contentDescription=null,modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("查看原始刊登") }
        }
    }) { padding ->
        LazyColumn(state=list,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp),modifier=Modifier.fillMaxSize().padding(padding).testTag("detail-list")) {
            item {
                Column {
                    if(state.storageError!=null) Text(state.storageError!!,color=MaterialTheme.colorScheme.error)
                    if(state.error!=null) {
                        if(details!=null) Text("保留上次取得的舊資料")
                        Text(state.error!!,color=MaterialTheme.colorScheme.error)
                        TextButton(onClick=viewModel::refresh,modifier=Modifier.heightIn(min=48.dp)) { Text("重試詳情") }
                    }
                    if(browserError) Text("無法開啟瀏覽器，請稍後重試")
                    if(state.loading) {
                        Text(if(details==null) "詳情載入中" else "正在更新詳情")
                        if(details==null) Box(Modifier.fillMaxWidth().aspectRatio(4f/3f).background(MaterialTheme.colorScheme.surfaceVariant))
                    }
                    if(state.notFound) Text("此物件已移除，可返回或取消收藏")
                }
            }
            if(details!=null) {
                val property=details.property
                item {
                    Box(Modifier.fillMaxWidth().aspectRatio(4f/3f).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center) {
                        val loader=LocalPropertyImageLoader.current;val url=mediaUrl(property.imageUrl)
                        if(loader!=null && url!=null) SubcomposeAsyncImage(model=url,imageLoader=loader,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize(),loading={ Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text("圖片載入中") } },error={ Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text("圖片無法載入") } })
                        else Text(if(property.imageUrl!=null && externalListingUrl(property.imageUrl)!=null && url==null) "影片請至原始刊登查看" else "暫無圖片")
                    }
                }
                item { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(property.title.ifBlank { "未提供標題" },style=MaterialTheme.typography.headlineSmall)
                    Text("${property.city}${property.district} · ${statusLabel(property.status)}")
                    Text("目前總價 ${property.currentTotalPrice.stripTrailingZeros().toPlainString()} 萬",style=MaterialTheme.typography.headlineMedium)
                    Text("目前單價 ${property.currentUnitPrice?.stripTrailingZeros()?.toPlainString() ?: "—"} 萬／坪")
                } }
                item { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("基本資料",style=MaterialTheme.typography.titleLarge)
                    Text("地址：${property.address ?: "—"}")
                    Text("坪數：${property.areaPing?.stripTrailingZeros()?.toPlainString() ?: "—"} 坪")
                    Text("樓層：${property.floor ?: "—"}");Text("屋齡：${property.ageYears?.let { "$it 年" } ?: "—"}")
                    Text(if(property.hasParking) "有車位" else "未標示車位")
                    Text("首見：${property.firstSeenAt?.let(::formatTaipeiTime) ?: "—"}")
                    Text("最後觀測：${property.lastSeenAt?.let(::formatTaipeiTime) ?: "—"}")
                } }
                item { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(property.score!=null) Icon(painterResource(R.drawable.ic_star_filled),contentDescription=null,modifier=Modifier.size(24.dp),tint=Color(0xFFFF385C))
                    Text(property.score?.let { "系統評分 ${it.multiply(java.math.BigDecimal(5)).setScale(1,java.math.RoundingMode.HALF_UP).toPlainString()}" } ?: "尚無評分",style=MaterialTheme.typography.titleLarge)
                } }
                item { PriceHistory(details,initiallyExpanded) }
                item { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("來源與時間",style=MaterialTheme.typography.titleLarge)
                    sources.forEach { source ->
                        TextButton(onClick={ open(source) },enabled=externalListingUrl(source.url)!=null,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Icon(painterResource(R.drawable.ic_external),contentDescription=null,modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(sourceLabel(source)) }
                        source.postedDate?.let { Text("來源刊登日期：${formatDateOnly(it)}") }
                    }
                    if(sources.isEmpty()) Text("沒有來源資料")
                    state.fetchedAt?.let { Text("本次取得資料時間：${formatTaipeiTime(it)}") }
                    TextButton(onClick=viewModel::refresh,enabled=!state.loading,modifier=Modifier.heightIn(min=48.dp)) { Icon(painterResource(R.drawable.ic_refresh),contentDescription=null,modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("重新整理詳情") }
                } }
            }
        }
    }
    if(chooseSource) AlertDialog(onDismissRequest={ chooseSource=false },title={ Text("選擇原始刊登") },text={ Column(Modifier.verticalScroll(rememberScrollState())) { sources.forEach { source -> TextButton(onClick={ open(source) },enabled=externalListingUrl(source.url)!=null,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Icon(painterResource(R.drawable.ic_external),contentDescription=null,modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(sourceLabel(source)) } } } },confirmButton={ TextButton(onClick={ chooseSource=false }) { Text("關閉") } })
}