package de.stickertresor.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Eigenes dunkles Design: fast schwarz, kühle Grautöne, Mint als Akzent. */
object Palette {
    val Background = Color(0xFF0E1014)
    val Surface = Color(0xFF14171D)
    val Card = Color(0xFF1A1E25)
    val CardHigh = Color(0xFF222731)
    val Line = Color(0xFF2B313C)
    val Text = Color(0xFFECEFF4)
    val TextMuted = Color(0xFF9AA3B2)
    val Accent = Color(0xFF74F0B0)
    val AccentDark = Color(0xFF0A2E1E)
    val AccentSoft = Color(0xFF1B3A2D)
    val Warm = Color(0xFFFFB86B)
    val Danger = Color(0xFFFF6B6B)

    /** Farben für Kategorien. Der Index wird gespeichert. */
    val CategoryColors = listOf(
        Color(0xFFFF7A6B), // Koralle
        Color(0xFFFFC15E), // Bernstein
        Color(0xFFB6E36A), // Limette
        Color(0xFF74F0B0), // Mint
        Color(0xFF6CB8FF), // Himmel
        Color(0xFFA78BFA), // Violett
        Color(0xFFF78FC4), // Pink
        Color(0xFFD6C29A), // Sand
    )

    fun category(index: Int): Color = CategoryColors[Math.floorMod(index, CategoryColors.size)]
}

private val scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.AccentDark,
    primaryContainer = Palette.AccentSoft,
    onPrimaryContainer = Palette.Accent,
    secondary = Palette.Warm,
    onSecondary = Color(0xFF3A2100),
    secondaryContainer = Palette.CardHigh,
    onSecondaryContainer = Palette.Text,
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Card,
    onSurfaceVariant = Palette.TextMuted,
    surfaceContainerLowest = Palette.Background,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Card,
    surfaceContainerHigh = Palette.CardHigh,
    surfaceContainerHighest = Color(0xFF2A303B),
    outline = Palette.Line,
    outlineVariant = Palette.Line,
    error = Palette.Danger,
    onError = Color(0xFF3B0000),
)

private val type = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

private val shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun TresorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, shapes = shapes, content = content)
}
