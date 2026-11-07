package tw.houselens.viewer.support

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import tw.houselens.viewer.AppGraph
import tw.houselens.viewer.ViewerContent

/** Synthetic test host only; recreation follows the production root's onCreate mounting. */
class ViewerTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val currentGraph = requireNotNull(graph)
        setContent { ViewerContent(currentGraph) }
    }
    companion object {
        var graph: AppGraph? = null
    }
}
