package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Custom Light Beige Schema
private val CustomBeigeColorScheme = lightColorScheme(
    primary = AntiqueWalnut,
    onPrimary = BookPageSurface,
    secondary = IslamicDeepGreen,
    onSecondary = BookPageSurface,
    tertiary = HadithRustAccent,
    background = BookParchmentBg,
    onBackground = ElegantBronzeCharcoal,
    surface = BookPageSurface,
    onSurface = ElegantBronzeCharcoal,
    surfaceVariant = SoftWarmTan,
    onSurfaceVariant = ElegantBronzeCharcoal,
    outline = BorderColor
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,    // Ignored as requested: Light beige only
    dynamicColor: Boolean = false, // Disabled to lock the custom artisanal brand aesthetic
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CustomBeigeColorScheme,
        typography = Typography,
        content = content
    )
}
