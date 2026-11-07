package tw.houselens.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import tw.houselens.viewer.ui.*

@Composable
fun ViewerContent(graph: AppGraph) {
    HouseLensTheme {
        Surface(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalPropertyImageLoader provides graph.session.imageLoader) {
                HouseLensNav(graph)
            }
        }
    }
}
