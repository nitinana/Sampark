package com.sampark.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SamparkColorScheme = lightColorScheme(
    primary = TranslateAmber,
    background = BackgroundColor,
    surface = BackgroundColor,
    onBackground = HeadingTextColor,
    onSurface = HeadingTextColor
)

@Composable
fun SamparkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SamparkColorScheme,
        typography = SamparkTypography,
        content = content
    )
}
