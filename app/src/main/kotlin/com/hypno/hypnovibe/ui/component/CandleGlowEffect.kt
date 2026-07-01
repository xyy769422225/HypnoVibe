package com.hypno.hypnovibe.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.hypno.hypnovibe.ui.theme.BloodRed

/**
 * 烛光光晕效果 — 径向渐变发光，用于播放按钮和进度指示器。
 * 光晕以 alpha 呼吸动画脉动。
 */
@Composable
fun CandleGlowEffect(
    modifier: Modifier = Modifier,
    color: Color = BloodRed,
    radius: Float = 80f,
    alphaRange: ClosedFloatingPointRange<Float> = 0.15f..0.40f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "candleGlow")
    val alpha by infiniteTransition.animateFloat(
        initialValue = alphaRange.start,
        targetValue = alphaRange.endInclusive,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val glowRadius = radius.coerceAtMost(size.minDimension / 2f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = alpha),
                    color.copy(alpha = alpha * 0.3f),
                    Color.Transparent
                ),
                center = center,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = center
        )
    }
}
