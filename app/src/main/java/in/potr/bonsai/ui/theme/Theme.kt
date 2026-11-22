package `in`.potr.bonsai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.potr.bonsai.R

// POTR Botanical Studio Palette
val ForestGreen = Color(0xFF2D4A3E)
val ForestGreenDeep = Color(0xFF1F352C)
val SageWash = Color(0xFFEAF1EE)
val SageBorder = Color(0xFFC6DDD2)

val Terracotta = Color(0xFFB85D3B)
val TerracottaWash = Color(0xFFFDF1EC)
val TerracottaBorder = Color(0xFFE6B8A7)

val AmberInk = Color(0xFF92400E)
val AmberWash = Color(0xFFFEF3C7)

val WarmStoneCanvas = Color(0xFFF6F4EF)
val GallerySurface = Color(0xFFFFFFFF)
val SoftStoneSurface = Color(0xFFFAF9F6)
val SandSurfaceVariant = Color(0xFFF2EFE8)
val SandOutline = Color(0xFFE5E0D5)

val ForestInk = Color(0xFF1F2421)
val BarkMuted = Color(0xFF5C584F)
val PebbleHint = Color(0xFF6E6A61)

val FrauncesFamily = FontFamily(
    Font(R.font.fraunces, FontWeight.Normal),
    Font(R.font.fraunces, FontWeight.Medium),
    Font(R.font.fraunces, FontWeight.SemiBold),
    Font(R.font.fraunces, FontWeight.Bold)
)

val PlusJakartaFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.SemiBold)
)

private val PotrColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = SageWash,
    onPrimaryContainer = ForestGreenDeep,
    secondary = Terracotta,
    onSecondary = Color.White,
    secondaryContainer = TerracottaWash,
    onSecondaryContainer = Terracotta,
    tertiary = AmberInk,
    onTertiary = Color.White,
    tertiaryContainer = AmberWash,
    onTertiaryContainer = AmberInk,
    background = WarmStoneCanvas,
    onBackground = ForestInk,
    surface = GallerySurface,
    onSurface = ForestInk,
    surfaceVariant = SandSurfaceVariant,
    onSurfaceVariant = BarkMuted,
    surfaceContainerLowest = GallerySurface,
    surfaceContainerLow = SoftStoneSurface,
    surfaceContainer = WarmStoneCanvas,
    surfaceContainerHigh = SandSurfaceVariant,
    outline = SandOutline,
    outlineVariant = SandOutline,
    error = Terracotta,
    onError = Color.White,
    errorContainer = TerracottaWash,
    onErrorContainer = Terracotta
)

val PotrTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    titleSmall = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

val PotrShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PotrBonsaiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PotrColorScheme,
        typography = PotrTypography,
        shapes = PotrShapes,
        content = content
    )
}
