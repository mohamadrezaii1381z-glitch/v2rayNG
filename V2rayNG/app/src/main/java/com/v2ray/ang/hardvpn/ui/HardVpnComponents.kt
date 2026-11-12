package com.v2ray.ang.hardvpn.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.hardvpn.domain.BoostSecurityUiState
import com.v2ray.ang.hardvpn.domain.ConnectionPhase
import com.v2ray.ang.hardvpn.domain.HardVpnFormat
import com.v2ray.ang.hardvpn.domain.HardVpnTab
import com.v2ray.ang.hardvpn.domain.ServerUiModel
import kotlinx.coroutines.delay

/** The card surface used by every HardVPN panel. */
@Composable
fun HardVpnCard(
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    var base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(Color(0xFF111F3E), Color(0xFF0A152D))
            )
        )
        .border(1.dp, Color(0x2E5C8DFF), shape)
    base = if (onClick != null) {
        base.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
    } else {
        base
    }
    Column(modifier = base.padding(contentPadding), content = content)
}

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    listOf(HardVpnColors.AccentBright, HardVpnColors.Accent)
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "H",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
    }
}

@Composable
fun PremiumPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.hardvpn_premium)
    val description = stringResource(R.string.hardvpn_premium_title)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFFFCE3A1), Color(0xFFE9AF3C), Color(0xFFCE8A20))
                )
            )
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = Color(0xFF3A2703),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = Color(0xFF3A2703),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

@Composable
fun PowerGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 4.dp.toPx()
        val radius = size.minDimension * 0.33f
        drawArc(
            color = tint,
            startAngle = -48f,
            sweepAngle = 276f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(
            color = tint,
            start = Offset(center.x, center.y - radius * 1.18f),
            end = Offset(center.x, center.y - radius * 0.12f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun ShieldGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val path = Path().apply {
            moveTo(width * 0.5f, height * 0.08f)
            lineTo(width * 0.88f, height * 0.24f)
            lineTo(width * 0.88f, height * 0.52f)
            quadraticBezierTo(width * 0.88f, height * 0.84f, width * 0.5f, height * 0.95f)
            quadraticBezierTo(width * 0.12f, height * 0.84f, width * 0.12f, height * 0.52f)
            lineTo(width * 0.12f, height * 0.24f)
            close()
        }
        drawPath(path, color = tint.copy(alpha = 0.14f))
        drawPath(path, color = tint, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawLine(
            color = tint,
            start = Offset(width * 0.32f, height * 0.48f),
            end = Offset(width * 0.46f, height * 0.62f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(width * 0.46f, height * 0.62f),
            end = Offset(width * 0.70f, height * 0.36f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * The large circular power control. Its color and animation follow the real daemon phase only.
 */
@Composable
fun PowerButton(
    phase: ConnectionPhase,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = phase == ConnectionPhase.CONNECTED
    val busy = phase == ConnectionPhase.CONNECTING || phase == ConnectionPhase.DISCONNECTING
    val transition = rememberInfiniteTransition(label = "hardvpn-power")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "hardvpn-power-pulse",
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1100, easing = LinearEasing)),
        label = "hardvpn-power-sweep",
    )
    val accent = if (connected) HardVpnColors.Success else HardVpnColors.Accent
    val description = stringResource(
        if (connected) R.string.hardvpn_action_disconnect_a11y
        else R.string.hardvpn_action_connect_a11y
    )

    Box(modifier = modifier.size(236.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = center
            val baseRadius = size.minDimension / 2f - 6.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.28f), Color.Transparent),
                    center = centre,
                    radius = baseRadius * 1.15f,
                ),
                radius = baseRadius * 1.18f,
                center = centre,
            )
            drawCircle(
                color = accent.copy(alpha = 0.32f),
                radius = baseRadius * pulse,
                center = centre,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = baseRadius * 0.84f,
                center = centre,
                style = Stroke(width = 12.dp.toPx()),
            )
            if (busy) {
                drawArc(
                    color = accent,
                    startAngle = sweep,
                    sweepAngle = 96f,
                    useCenter = false,
                    topLeft = Offset(
                        centre.x - baseRadius * 0.76f,
                        centre.y - baseRadius * 0.76f,
                    ),
                    size = Size(baseRadius * 1.52f, baseRadius * 1.52f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(152.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            if (connected) Color(0xFF10483B) else Color(0xFF162E60),
                            Color(0xFF081226),
                        )
                    )
                )
                .border(1.dp, accent.copy(alpha = 0.6f), CircleShape)
                .clickable(
                    enabled = !busy,
                    role = Role.Button,
                    onClickLabel = description,
                    onClick = onToggle,
                )
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            PowerGlyph(
                tint = if (connected) HardVpnColors.Success else Color.White,
                modifier = Modifier.size(58.dp),
            )
        }
    }
}

/** One-second session clock. It only advances while the engine reports a live connection. */
@Composable
fun rememberSessionSeconds(connectedSinceMillis: Long?): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connectedSinceMillis) {
        while (connectedSinceMillis != null) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    if (connectedSinceMillis == null || connectedSinceMillis <= 0L) return 0L
    return ((now - connectedSinceMillis) / 1000L).coerceAtLeast(0L)
}

@Composable
fun StatCard(
    title: String,
    value: String,
    detail: String,
    accent: Color,
    downward: Boolean,
    modifier: Modifier = Modifier,
) {
    HardVpnCard(modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (downward) Icons.Filled.KeyboardArrowDown
                    else Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                color = HardVpnColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = value,
            color = HardVpnColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = detail,
            color = HardVpnColors.TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PingChip(pingMillis: Long?, modifier: Modifier = Modifier) {
    val (label, color) = when {
        pingMillis == null -> stringResource(R.string.hardvpn_ping_unknown) to HardVpnColors.TextMuted
        pingMillis < 0 -> stringResource(R.string.hardvpn_ping_failed) to HardVpnColors.Danger
        pingMillis <= 200 -> stringResource(R.string.hardvpn_ping_value, pingMillis) to HardVpnColors.Success
        pingMillis <= 500 -> stringResource(R.string.hardvpn_ping_value, pingMillis) to HardVpnColors.Gold
        else -> stringResource(R.string.hardvpn_ping_value, pingMillis) to HardVpnColors.Danger
    }
    Text(
        text = label,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
fun ServerRow(
    server: ServerUiModel,
    selected: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) Color(0xFF14294F) else Color(0xFF0B1730)
            )
            .border(
                width = 1.dp,
                color = if (active) HardVpnColors.Success.copy(alpha = 0.6f)
                else if (selected) HardVpnColors.Accent.copy(alpha = 0.55f)
                else Color(0x1A6E8CBF),
                shape = shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF13244A)),
            contentAlignment = Alignment.Center,
        ) {
            if (server.flag != null) {
                Text(text = server.flag, fontSize = 18.sp)
            } else {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    tint = HardVpnColors.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = server.name,
                color = HardVpnColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = server.location ?: stringResource(R.string.hardvpn_location_unknown),
                color = HardVpnColors.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        PingChip(server.pingMillis)
        if (active || selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = if (active) HardVpnColors.Success else HardVpnColors.AccentBright,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun AutoSelectRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF17326B), Color(0xFF0F2148))
                )
            )
            .border(
                width = 1.dp,
                color = if (selected) HardVpnColors.AccentBright.copy(alpha = 0.75f)
                else Color(0x334B7BFF),
                shape = shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(HardVpnColors.AccentBright, Color(0xFF7C4DFF))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.hardvpn_auto_select_badge),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.hardvpn_auto_select),
                color = HardVpnColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.hardvpn_auto_select_subtitle),
                color = HardVpnColors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = HardVpnColors.AccentBright,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun HardVpnSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        placeholder = {
            Text(
                text = stringResource(R.string.hardvpn_search_servers),
                color = HardVpnColors.TextMuted,
                fontSize = 14.sp,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = HardVpnColors.TextMuted,
                modifier = Modifier.size(20.dp),
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = HardVpnColors.Accent,
            unfocusedBorderColor = Color(0x336E8CBF),
            focusedContainerColor = Color(0xFF0B1730),
            unfocusedContainerColor = Color(0xFF0B1730),
            focusedTextColor = HardVpnColors.TextPrimary,
            unfocusedTextColor = HardVpnColors.TextPrimary,
            cursorColor = HardVpnColors.Accent,
        ),
    )
}

@Composable
fun IconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) HardVpnColors.TextSecondary else HardVpnColors.TextMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFF12224A)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HardVpnColors.AccentBright,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            color = HardVpnColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            color = HardVpnColors.TextMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(HardVpnColors.Accent)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = actionLabel,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Compact connection strip used by the ExploreVPN and Import Config destinations. */
@Composable
fun ConnectBar(
    phase: ConnectionPhase,
    subtitle: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = phase == ConnectionPhase.CONNECTED
    val busy = phase == ConnectionPhase.CONNECTING || phase == ConnectionPhase.DISCONNECTING
    val statusRes = when (phase) {
        ConnectionPhase.CONNECTED -> R.string.hardvpn_status_connected
        ConnectionPhase.CONNECTING -> R.string.hardvpn_status_connecting
        ConnectionPhase.DISCONNECTING -> R.string.hardvpn_status_disconnecting
        ConnectionPhase.ERROR -> R.string.hardvpn_status_error
        ConnectionPhase.DISCONNECTED -> R.string.hardvpn_status_disconnected
    }
    val status = stringResource(statusRes)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0D1A36))
            .border(1.dp, Color(0x2E5C8DFF), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    when (phase) {
                        ConnectionPhase.CONNECTED -> HardVpnColors.Success
                        ConnectionPhase.CONNECTING, ConnectionPhase.DISCONNECTING ->
                            HardVpnColors.Gold

                        ConnectionPhase.ERROR -> HardVpnColors.Danger
                        ConnectionPhase.DISCONNECTED -> HardVpnColors.TextMuted
                    }
                )
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = status,
                color = HardVpnColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = HardVpnColors.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = HardVpnColors.AccentBright,
                strokeWidth = 2.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (connected) Color(0xFF173F35) else HardVpnColors.Accent
                    )
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(
                            if (connected) R.string.hardvpn_action_disconnect
                            else R.string.hardvpn_action_connect
                        ),
                        onClick = onToggle,
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(
                        if (connected) R.string.hardvpn_action_disconnect
                        else R.string.hardvpn_action_connect
                    ),
                    color = if (connected) HardVpnColors.Success else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun BoostSecurityCard(
    state: BoostSecurityUiState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.hardvpn_boost_a11y)
    val status = stringResource(
        if (state.active) R.string.hardvpn_boost_active else R.string.hardvpn_boost_off
    )
    HardVpnCard(
        modifier = modifier
            .clickable(role = Role.Switch, onClickLabel = description, onClick = onToggle)
            .semantics { contentDescription = description },
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        if (state.active) Color(0xFF0F3B30) else Color(0xFF152A52)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                ShieldGlyph(
                    tint = if (state.active) HardVpnColors.Success else HardVpnColors.AccentBright,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.hardvpn_boost_title),
                    color = HardVpnColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.hardvpn_boost_subtitle),
                    color = HardVpnColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 2,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = status,
                    color = if (state.active) HardVpnColors.Success else HardVpnColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Switch(
                    checked = state.active,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = HardVpnColors.Success,
                        uncheckedThumbColor = HardVpnColors.TextSecondary,
                        uncheckedTrackColor = Color(0xFF1B2C4E),
                    ),
                )
            }
        }
        if (state.requiresReconnect) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.hardvpn_boost_reconnect),
                color = HardVpnColors.Gold,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
fun ConnectionStatusBlock(
    phase: ConnectionPhase,
    serverName: String?,
    seconds: Long,
    hasError: Boolean,
    modifier: Modifier = Modifier,
) {
    val statusRes = when (phase) {
        ConnectionPhase.CONNECTED -> R.string.hardvpn_status_connected
        ConnectionPhase.CONNECTING -> R.string.hardvpn_status_connecting
        ConnectionPhase.DISCONNECTING -> R.string.hardvpn_status_disconnecting
        ConnectionPhase.ERROR -> R.string.hardvpn_status_error
        ConnectionPhase.DISCONNECTED -> R.string.hardvpn_status_disconnected
    }
    val statusColor = when (phase) {
        ConnectionPhase.CONNECTED -> HardVpnColors.Success
        ConnectionPhase.CONNECTING, ConnectionPhase.DISCONNECTING -> HardVpnColors.Gold
        ConnectionPhase.ERROR -> HardVpnColors.Danger
        ConnectionPhase.DISCONNECTED -> HardVpnColors.TextSecondary
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(statusRes),
            color = statusColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = serverName ?: stringResource(R.string.hardvpn_choose_server),
            color = HardVpnColors.TextPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = HardVpnColors.TextMuted,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = stringResource(R.string.hardvpn_session_label) + "  " +
                    HardVpnFormat.duration(seconds),
                color = HardVpnColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (hasError) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.hardvpn_error_connection_failed),
                color = HardVpnColors.Danger,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
fun AddDestinationButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF142B57))
            .border(1.dp, Color(0x445C8DFF), RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.AddCircle,
            contentDescription = null,
            tint = HardVpnColors.AccentBright,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = HardVpnColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun HardVpnBottomBar(
    selected: HardVpnTab,
    onSelect: (HardVpnTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        Triple(HardVpnTab.HOME, Icons.Filled.Home, R.string.hardvpn_tab_home),
        Triple(HardVpnTab.EXPLORE, Icons.Filled.Place, R.string.hardvpn_tab_explore),
        Triple(HardVpnTab.DNS_GAMING, Icons.Filled.Lock, R.string.hardvpn_tab_dns),
        Triple(HardVpnTab.IMPORT_CONFIG, Icons.Filled.AddCircle, R.string.hardvpn_tab_import),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xF20A1428))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        items.forEach { (tab, icon, labelRes) ->
            val isSelected = tab == selected
            val label = stringResource(labelRes)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(tab) },
                    )
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) HardVpnColors.AccentBright else HardVpnColors.TextMuted,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    color = if (isSelected) HardVpnColors.TextPrimary else HardVpnColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}
