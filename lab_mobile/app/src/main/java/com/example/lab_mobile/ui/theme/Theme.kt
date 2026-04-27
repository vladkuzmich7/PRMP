package com.example.lab_mobile3.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.lab_mobile3.AppTheme

private val LightColorScheme = lightColorScheme(
    primary = MiOrange,
    secondary = MiGrayButton,
    tertiary = MiDarkGrayButton,
    background = MiBackground,
    surface = MiSurface,
    onPrimary = White,
    onSecondary = MiTextPrimary,
    onTertiary = MiTextPrimary,
    onBackground = MiTextPrimary,
    onSurface = MiTextPrimary
)

private val DarkColorScheme = darkColorScheme(
    primary = MiOrange,
    secondary = MiGrayButtonDark,
    tertiary = MiDarkGrayButtonDark,
    background = MiBackgroundDark,
    surface = MiSurfaceDark,
    onPrimary = White,
    onSecondary = MiTextPrimaryDark,
    onTertiary = MiTextPrimaryDark,
    onBackground = MiTextPrimaryDark,
    onSurface = MiTextPrimaryDark
)

private val OrangeColorScheme = lightColorScheme(
    primary = OrangePrimary,
    background = OrangeBackground,
    surface = OrangeSurface,
    onPrimary = Color.White,
    onBackground = Color.Black
)

private val BlueColorScheme = lightColorScheme(
    primary = BluePrimary,
    background = BlueBackground,
    surface = BlueSurface,
    onPrimary = Color.White,
    onBackground = Color.Black
)

@Composable
fun Lab_mobileTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.SYSTEM -> if (darkTheme) DarkColorScheme else LightColorScheme
        AppTheme.LIGHT -> LightColorScheme
        AppTheme.DARK -> DarkColorScheme
        AppTheme.ORANGE -> OrangeColorScheme
        AppTheme.BLUE -> BlueColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = 
                !(appTheme == AppTheme.DARK || (appTheme == AppTheme.SYSTEM && darkTheme))
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
