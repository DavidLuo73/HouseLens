package tw.houselens.viewer.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

@Composable
fun DataStatusSection(viewModel:DataStatusViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("資料狀態",style=MaterialTheme.typography.titleLarge)
        if(state.loading) Text("正在讀取資料狀態")
        if(state.fetchedAt!=null || (!state.loading && state.error==null)) runSummary(state.run).forEach { Text(it) }
        if(state.fetchedAt!=null && (state.loading || state.error!=null)) Text("保留上次資料狀態（舊資料）")
        state.error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite }) }
        state.fetchedAt?.let { Text("本次取得資料時間：${tw.houselens.viewer.data.formatTaipeiTime(it)}",style=MaterialTheme.typography.bodyMedium) }
        TextButton(onClick=viewModel::refresh,enabled=!state.loading,modifier=Modifier.heightIn(min=48.dp)) { Text("更新資料狀態") }
    }
}
