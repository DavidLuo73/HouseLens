package tw.houselens.viewer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = lightColorScheme(
    primary = Color(0xFFE31C5F), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFF0F3), onPrimaryContainer = Color(0xFF222222),
    background = Color(0xFFF7F7F7), onBackground = Color(0xFF222222),
    surface = Color.White, onSurface = Color(0xFF222222),
    onSurfaceVariant = Color(0xFF717171), outline = Color(0xFF717171)
)
@Composable
fun HouseLensTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Colors, content = content)
