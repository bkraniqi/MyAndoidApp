package com.moneta.finance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneta.finance.domain.ThemeMode

private val Emerald = Color(0xFF0B6B4F)
private val EmeraldDeep = Color(0xFF063D2E)
private val Gold = Color(0xFFB08A35)
private val GoldLight = Color(0xFFE2C47C)

private val LightColors = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFEFE2),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4C635A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE9E3),
    onSecondaryContainer = Color(0xFF0A1F18),
    tertiary = Gold,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6E7C1),
    onTertiaryContainer = Color(0xFF2B2000),
    background = Color(0xFFF4F5F3),
    onBackground = Color(0xFF151917),
    surface = Color(0xFFF4F5F3),
    onSurface = Color(0xFF151917),
    surfaceVariant = Color(0xFFE6EAE7),
    onSurfaceVariant = Color(0xFF5B6460),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFDFDFC),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEFF1EF),
    surfaceContainerHighest = Color(0xFFE8EBE9),
    outline = Color(0xFFB8C0BC),
    outlineVariant = Color(0xFFDCE1DE),
    error = Color(0xFFC4403C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5FD3A4),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0B5A43),
    onPrimaryContainer = Color(0xFFBDF0DA),
    secondary = Color(0xFFB2CCC1),
    onSecondary = Color(0xFF1E352D),
    secondaryContainer = Color(0xFF2A3A34),
    onSecondaryContainer = Color(0xFFD5E8DF),
    tertiary = GoldLight,
    onTertiary = Color(0xFF3D2E00),
    tertiaryContainer = Color(0xFF5A4613),
    onTertiaryContainer = Color(0xFFFBE7B5),
    background = Color(0xFF050606),
    onBackground = Color(0xFFE4E8E6),
    surface = Color(0xFF050606),
    onSurface = Color(0xFFE4E8E6),
    surfaceVariant = Color(0xFF242927),
    onSurfaceVariant = Color(0xFFA4ADA9),
    surfaceContainerLowest = Color(0xFF0B0D0C),
    surfaceContainerLow = Color(0xFF141716),
    surfaceContainer = Color(0xFF171A19),
    surfaceContainerHigh = Color(0xFF1F2321),
    surfaceContainerHighest = Color(0xFF292E2C),
    outline = Color(0xFF59625E),
    outlineVariant = Color(0xFF323836),
    error = Color(0xFFFF8A80),
)

/** Colours outside the Material scheme: income/expense semantics and the hero card gradient. */
@Immutable
data class MonetaColors(
    val income: Color,
    val expense: Color,
    val warning: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    val gold: Color,
    val isDark: Boolean,
)

private val LightExtra = MonetaColors(
    income = Color(0xFF14875A),
    expense = Color(0xFFD0453F),
    warning = Color(0xFFD48A12),
    heroStart = Color(0xFF0E7A59),
    heroEnd = EmeraldDeep,
    onHero = Color.White,
    gold = GoldLight,
    isDark = false,
)

private val DarkExtra = MonetaColors(
    income = Color(0xFF55D69B),
    expense = Color(0xFFFF7A70),
    warning = Color(0xFFFFB648),
    heroStart = Color(0xFF0C5C45),
    heroEnd = Color(0xFF042A20),
    onHero = Color.White,
    gold = GoldLight,
    isDark = true,
)

val LocalMonetaColors = staticCompositionLocalOf { LightExtra }

private val base = Typography()
private val MonetaTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
)

/** Tabular figures keep columns of amounts aligned. */
fun TextStyle.money(): TextStyle = copy(fontFeatureSettings = "tnum", fontFamily = FontFamily.Default)

private val MonetaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MonetaTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalMonetaColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = MonetaTypography,
            shapes = MonetaShapes,
            content = content,
        )
    }
}

object Moneta {
    val colors: MonetaColors
        @Composable get() = LocalMonetaColors.current
}
