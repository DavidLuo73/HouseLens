package tw.houselens.viewer.detail

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.MathContext
import java.time.OffsetDateTime
import tw.houselens.viewer.data.*
import tw.houselens.viewer.ui.*

data class PriceStats(val high:BigDecimal?,val low:BigDecimal?,val change:BigDecimal?)
internal fun chronologicalHistory(entries:List<PriceEntryDto>)=entries.sortedBy { OffsetDateTime.parse(it.capturedAt).toInstant() }
fun priceStats(entries:List<PriceEntryDto>):PriceStats {
    if(entries.isEmpty()) return PriceStats(null,null,null)
    val sorted=chronologicalHistory(entries)
    val first=OffsetDateTime.parse(sorted.first().capturedAt).toInstant();val last=OffsetDateTime.parse(sorted.last().capturedAt).toInstant()
    val uniqueEndpoints=first!=last && sorted.count { OffsetDateTime.parse(it.capturedAt).toInstant()==first }==1 && sorted.count { OffsetDateTime.parse(it.capturedAt).toInstant()==last }==1
    val change=if(uniqueEndpoints && sorted.first().totalPrice.signum()!=0)
        sorted.last().totalPrice.subtract(sorted.first().totalPrice).divide(sorted.first().totalPrice,MathContext.DECIMAL64) else null
    return PriceStats(entries.maxOf { it.totalPrice },entries.minOf { it.totalPrice },change)
}
private fun recordText(entry:PriceEntryDto)="${formatTaipeiTime(entry.capturedAt)} · ${entry.totalPrice.stripTrailingZeros().toPlainString()} 萬 · 單價 ${entry.unitPrice?.stripTrailingZeros()?.toPlainString() ?: "—"} 萬／坪 · 觀測變動 ${formatDrop(entry.changePercent)}"

@Composable
fun PriceHistory(details:PropertyDetails,initiallyExpanded:Boolean) {
    val ordered=remember(details.priceHistory) { chronologicalHistory(details.priceHistory) }
    val newest=remember(ordered) { ordered.sortedByDescending { OffsetDateTime.parse(it.capturedAt).toInstant() } }
    val points=remember(ordered) { ordered.map { ChartPoint(OffsetDateTime.parse(it.capturedAt).toInstant().toEpochMilli(),it.totalPrice) } }
    val stats=remember(ordered) { priceStats(ordered) }
    var expanded by rememberSaveable(details.property.id) { mutableStateOf(initiallyExpanded) }
    var count by rememberSaveable(details.property.id) { mutableIntStateOf(20) }
    var selected by remember(ordered) { mutableStateOf(emptyList<Int>()) }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("系統擷取價格紀錄",style=MaterialTheme.typography.titleLarge)
        Text("可能包含不同平台的觀測價格",style=MaterialTheme.typography.bodyMedium)
        if(ordered.isEmpty()) Text("尚無價格紀錄") else {
            if(ordered.size==1) Text(recordText(ordered.single()))
            TimePriceChart(points) { selected=it }
            selected.forEach { Text("選取觀測：${recordText(ordered[it])}") }
            Text("最高 ${stats.high?.stripTrailingZeros()?.toPlainString()} 萬 · 最低 ${stats.low?.stripTrailingZeros()?.toPlainString()} 萬")
            Text(if(stats.change!=null) "期間觀測價格變動 ${formatDrop(stats.change)}" else "無法判定期間變動（單筆、零基準或同時間多筆）")
            if(ordered.groupBy { OffsetDateTime.parse(it.capturedAt).toInstant() }.any { it.value.size>1 }) Text("同時間多筆觀測依原順序列出，無法判定先後；圖上不連接該時間的觀測")
            TextButton(onClick={ expanded=!expanded },modifier=Modifier.heightIn(min=48.dp)) { Text(if(expanded) "收合紀錄（${count.coerceAtMost(newest.size)}／${newest.size}）" else "展開紀錄（${count.coerceAtMost(newest.size)}／${newest.size}）") }
            if(expanded) {
                newest.take(count).forEach { entry -> Text(recordText(entry),modifier=Modifier.fillMaxWidth().padding(vertical=8.dp).testTag("history-${entry.id}")) }
                if(count<newest.size) TextButton(onClick={ count+=20 },modifier=Modifier.heightIn(min=48.dp)) { Text("顯示更多") }
            }
        }
    }
}