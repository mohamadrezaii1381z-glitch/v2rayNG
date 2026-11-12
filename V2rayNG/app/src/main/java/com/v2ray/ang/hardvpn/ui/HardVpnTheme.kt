package com.v2ray.ang.hardvpn.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.v2ray.ang.R

/** HardVPN's fixed dark-navy brand palette. The brand never follows system light theme. */
object HardVpnColors {
    val Background = Color(0xFF040A18)
    val BackgroundDeep = Color(0xFF02060F)
    val Surface = Color(0xFF0C1730)
    val SurfaceHigh = Color(0xFF122043)
    val Border = Color(0xFF1E3263)
    val Accent = Color(0xFF3B82F6)
    val AccentBright = Color(0xFF6EA8FF)
    val Success = Color(0xFF22D18A)
    val Gold = Color(0xFFF6C453)
    val GoldDeep = Color(0xFFA97A1F)
    val TextPrimary = Color(0xFFF3F7FF)
    val TextSecondary = Color(0xFF9DB0D6)
    val TextMuted = Color(0xFF6F82A8)
    val Danger = Color(0xFFFF7A7A)
}

@Composable
fun HardVpnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HardVpnColors.Accent,
            onPrimary = Color.White,
            primaryContainer = HardVpnColors.SurfaceHigh,
            onPrimaryContainer = HardVpnColors.TextPrimary,
            secondary = HardVpnColors.Success,
            onSecondary = Color(0xFF002015),
            tertiary = HardVpnColors.Gold,
            onTertiary = Color(0xFF241800),
            background = HardVpnColors.Background,
            onBackground = HardVpnColors.TextPrimary,
            surface = HardVpnColors.Surface,
            onSurface = HardVpnColors.TextPrimary,
            surfaceVariant = HardVpnColors.SurfaceHigh,
            onSurfaceVariant = HardVpnColors.TextSecondary,
            outline = HardVpnColors.Border,
            error = HardVpnColors.Danger,
            onError = Color(0xFF2B0000),
        ),
        content = content,
    )
}

/**
 * Full-bleed globe artwork with a readability scrim.
 *
 * The image is a static design element: it never changes with the selected country, and no flag
 * is ever drawn as the background. The gradient underneath is the graceful fallback.
 */
@Composable
fun HardVpnBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        HardVpnColors.BackgroundDeep,
                        Color(0xFF071128),
                        HardVpnColors.BackgroundDeep,
                    )
                )
            )
    ) {
        Image(
            painter = painterResource(R.drawable.hardvpn_globe),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xF2050B1C),
                        0.30f to Color(0xB3050B1C),
                        0.62f to Color(0xD9050B1C),
                        1f to Color(0xF7050B1C),
                    )
                )
        )
        content()
    }
}

/** Shared background for the secondary destinations: navy gradient with an accent glow. */
@Composable
fun HardVpnSecondaryBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF10224A), HardVpnColors.Background, HardVpnColors.BackgroundDeep),
                    radius = 1400f,
                )
            )
    ) {
        content()
    }
}
