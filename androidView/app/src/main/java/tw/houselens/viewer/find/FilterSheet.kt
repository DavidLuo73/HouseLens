package tw.houselens.viewer.find

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tw.houselens.viewer.data.*

internal val sortLabels=linkedMapOf("score" to "系統評分優先","price" to "總價低到高","unitPrice" to "單價排序（未提供優先）","newest" to "最近收錄")
internal data class QueryChip(val label: String,val removed: PropertyQuery)
internal fun queryChips(query: PropertyQuery): List<QueryChip> = buildList {
    query.districts.forEach { name -> add(QueryChip(name,query.copy(districts=query.districts-name))) }
    query.sourceSites.forEach { source -> add(QueryChip(sourceName(source),query.copy(sourceSites=query.sourceSites-source))) }
    query.minPrice?.let { add(QueryChip("最低 ${it.toPlainString()} 萬",query.copy(minPrice=null))) }
    query.maxPrice?.let { add(QueryChip("最高 ${it.toPlainString()} 萬",query.copy(maxPrice=null))) }
    query.hasParking?.let { add(QueryChip(if(it) "有車位" else "未標示車位",query.copy(hasParking=null))) }
    if(query.priceDropped) add(QueryChip("曾有降價紀錄",query.copy(priceDropped=false)))
    if(query.status!=0) add(QueryChip("已下架",query.copy(status=0)))
    if(query.sortBy!="score") add(QueryChip(sortLabels.getValue(query.sortBy),query.copy(sortBy="score")))
}

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(initial: PropertyQuery,choices: List<DistrictChoice>,onApply: (PropertyQuery) -> Unit,onDismiss: () -> Unit) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var min by remember(initial) { mutableStateOf(initial.minPrice?.toPlainString().orEmpty()) }
    var max by remember(initial) { mutableStateOf(initial.maxPrice?.toPlainString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        dragHandle=null) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.95f).padding(horizontal=16.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("篩選房源",style=MaterialTheme.typography.headlineSmall)
                TextButton(onClick=onDismiss,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) { Text("關閉篩選") }
                Text("行政區",style=MaterialTheme.typography.titleMedium)
                FilterChip(selected=draft.districts.isEmpty(),onClick={ draft=draft.copy(districts=emptyList()) },label={ Text("全部行政區") },modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp))
                val available=choices+initial.districts.filter { name -> choices.none { it.name==name } }.map { DistrictChoice(it,null) }
                available.groupBy { it.cityLabel }.forEach { (city,group) ->
                    if(city!=null && city!="所有同名行政區") Text(city,style=MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        group.forEach { choice ->
                            FilterChip(selected=choice.name in draft.districts,onClick={ draft=draft.copy(districts=if(choice.name in draft.districts) draft.districts-choice.name else draft.districts+choice.name) },
                                label={ Text(if(choice.cityLabel=="所有同名行政區") "${choice.name}（所有同名行政區）" else choice.name) },modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp))
                        }
                    }
                }
                Text("平台",style=MaterialTheme.typography.titleMedium)
                FilterChip(selected=draft.sourceSites.isEmpty(),onClick={ draft=draft.copy(sourceSites=emptyList()) },label={ Text("全部平台") },modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp))
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    sourceNames.forEachIndexed { id,label -> FilterChip(selected=id in draft.sourceSites,onClick={ draft=draft.copy(sourceSites=if(id in draft.sourceSites) draft.sourceSites-id else draft.sourceSites+id) },label={ Text(label) },modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) }
                }
                OutlinedTextField(value=min,onValueChange={ min=it;error=null },label={ Text("最低總價（萬元）") },keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth(),singleLine=true,isError=error!=null)
                OutlinedTextField(value=max,onValueChange={ max=it;error=null },label={ Text("最高總價（萬元）") },keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth(),singleLine=true,isError=error!=null)
                if(error!=null) Text(error!!,color=MaterialTheme.colorScheme.error)
                Text("車位",style=MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(null to "車位不限",true to "有車位",false to "未標示車位").forEach { (value,label) ->
                        FilterChip(selected=draft.hasParking==value,onClick={ draft=draft.copy(hasParking=value) },label={ Text(label) },modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp))
                    }
                }
                Text("曾有降價紀錄")
                Switch(checked=draft.priceDropped,onCheckedChange={ draft=draft.copy(priceDropped=it) },modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).semantics { contentDescription="曾有降價紀錄" })
                Text("狀態",style=MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("在售","已下架").forEachIndexed { value,label -> FilterChip(selected=draft.status==value,onClick={ draft=draft.copy(status=value) },label={ Text(label) },modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) }
                }
                Spacer(Modifier.height(16.dp))
            }
            Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={ draft=PropertyQuery();min="";max="";error=null },modifier=Modifier.weight(1f).sizeIn(minWidth=48.dp,minHeight=48.dp)) { Text("重設") }
                Button(onClick={
                    val query=draft.copy(minPrice=min.trim().toBigDecimalOrNull(),maxPrice=max.trim().toBigDecimalOrNull())
                    error=if((min.isNotBlank() && query.minPrice==null) || (max.isNotBlank() && query.maxPrice==null)) "請輸入有效總價" else validateQuery(query)
                    if(error==null) onApply(query)
                },modifier=Modifier.weight(1f).sizeIn(minWidth=48.dp,minHeight=48.dp)) { Text("套用") }
            }
        }
    }
}
