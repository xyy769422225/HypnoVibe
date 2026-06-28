package com.hypno.hypnovibe.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DungeonDarkColorScheme = darkColorScheme(
    primary = BloodRed,
    onPrimary = SilverGray,
    primaryContainer = BloodRed.copy(alpha = 0.3f),
    secondary = DarkPurple,
    onSecondary = SilverGray,
    secondaryContainer = DarkPurple.copy(alpha = 0.3f),
    tertiary = GoldAncient,
    background = AbyssBlack,
    onBackground = SilverGray,
    surface = DarkStoneBrown,
    onSurface = SilverGray,
    surfaceVariant = StoneGray,
    onSurfaceVariant = DarkGray,
    outline = DarkCopper,
    error = AlertRed,
    onError = SilverGray,
)

@Composable
fun HypnoVibeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DungeonDarkColorScheme,
        typography = DungeonTypography,
        shapes = DungeonShapes,
        content = content
    )
}
