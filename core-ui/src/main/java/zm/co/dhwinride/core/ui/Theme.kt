package zm.co.dhwinride.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DhwNavy = Color(0xFF082C67)
val DhwBlue = Color(0xFF0A55A5)
val DhwOrange = Color(0xFFB84600)
val DhwOrangeDark = Color(0xFFD95300)
val DhwGreen = Color(0xFF16723F)
val DhwInk = Color(0xFF172033)
val DhwMuted = Color(0xFF667085)
val DhwSurface = Color(0xFFF4F6F9)
val DhwLine = Color(0xFFE3E8EF)
val DhwMap = Color(0xFFEAF1E9)

private val DhwColors = lightColorScheme(
    primary = DhwOrange,
    onPrimary = Color.White,
    secondary = DhwNavy,
    onSecondary = Color.White,
    tertiary = DhwGreen,
    background = DhwSurface,
    onBackground = DhwInk,
    surface = Color.White,
    onSurface = DhwInk,
    outline = DhwLine,
    error = Color(0xFFC62828),
)

@Composable
fun DhwTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DhwColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
