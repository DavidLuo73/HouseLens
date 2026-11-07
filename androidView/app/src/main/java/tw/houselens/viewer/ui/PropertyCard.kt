package tw.houselens.viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import tw.houselens.viewer.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import tw.houselens.viewer.data.sourceName
import tw.houselens.viewer.data.statusLabel
import tw.houselens.viewer.data.formatDrop
import tw.houselens.viewer.data.mediaUrl
import tw.houselens.viewer.saved.PropertySnapshot

val LocalPropertyImageLoader = staticCompositionLocalOf<ImageLoader?> { null }

@Composable
fun PropertyCard(snapshot: PropertySnapshot, saved: Boolean, onOpen: () -> Unit, onSave: () -> Unit, onHistory: () -> Unit, saveEnabled: Boolean = true) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag("property-${snapshot.id}"), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            val loader = LocalPropertyImageLoader.current
            val url = mediaUrl(snapshot.imageUrl)
            if (loader != null && url != null) SubcomposeAsyncImage(
                model = url, imageLoader = loader, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                loading = { ImagePlaceholder("圖片載入中") }, error = { ImagePlaceholder("圖片無法載入") }
            ) else ImagePlaceholder("暫無圖片")
            FilledIconButton(onClick = onSave, enabled = saveEnabled, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                .size(48.dp).semantics { contentDescription = if(saved) "取消收藏" else "加入收藏" },
                colors=IconButtonDefaults.filledIconButtonColors(
                    containerColor=Color.White.copy(alpha=.96f),contentColor=if(saved) Color(0xFFFF385C) else Color(0xFF222222),
                    disabledContainerColor=Color.White.copy(alpha=.96f),disabledContentColor=Color(0xFF717171))) {
                Icon(painterResource(if(saved) R.drawable.ic_heart_filled else R.drawable.ic_heart),contentDescription=null,modifier=Modifier.size(24.dp))
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${snapshot.city}${snapshot.district}", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(snapshot.score!=null) Icon(painterResource(R.drawable.ic_star_filled),contentDescription=null,modifier=Modifier.size(20.dp),tint=Color(0xFFFF385C))
                Text(snapshot.score?.let { "系統評分 ${it.multiply(java.math.BigDecimal(5)).setScale(1,java.math.RoundingMode.HALF_UP).toPlainString()}" } ?: "尚無評分",style=MaterialTheme.typography.bodyMedium)
            }
            Text(snapshot.title.ifBlank { "未提供標題" }, style = MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text("${snapshot.currentTotalPrice.stripTrailingZeros().toPlainString()} 萬",fontSize = 26.sp)
            Text("單價 ${snapshot.currentUnitPrice?.stripTrailingZeros()?.toPlainString() ?: "—"} 萬／坪",style = MaterialTheme.typography.bodyMedium)
            Text("${snapshot.areaPing?.stripTrailingZeros()?.toPlainString() ?: "—"} 坪 · 屋齡 ${snapshot.ageYears?.let { "$it 年" } ?: "—"} · ${snapshot.floor ?: "樓層 —"}",style = MaterialTheme.typography.bodyMedium)
            Text(if(snapshot.hasParking) "有車位" else "未標示車位",style=MaterialTheme.typography.bodyMedium)
            if(snapshot.sourceSites.isNotEmpty()) Text(snapshot.sourceSites.joinToString("、") {
                sourceName(it)
            },style=MaterialTheme.typography.bodyMedium)
            when {
                snapshot.status!=0 -> Text(statusLabel(snapshot.status))
                snapshot.latestIsBigDrop -> Text("▼ 大降價",color=Color(0xFFC13515))
                snapshot.isNew -> Text("新上架")
            }
            if(snapshot.hasPriceDrop && !snapshot.latestIsBigDrop) Text("▼ 曾有降價紀錄",color=Color(0xFFC13515))
            if(snapshot.latestChangePercent != null) Text("價格變動 ${formatDrop(snapshot.latestChangePercent)}",
                color = when(snapshot.latestChangePercent.signum()) {
                    -1 -> Color(0xFFC13515); 1 -> Color(0xFF008A05); else -> MaterialTheme.colorScheme.onSurface
                })
            TextButton(onClick = onHistory,modifier = Modifier.heightIn(min=48.dp)) { Icon(painterResource(R.drawable.ic_history),contentDescription=null,modifier=Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("價格歷史") }
        }
    }
}
@Composable private fun ImagePlaceholder(text: String) {
    Box(Modifier.fillMaxSize(),contentAlignment = Alignment.Center) { Text(text,style = MaterialTheme.typography.bodyMedium) }
}
