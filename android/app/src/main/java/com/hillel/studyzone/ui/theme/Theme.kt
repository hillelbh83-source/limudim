package com.hillel.studyzone.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.hillel.studyzone.model.ThemeMode

val StudyBlue = Color(0xFF0A73FF)
val StudyBlueSoft = Color(0xFF78AFFF)
val Ink = Color(0xFF030406)
val InkRaised = Color(0xFF101217)
val InkSurface = Color(0xFF191C23)
val Paper = Color(0xFFF5F6F8)
val PaperRaised = Color(0xFFFFFFFF)
val PaperSurface = Color(0xFFECEFF3)

private val DarkColors = darkColorScheme(
    primary = StudyBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF12376E),
    onPrimaryContainer = Color(0xFFD8E7FF),
    secondary = StudyBlueSoft,
    onSecondary = Color(0xFF061A38),
    background = Ink,
    onBackground = Color(0xFFF5F6F8),
    surface = InkRaised,
    onSurface = Color(0xFFF5F6F8),
    surfaceVariant = InkSurface,
    onSurfaceVariant = Color(0xFFB1B6C0),
    outline = Color(0xFF343943),
    outlineVariant = Color(0xFF242831),
    error = Color(0xFFFF737A),
    scrim = Color.Black
)

private val LightColors = lightColorScheme(
    primary = StudyBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9FF),
    onPrimaryContainer = Color(0xFF062653),
    secondary = Color(0xFF3569A9),
    onSecondary = Color.White,
    background = Paper,
    onBackground = Color(0xFF111318),
    surface = PaperRaised,
    onSurface = Color(0xFF111318),
    surfaceVariant = PaperSurface,
    onSurfaceVariant = Color(0xFF5D6470),
    outline = Color(0xFFCDD2DA),
    outlineVariant = Color(0xFFE0E4EA),
    error = Color(0xFFB3261E),
    scrim = Color.Black
)

private val BaseTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 36.sp,
        lineHeight = 43.sp,
        letterSpacing = (-.35).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 37.sp,
        letterSpacing = (-.2).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 31.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 27.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 17.sp
    )
)

@Composable
fun StudyZoneTheme(themeMode: ThemeMode, fontScale: Float, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(dark) {
            val activity = view.context as? Activity
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                    window.isStatusBarContrastEnforced = false
                }
            }
            onDispose { }
        }
    }

    val safeScale = fontScale.coerceIn(.85f, 1.35f)
    val typography = remember(safeScale) { BaseTypography.scaled(safeScale) }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = typography,
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(15.dp),
            medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(30.dp),
            extraLarge = RoundedCornerShape(36.dp)
        ),
        content = content
    )
}

private fun Typography.scaled(scale: Float) = copy(
    displaySmall = displaySmall.scaled(scale),
    headlineLarge = headlineLarge.scaled(scale),
    headlineMedium = headlineMedium.scaled(scale),
    titleLarge = titleLarge.scaled(scale),
    titleMedium = titleMedium.scaled(scale),
    bodyLarge = bodyLarge.scaled(scale),
    bodyMedium = bodyMedium.scaled(scale),
    labelLarge = labelLarge.scaled(scale),
    labelMedium = labelMedium.scaled(scale)
)

private fun TextStyle.scaled(scale: Float) = copy(
    fontSize = fontSize * scale,
    lineHeight = lineHeight * scale
)
