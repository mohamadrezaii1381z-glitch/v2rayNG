package com.v2ray.ang.ui.hardvpn

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Connection panel transcribed from the design: real timer, real state label and the
 * circular power control with its rotating ring.
 */
@Composable
fun ConnectionPanel(
    phase: ConnectionPhase,
    sessionStartedAt: Long,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    val statusText = when (phase) {
        ConnectionPhase.Disconnected -> "Disconnected"
        ConnectionPhase.Connecting -> "Connecting…"
        ConnectionPhase.Connected -> "Connected"
        ConnectionPhase.Disconnecting -> "Stopping…"
        ConnectionPhase.Error -> "Connection failed"
    }
    val statusTargetColor = when (phase) {
        ConnectionPhase.Connected -> HardGreen
        ConnectionPhase.Connecting -> LightBlue
        ConnectionPhase.Error -> Color(0xFFFF8A9B)
        ConnectionPhase.Disconnecting,
        ConnectionPhase.Disconnected -> Muted
    }
    val statusColor by animateColorAsState(
        targetValue = statusTargetColor,
        animationSpec = tween(220),
        label = "statusColor",
    )
    val caption = when (phase) {
        ConnectionPhase.Connected -> "Tap to disconnect"
        ConnectionPhase.Connecting -> "Establishing the tunnel…"
        ConnectionPhase.Disconnecting -> "Stopping the tunnel…"
        ConnectionPhase.Error -> "Tap to try again"
        ConnectionPhase.Disconnected -> "Tap to connect"
    }

    BoxWithConstraints(modifier = modifier) {
        val stageSize = minOf(
            (maxHeight - 110.dp).coerceIn(64.dp, 202.dp),
            maxWidth,
        )
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SessionTimer(sessionStartedAt = sessionStartedAt, compact = compact)
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(statusColor, CircleShape)
                    )
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            PowerButton(
                phase = phase,
                stageSize = stageSize,
                onClick = onToggle,
            )

            Text(
                text = caption,
                modifier = Modifier.padding(bottom = 4.dp),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Real session timer. It only runs while the daemon reports a live tunnel and a start time
 * is known; a session that predates this UI shows "--:--:--" instead of a made-up duration.
 */
@Composable
fun SessionTimer(sessionStartedAt: Long, compact: Boolean) {
    var elapsedSeconds by remember(sessionStartedAt) { mutableLongStateOf(-1L) }

    LaunchedEffect(sessionStartedAt) {
        if (sessionStartedAt <= 0L) {
            elapsedSeconds = -1L
            return@LaunchedEffect
        }
        while (true) {
            val elapsed = (System.currentTimeMillis() - sessionStartedAt).coerceAtLeast(0L)
            elapsedSeconds = elapsed / 1000L
            // Align to the next second boundary; one lightweight wake per second at most.
            delay(1_000L - (elapsed % 1_000L))
        }
    }

    val text = if (elapsedSeconds < 0L) "--:--:--" else formatDuration(elapsedSeconds)
    Text(
        text = text,
        color = Color.White,
        fontSize = if (compact) 34.sp else 38.sp,
        lineHeight = if (compact) 40.sp else 44.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = (-1.5).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics {
            contentDescription = if (elapsedSeconds < 0L) {
                "Session duration unavailable"
            } else {
                "Connected for $text"
            }
        },
    )
}

@Composable
private fun PowerButton(
    phase: ConnectionPhase,
    stageSize: Dp,
    onClick: () -> Unit,
) {
    val connected = phase == ConnectionPhase.Connected
    val ringVisible = phase == ConnectionPhase.Connecting ||
            phase == ConnectionPhase.Connected ||
            phase == ConnectionPhase.Disconnecting

    val accent by animateColorAsState(
        targetValue = if (connected || phase == ConnectionPhase.Disconnecting) HardGreen else ElectricBlue,
        animationSpec = tween(POWER_COLOR_MS, easing = FastOutSlowInEasing),
        label = "powerAccent",
    )
    val greenAlpha = animateFloatAsState(
        targetValue = if (connected) 1f else 0f,
        animationSpec = tween(POWER_COLOR_MS, easing = FastOutSlowInEasing),
        label = "powerGreenAlpha",
    )
    val ringRotating = phase == ConnectionPhase.Connecting
    val ringSettled = phase == ConnectionPhase.Connected

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "powerPressScale",
    )

    val buttonSize = stageSize * (154f / 202f)
    val blueBrush = remember {
        Brush.linearGradient(listOf(Color(0xFF348AFF), ElectricBlue, Color(0xFF0755E5)))
    }
    val greenBrush = remember {
        Brush.linearGradient(listOf(Color(0xFF39E5AD), HardGreen, Color(0xFF0CA475)))
    }
    val haloBrush = remember(accent) {
        Brush.radialGradient(
            listOf(
                accent.copy(alpha = 0.19f),
                accent.copy(alpha = 0.055f),
                Color.Transparent,
            )
        )
    }

    Box(modifier = Modifier.size(stageSize), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxSize().background(haloBrush, CircleShape))
        Box(
            modifier = Modifier
                .size(stageSize * 0.94f)
                .border(1.dp, accent.copy(alpha = 0.12f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(stageSize * 0.81f)
                .border(1.dp, accent.copy(alpha = 0.08f), CircleShape)
        )

        AnimatedVisibility(
            visible = ringVisible,
            enter = fadeIn(tween(RING_FADE_MS)),
            exit = fadeOut(tween(RING_FADE_MS)),
        ) {
            ConnectionRing(
                green = connected || phase == ConnectionPhase.Disconnecting,
                rotating = ringRotating,
                settled = ringSettled,
                modifier = Modifier.size(stageSize),
            )
        }

        Box(
            modifier = Modifier
                .size(buttonSize)
                .graphicsLayer {
                    scaleX = pressScale.value
                    scaleY = pressScale.value
                }
                .clip(CircleShape)
                .background(blueBrush)
                .border(1.dp, Color.White.copy(alpha = 0.17f), CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = when (phase) {
                        ConnectionPhase.Connected -> "Disconnect"
                        ConnectionPhase.Connecting -> "Connecting"
                        ConnectionPhase.Disconnecting -> "Disconnecting"
                        ConnectionPhase.Disconnected,
                        ConnectionPhase.Error -> "Connect"
                    },
                    onClick = onClick,
                )
                .semantics {
                    contentDescription = when (phase) {
                        ConnectionPhase.Connected -> "Disconnect the VPN"
                        ConnectionPhase.Connecting -> "The VPN is connecting"
                        ConnectionPhase.Disconnecting -> "The VPN is disconnecting"
                        ConnectionPhase.Disconnected -> "Connect the VPN"
                        ConnectionPhase.Error -> "Connection failed. Tap to try again."
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = greenAlpha.value }
                    .background(greenBrush)
            )
            Canvas(modifier = Modifier.size(buttonSize * (54f / 154f))) {
                val strokeWidth = size.minDimension * (4f / 54f)
                drawArc(
                    color = Color.White,
                    startAngle = -52f,
                    sweepAngle = 284f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.17f, size.height * 0.19f),
                    size = Size(size.width * 0.66f, size.height * 0.66f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * 0.5f, size.height * 0.10f),
                    end = Offset(size.width * 0.5f, size.height * 0.47f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun ConnectionRing(
    green: Boolean,
    rotating: Boolean,
    settled: Boolean,
    modifier: Modifier = Modifier,
) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(rotating) {
        if (rotating) {
            while (true) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(RING_ROTATION_MS, easing = LinearEasing),
                )
                rotation.snapTo(rotation.value % 360f)
            }
        }
    }

    val ringColor by animateColorAsState(
        targetValue = if (green) HardGreen else ElectricBlue,
        animationSpec = tween(POWER_COLOR_MS, easing = FastOutSlowInEasing),
        label = "ringColor",
    )
    val tipColor by animateColorAsState(
        targetValue = if (green) Color(0xFFB9FFE6) else Color(0xFFDCEEFF),
        animationSpec = tween(POWER_COLOR_MS, easing = FastOutSlowInEasing),
        label = "ringTipColor",
    )
    val settledAlpha = animateFloatAsState(
        targetValue = if (settled) 1f else 0f,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "ringSettled",
    )
    val arcBrush = remember(ringColor, tipColor) {
        Brush.sweepGradient(
            0f to ringColor.copy(alpha = 0.02f),
            0.12f to ringColor.copy(alpha = 0.25f),
            0.26f to ringColor.copy(alpha = 0.90f),
            0.35f to tipColor,
            0.36f to Color.Transparent,
            1f to Color.Transparent,
        )
    }

    Canvas(
        modifier = modifier.graphicsLayer {
            // Rotating the layer keeps the small ring drawing cached while it spins.
            rotationZ = rotation.value
        }
    ) {
        val inset = 7.dp.toPx().coerceAtMost(size.minDimension * 0.06f)
        val arcTopLeft = Offset(inset, inset)
        val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
        val radius = arcSize.minDimension / 2f
        val staticAlpha = settledAlpha.value
        val arcAlpha = 1f - staticAlpha

        drawCircle(
            color = ringColor.copy(alpha = 0.14f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx()),
        )
        if (arcAlpha > 0.001f) {
            drawArc(
                color = ringColor.copy(alpha = 0.10f * arcAlpha),
                startAngle = 0f,
                sweepAngle = 126f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                brush = arcBrush,
                startAngle = 0f,
                sweepAngle = 126f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                alpha = arcAlpha,
                style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round),
            )
            val tip = Offset(
                x = center.x - radius * 0.58778524f,
                y = center.y + radius * 0.80901699f,
            )
            drawCircle(
                color = tipColor.copy(alpha = 0.55f * arcAlpha),
                radius = 3.3.dp.toPx(),
                center = tip,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.92f * arcAlpha),
                radius = 1.4.dp.toPx(),
                center = tip,
            )
        }
        if (staticAlpha > 0.001f) {
            drawCircle(
                color = ringColor.copy(alpha = 0.95f * staticAlpha),
                radius = radius,
                center = center,
                style = Stroke(width = 2.8.dp.toPx()),
            )
        }
    }
}

/** Cheap monotonic helper for UI-only timestamps (never used for session duration). */
fun elapsedSince(startedAtMillis: Long): Long =
    (SystemClock.elapsedRealtime() - startedAtMillis).coerceAtLeast(0L)
