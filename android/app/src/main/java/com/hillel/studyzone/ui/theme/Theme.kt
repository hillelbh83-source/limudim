package com.hillel.studyzone.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.hillel.studyzone.model.ThemeMode

val StudyBlue = Color(0xFF1473FF)
val StudyBlueSoft = Color(0xFF79A8FF)
val Ink = Color(0xFF050608)
val InkRaised = Color(0xFF111318)
val InkSurface = Color(0xFF191C22)
val Paper = Color(0xFFF7F8FA)
val PaperRaised = Color(0xFFFFFFFF)
val PaperSurface = Color(0xFFECEEF2)

private val DarkColors = darkColorScheme(
    primary = StudyBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0E326A),
    onPrimaryContainer = Color(0xFFD7E5FF),
    background = Color.Black,
    onBackground = Color(0xFFF4F5F7),
    surface = InkRaised,
    onSurface = Color(0xFFF4F5F7),
    surfaceVariant = InkSurface,
    onSurfaceVariant = Color(0xFFA9AFBB),
    outline = Color(0xFF30343D),
    outlineVariant = Color(0xFF23262D),
    error = Color(0xFFFF6B72)
)

private val LightColors = lightColorScheme(
    primary = StudyBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E6FF),
    onPrimaryContainer = Color(0xFF08224C),
    background = Paper,
    onBackground = Color(0xFF111318),
    surface = PaperRaised,
    onSurface = Color(0xFF111318),
    surfaceVariant = PaperSurface,
    onSurfaceVariant = Color(0xFF5E6470),
    outline = Color(0xFFD1D5DC),
    outlineVariant = Color(0xFFE2E4E8),
    error = Color(0xFFB3261E)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
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
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            onDispose { }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography.run {
            copy(
                displaySmall = displaySmall.copy(fontSize = displaySmall.fontSize * fontScale),
                headlineLarge = headlineLarge.copy(fontSize = headlineLarge.fontSize * fontScale),
                headlineMedium = headlineMedium.copy(fontSize = headlineMedium.fontSize * fontScale),
                titleLarge = titleLarge.copy(fontSize = titleLarge.fontSize * fontScale),
                titleMedium = titleMedium.copy(fontSize = titleMedium.fontSize * fontScale),
                bodyLarge = bodyLarge.copy(fontSize = bodyLarge.fontSize * fontScale),
                bodyMedium = bodyMedium.copy(fontSize = bodyMedium.fontSize * fontScale)
            )
        },
        shapes = MaterialTheme.shapes.copy(
            small = RoundedCornerShape(14),
            medium = RoundedCornerShape(22),
            large = RoundedCornerShape(30)
        ),
        content = content
    )
}
