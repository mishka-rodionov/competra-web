package com.competra.web.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Фирменная палитра «Лес» (см. competra-android/core/designsystem/DESIGN.md).
 * Хвойный зелёный + терракота + кремовый фон. Веб зеркалит те же HEX, что и android.
 */
private val LesLightColorScheme = lightColorScheme(
    primary = Color(0xFF2E5D43),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB6E5C9),
    onPrimaryContainer = Color(0xFF0A2417),
    secondary = Color(0xFFC8643C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFCDBC8),
    onSecondaryContainer = Color(0xFF3A1A0C),
    tertiary = Color(0xFF6B7A5A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF4F1E8),
    onBackground = Color(0xFF21302A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF21302A),
    surfaceVariant = Color(0xFFE5E2D6),
    onSurfaceVariant = Color(0xFF4C5A50),
    outline = Color(0xFF7E877C),
    outlineVariant = Color(0xFFC9CEC3),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    surfaceTint = Color(0xFF2E5D43),
    // M3 surface-container роли (Card/NavigationBar/меню) — не описаны в DESIGN.md,
    // выведены как нейтральная тональная лестница кремового фона, чтобы не протекала
    // фиолетовая baseline-палитра Material по умолчанию.
    surfaceDim = Color(0xFFDCD9CC),
    surfaceBright = Color(0xFFFFFBF5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F5EC),
    surfaceContainer = Color(0xFFF1EEE1),
    surfaceContainerHigh = Color(0xFFEBE8DB),
    surfaceContainerHighest = Color(0xFFE5E2D6),
    inverseSurface = Color(0xFF21302A),
    inverseOnSurface = Color(0xFFF4F1E8),
    inversePrimary = Color(0xFF7FCFA0),
)

private val LesDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7FCFA0),
    onPrimary = Color(0xFF06311D),
    primaryContainer = Color(0xFF1E4A34),
    onPrimaryContainer = Color(0xFF9CEBC0),
    secondary = Color(0xFFE8A07C),
    onSecondary = Color(0xFF4A2210),
    secondaryContainer = Color(0xFF693924),
    onSecondaryContainer = Color(0xFFFCDBC8),
    tertiary = Color(0xFFAEBE96),
    onTertiary = Color(0xFF2B3420),
    background = Color(0xFF121A15),
    onBackground = Color(0xFFE1E6DD),
    surface = Color(0xFF1B271F),
    onSurface = Color(0xFFE1E6DD),
    surfaceVariant = Color(0xFF3F4A42),
    onSurfaceVariant = Color(0xFFBFCABE),
    outline = Color(0xFF899389),
    outlineVariant = Color(0xFF3F4A42),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    surfaceTint = Color(0xFF7FCFA0),
    surfaceDim = Color(0xFF121A15),
    surfaceBright = Color(0xFF39443D),
    surfaceContainerLowest = Color(0xFF0D1310),
    surfaceContainerLow = Color(0xFF1B271F),
    surfaceContainer = Color(0xFF202D26),
    surfaceContainerHigh = Color(0xFF2A3830),
    surfaceContainerHighest = Color(0xFF3F4A42),
    inverseSurface = Color(0xFFE1E6DD),
    inverseOnSurface = Color(0xFF121A15),
    inversePrimary = Color(0xFF2E5D43),
)

private val LesShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun CompetiraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) LesDarkColorScheme else LesLightColorScheme,
        shapes = LesShapes,
        content = content,
    )
}
