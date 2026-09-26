package `in`.potr.bonsai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF3E5A36),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE8D3),
    onPrimaryContainer = Color(0xFF14230F),
    secondary = Color(0xFF9C4F2C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E1D5),
    onSecondaryContainer = Color(0xFF3A1606),
    background = Color(0xFFF6F2EA),
    onBackground = Color(0xFF23261F),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF23261F),
    surfaceVariant = Color(0xFFEAE4D7),
    onSurfaceVariant = Color(0xFF5E5D53),
    surfaceContainer = Color(0xFFF0EBE1),
    surfaceContainerHigh = Color(0xFFEBE5DA),
    outline = Color(0xFFB9B3A5),
    outlineVariant = Color(0xFFE2DCCD),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9CC28D),
    onPrimary = Color(0xFF14230F),
    primaryContainer = Color(0xFF2B4124),
    onPrimaryContainer = Color(0xFFDDE8D3),
    secondary = Color(0xFFE0976F),
    onSecondary = Color(0xFF3A1606),
    secondaryContainer = Color(0xFF5A2F19),
    onSecondaryContainer = Color(0xFFF5E1D5),
    background = Color(0xFF151713),
    onBackground = Color(0xFFECE8DE),
    surface = Color(0xFF1D201A),
    onSurface = Color(0xFFECE8DE),
    surfaceVariant = Color(0xFF33372E),
    onSurfaceVariant = Color(0xFFBDB9AC),
)

@Composable
fun PotrTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
