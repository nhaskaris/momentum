package com.eliteonetube.momentum.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.eliteonetube.momentum.data.AppTheme

private val DarkColorScheme = darkColorScheme(
    primary = MomentumBlue,
    onPrimary = Color.White,
    primaryContainer = MomentumBlue.copy(alpha = 0.15f),
    onPrimaryContainer = MomentumBlue,
    
    secondary = Color(0xFF71717A), // Neutral Zinc secondary for UI elements
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF27272A),
    onSecondaryContainer = Color(0xFFE4E4E7),
    
    tertiary = MomentumCyan,
    onTertiary = Color.Black,
    
    background = MomentumDark,
    onBackground = Color(0xFFFAFAFA),
    surface = MomentumSurface,
    onSurface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFF27272A),
    onSurfaceVariant = Color(0xFFE4E4E7), // Brightened from D4D4D8
    
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF27272A)
)

private val LightColorScheme = lightColorScheme(
    primary = ProteinBlue,
    onPrimary = Color.White,
    primaryContainer = ProteinBlue.copy(alpha = 0.1f),
    onPrimaryContainer = ProteinBlue,
    
    secondary = CarbGreen,
    onSecondary = Color.White,
    secondaryContainer = CarbGreen.copy(alpha = 0.1f),
    onSecondaryContainer = CarbGreen,
    
    tertiary = FatYellow,
    onTertiary = Color.White,
    tertiaryContainer = FatYellow.copy(alpha = 0.1f),
    onTertiaryContainer = FatYellow,

    background = Color(0xFFF9FAFB),
    surface = Color.White,
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF6B7280),
    
    outline = Color(0xFFE5E7EB),
    outlineVariant = Color(0xFFF3F4F6)
)

@Composable
fun WeeklyCoachTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    // Dynamic color is disabled by default to maintain the "Momentum" brand aesthetic
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use the same surface as the app background. A transparent system bar can
            // reveal the window's default black behind inset content on some devices.
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            window.decorView.setBackgroundColor(colorScheme.background.toArgb())
            
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(32.dp)
        ),
        typography = Typography,
        content = content
    )
}
