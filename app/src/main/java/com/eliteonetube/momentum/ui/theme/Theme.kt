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
    onPrimary = Color.Black,
    primaryContainer = MomentumBlue.copy(alpha = 0.2f),
    onPrimaryContainer = MomentumBlue,
    
    secondary = CarbGreen,
    onSecondary = Color.Black,
    secondaryContainer = CarbGreen.copy(alpha = 0.2f),
    onSecondaryContainer = CarbGreen,
    
    tertiary = FatYellow,
    onTertiary = Color.Black,
    tertiaryContainer = FatYellow.copy(alpha = 0.2f),
    onTertiaryContainer = FatYellow,
    
    background = MomentumDark,
    onBackground = Color.White,
    surface = MomentumSurface,
    onSurface = Color.White,
    surfaceVariant = MomentumSurface.copy(alpha = 0.7f),
    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
    
    outline = MomentumBlue.copy(alpha = 0.5f),
    outlineVariant = Color.White.copy(alpha = 0.1f)
)

private val LightColorScheme = lightColorScheme(
    primary = ProteinBlue,
    secondary = CarbGreen,
    tertiary = FatYellow,
    background = Color(0xFFF8F9FA),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
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
