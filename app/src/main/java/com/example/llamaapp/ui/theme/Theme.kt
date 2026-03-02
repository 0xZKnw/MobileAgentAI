package com.example.llamaapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    secondary = Secondary,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    error = ErrorColor
)

// Custom color tokens not in M3 spec — accessed via LocalAppColors
data class AppColors(
    val userBubble: Color = UserBubbleColor,
    val aiBubble: Color = AiBubbleColor,
    val thinkingBackground: Color = ThinkingBlockBackground,
    val thinkingBorder: Color = ThinkingBlockBorder,
    val thinkingText: Color = ThinkingBlockText,
    val hudBackground: Color = HudBackground,
    val hudText: Color = HudText
)

val LocalAppColors = staticCompositionLocalOf { AppColors() }

@Composable
fun LlamaAppTheme(
    content: @Composable () -> Unit
) {
    // Always dark — no dynamic color, no light theme toggle
    CompositionLocalProvider(LocalAppColors provides AppColors()) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

// Extension for easy access: MaterialTheme.appColors.userBubble
val MaterialTheme.appColors: AppColors
    @Composable get() = LocalAppColors.current
