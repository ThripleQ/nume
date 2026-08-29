package com.thripleq.nume.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Crimson,
    background = SurfaceDark,
    surface = SurfaceDark,
    onPrimary = TextOnDark,
    onBackground = TextOnDark,
    onSurface = TextOnDark,
)

private val LightColorScheme = lightColorScheme(
    primary = Crimson,
    onPrimary = OffWhite,
    background = SurfaceLight,
    surface = SurfaceLight,
    onBackground = TextOnLight,
    onSurface = TextOnLight,
)

@Composable
fun NumeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Android 12+ surfaces dynamic color (Material You) when available.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}