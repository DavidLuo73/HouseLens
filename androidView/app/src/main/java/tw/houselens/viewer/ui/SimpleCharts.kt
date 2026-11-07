package tw.houselens.viewer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs

data class ChartPoint(val x:Long,val y:BigDecimal)

internal fun chartCoordinates(points:List<ChartPoint>):List<Pair<Float,Float>> {
    if(points.isEmpty()) return emptyList()
    val minX=BigDecimal.valueOf(points.minOf { it.x });val maxX=BigDecimal.valueOf(points.maxOf { it.x })
    val minY=points.minOf { it.y };val maxY=points.maxOf { it.y }
    fun ratio(value:BigDecimal,base:BigDecimal,limit:BigDecimal):Float =
        if(limit.compareTo(base)==0) 0.5f else value.subtract(base).divide(limit.subtract(base),MathContext.DECIMAL64).toFloat()
    return points.map { ratio(BigDecimal.valueOf(it.x),minX,maxX) to ratio(it.y,minY,maxY) }
}
internal fun overlappingPoints(points:List<ChartPoint>,selected:Int):List<Int> = points.indices.filter { points[it].x==points[selected].x }

@Composable
fun TimePriceChart(points:List<ChartPoint>,onSelected:(List<Int>)->Unit) {
    if(points.isEmpty()) return
    val coordinates=remember(points) { chartCoordinates(points) }
    val counts=remember(points) { points.groupingBy { it.x }.eachCount() }
    val currentSelected by rememberUpdatedState(onSelected)
    Canvas(Modifier.fillMaxWidth().height(180.dp).semantics {
        contentDescription="價格觀測圖，共 ${points.size} 筆；日期、價格與變動請閱讀下方紀錄"
    }.pointerInput(points) {
        detectTapGestures { tap ->
            val inset=12.dp.toPx();val width=(size.width-2*inset).coerceAtLeast(1f)
            val selected=coordinates.indices.minByOrNull { abs(inset+coordinates[it].first*width-tap.x) }
            if(selected!=null) currentSelected(overlappingPoints(points,selected))
        }
    }) {
        val inset=12.dp.toPx()
        val positions=coordinates.map { Offset(inset+it.first*(size.width-2*inset),inset+(1-it.second)*(size.height-2*inset)) }
        drawLine(Color(0xFF717171),Offset(inset,size.height-inset),Offset(size.width-inset,size.height-inset),1.dp.toPx())
        positions.zipWithNext().forEachIndexed { index,(a,b) ->
            // A tied observation has no known order, so do not connect through that timestamp.
            if(counts[points[index].x]==1 && counts[points[index+1].x]==1) drawLine(Color(0xFFE31C5F),a,b,2.dp.toPx())
        }
        positions.forEach { drawCircle(Color(0xFFE31C5F),4.dp.toPx(),it) }
    }
}