package tw.houselens.viewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tw.houselens.viewer.ui.HouseLensTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HouseLensTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
                        Text("HouseLens", style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(16.dp))
                        Text("隨時查看收藏與房價變化", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(24.dp))
                        Text("請先登入核准的 Google 帳號。登入功能準備中。")
                    }
                }
            }
        }
    }
}
