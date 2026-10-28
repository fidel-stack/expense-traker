package com.xyrospace.expensetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = ForestGreen,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = PaleGreen,
    onPrimaryContainer = DeepGreen,
    secondary = SoftInk,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    tertiary = KenyanSun,
    background = WarmBackground,
    surface = WarmBackground,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = LightForestGreen,
    onPrimary = DeepGreen,
    primaryContainer = ForestGreen,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
    secondary = DarkSoftInk,
    tertiary = KenyanSun,
    background = DarkBackground,
    surface = DarkSurface,
    onSurface = DarkInk,
)

@Composable
fun ExpenseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
