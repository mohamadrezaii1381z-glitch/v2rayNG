package com.v2ray.ang.ui.hardvpn

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R

/*
 * Shared HardVPN surfaces. These are transcribed from the supplied design source so every
 * screen keeps the same card geometry, type scale, icon weight and interaction feedback.
 */

@Composable
fun GlobeBackground(modifier: Modifier = Modifier) {
    val overlay = remember {
        Brush.verticalGradient(
            listOf(
                Navy.copy(alpha = 0.55f),
                Navy.copy(alpha = 0.65f),
                Navy.copy(alpha = 0.84f),
                Navy.copy(alpha = 0.97f),
            )
        )
    }
    Box(modifier = modifier.background(Navy)) {
        Image(
            painter = painterResource(R.drawable.hardvpn_earth),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
        )
        Box(modifier = Modifier.fillMaxSize().background(overlay))
    }
}

/** Base card used by every HardVPN list and information block. */
@Composable
fun HardVpnCard(
    modifier: Modifier = Modifier,
    color: Color = CardColor,
    border: BorderStroke? = CardBorder,
    shape: androidx.compose.ui.graphics.Shape = CardShape,
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier, shape = shape, color = color, border = border) {
        content()
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                DrawIcon(AppIcon.Back, Color.White, Modifier.size(22.dp))
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null) 4.dp else 0.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Muted,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (action != null) action() else if (onBack != null) Spacer(Modifier.size(48.dp))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = LightBlue,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}

@Composable
fun PingDot(pingMs: Long?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(6.dp)
            .background(pingColor(pingMs), CircleShape)
    )
}

/** Four-bar signal meter driven only by a real measured ping. */
@Composable
fun SignalBars(pingMs: Long?, modifier: Modifier = Modifier) {
    val activeBars = activeSignalBars(pingMs)
    val activeColor = pingColor(pingMs)
    Canvas(
        modifier = modifier.semantics {
            contentDescription = if (activeBars == 0) {
                "Latency not measured yet"
            } else {
                "Signal quality based on measured latency"
            }
        }
    ) {
        for (bar in 0..3) {
            val barHeight = size.height * (0.3f + bar * 0.2f)
            drawRoundRect(
                color = if (bar < activeBars) activeColor
                else Color(0xFF8497AE).copy(alpha = 0.32f),
                topLeft = Offset(bar * size.width / 4f, size.height - barHeight),
                size = Size(size.width / 6f, barHeight),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
    }
}

/**
 * Country marker. Uses `flag_xx` when such a drawable is present and otherwise falls back to
 * the ISO code, so adding real flag assets later needs no code change.
 */
@Composable
fun CountryFlagChip(
    countryCode: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val context = LocalContext.current
    val drawableName = flagDrawableName(countryCode)
    val resourceId = remember(drawableName) {
        drawableName?.let { context.resources.getIdentifier(it, "drawable", context.packageName) } ?: 0
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF263A56), CircleShape)
            .semantics {
                contentDescription = countryCode?.let { "Country $it" } ?: "Location unknown"
            },
        contentAlignment = Alignment.Center,
    ) {
        if (resourceId != 0) {
            Image(
                painter = painterResource(resourceId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = countryCode ?: "VPN",
                color = Color.White,
                fontSize = if (size < 32.dp) 10.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/** Real traffic statistic card: speed plus this session's total. */
@Composable
fun StatisticCard(
    title: String,
    icon: AppIcon,
    accent: Color,
    speedBytesPerSecond: Long,
    totalBytes: Long,
    connected: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CardShape,
        color = CardColor.copy(alpha = 0.94f),
        border = CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = if (compact) 10.dp else 14.dp
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(23.dp)
                        .background(accent.copy(alpha = 0.17f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    DrawIcon(icon, accent, Modifier.size(13.dp))
                }
            }

            Spacer(Modifier.height(if (compact) 3.dp else 8.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.semantics {
                    contentDescription = if (connected) {
                        "$title speed ${formatSpeed(speedBytesPerSecond)}, session total ${formatBytes(totalBytes)}"
                    } else {
                        "$title speed unavailable while disconnected"
                    }
                },
            ) {
                Text(
                    text = if (connected) splitSpeedValue(speedBytesPerSecond) else "--",
                    color = Color.White,
                    fontSize = 22.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = if (connected) splitSpeedUnit(speedBytesPerSecond) else "B/s",
                    modifier = Modifier.padding(bottom = 3.dp),
                    color = Color(0xFFD0DAE8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(2.dp))
            Text(
                text = if (connected) formatBytes(totalBytes) else "0 B",
                color = Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

private fun splitSpeedValue(bytesPerSecond: Long): String =
    formatSpeed(bytesPerSecond).substringBefore(' ')

private fun splitSpeedUnit(bytesPerSecond: Long): String =
    formatSpeed(bytesPerSecond).substringAfter(' ', "B/s")

/** Home server card: friendly name, country, measured ping, signal and selection chevron. */
@Composable
fun SelectedServerCard(
    serverName: String,
    countryCode: String?,
    location: String,
    pingMs: Long?,
    sourceLabel: String,
    autoSelect: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Selected server $serverName. Open the Home server list."
            },
        shape = CardShape,
        color = CardColor,
        border = CardBorder,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 15.dp,
                vertical = if (compact) 11.dp else 14.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CountryFlagChip(countryCode = countryCode, modifier = Modifier.size(38.dp), size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = serverName.ifBlank { "No server selected" },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = listOf(location, sourceLabel)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { "Tap to choose a server" },
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (autoSelect) {
                    Text(
                        text = "Smart auto select",
                        color = LightBlue,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = pingLabel(pingMs),
                    color = pingColor(pingMs),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = if (pingMs != null && pingMs > 0L) "measured" else "untested",
                    color = Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(7.dp))
            SignalBars(pingMs = pingMs, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            DrawIcon(AppIcon.Chevron, Muted, Modifier.size(13.dp))
        }
    }
}

/**
 * "Boost Security" replaces the design's WARP card in place, keeping its geometry and
 * interaction. It is bound to one verified engine preference (local DNS interception) and
 * never claims protection that is not actually configured.
 */
@Composable
fun BoostSecurityCard(
    compact: Boolean,
    enabled: Boolean,
    connected: Boolean,
    onToggle: () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (enabled) ElectricBlue.copy(alpha = 0.55f)
        else Color.White.copy(alpha = 0.07f),
        animationSpec = tween(200),
        label = "boostBorder",
    )
    val cardColor by animateColorAsState(
        targetValue = if (enabled) SelectedCardColor else CardColor,
        animationSpec = tween(200),
        label = "boostCard",
    )

    Surface(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                stateDescription = when {
                    enabled && connected -> "Local DNS protection enabled in settings"
                    enabled -> "Local DNS protection enabled for the next connection"
                    else -> "Local DNS protection disabled"
                }
            },
        shape = CardShape,
        color = cardColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(
                start = 15.dp,
                end = 10.dp,
                top = if (compact) 6.dp else 9.dp,
                bottom = if (compact) 6.dp else 9.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(ElectricBlue.copy(alpha = 0.16f))
                    .border(1.dp, ElectricBlue.copy(alpha = 0.25f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                DrawIcon(AppIcon.Shield, LightBlue, Modifier.size(21.dp))
            }
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Boost Security",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "افزایش امنیت",
                    color = Color(0xFFD0E2FA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PingDot(pingMs = if (enabled) 30L else null, modifier = Modifier.size(5.dp))
                    Text(
                        text = when {
                            enabled && connected -> "Local DNS on · applies next connection"
                            enabled -> "Local DNS on · used on next connection"
                            else -> "Local DNS off · no protection claimed"
                        },
                        color = Muted,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(4.dp))
            HardVpnSwitch(selected = enabled, onToggle = onToggle)
        }
    }
}

@Composable
fun HardVpnSwitch(selected: Boolean, onToggle: () -> Unit) {
    val trackBorder by animateColorAsState(
        targetValue = if (selected) LightBlue.copy(alpha = 0.55f)
        else Color.White.copy(alpha = 0.14f),
        animationSpec = tween(200),
        label = "switchBorder",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (selected) 23.dp else 3.dp,
        animationSpec = tween(200),
        label = "switchKnob",
    )
    val trackBrush = remember {
        Brush.linearGradient(listOf(Color(0xFF348AFF), ElectricBlue))
    }

    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClickLabel = if (selected) "Turn off" else "Turn on",
                onClick = onToggle,
            )
            .semantics {
                stateDescription = if (selected) "On" else "Off"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 26.dp)
                .clip(RoundedCornerShape(50))
                .then(
                    if (selected) Modifier.background(trackBrush)
                    else Modifier.background(Color(0xFF22314A))
                )
                .border(1.dp, trackBorder, RoundedCornerShape(50)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = knobOffset)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Canvas(modifier = Modifier.size(11.dp)) {
                        val strokeWidth = size.minDimension * 0.22f
                        drawLine(
                            color = ElectricBlue,
                            start = Offset(size.width * 0.16f, size.height * 0.54f),
                            end = Offset(size.width * 0.40f, size.height * 0.78f),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = ElectricBlue,
                            start = Offset(size.width * 0.40f, size.height * 0.78f),
                            end = Offset(size.width * 0.86f, size.height * 0.24f),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sponsors & support placeholders. No sponsor name, image, link or payment address is
 * invented: the rows stay explicitly empty until real data is supplied.
 */
@Composable
fun SponsorsCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = CardColor.copy(alpha = 0.85f),
        border = CardBorder,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Gold.copy(alpha = 0.12f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                DrawIcon(AppIcon.Heart, Gold, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sponsors & Support",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = "Sponsor slots are ready, none configured yet",
                    color = Muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DrawIcon(AppIcon.Chevron, Muted, Modifier.size(13.dp))
        }
    }
}

@Composable
fun HardVpnBottomNavigation(
    selected: HardVpnDestination,
    compact: Boolean,
    onSelect: (HardVpnDestination) -> Unit,
) {
    Surface(color = Navy.copy(alpha = 0.94f)) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.07f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = if (compact) 2.dp else 5.dp)
            ) {
                NavigationItem(
                    title = "Home",
                    icon = AppIcon.Home,
                    selected = selected == HardVpnDestination.Home,
                    onClick = { onSelect(HardVpnDestination.Home) },
                    modifier = Modifier.weight(1f),
                )
                NavigationItem(
                    title = "ExploreVPN",
                    icon = AppIcon.Globe,
                    selected = selected == HardVpnDestination.ExploreVpn,
                    onClick = { onSelect(HardVpnDestination.ExploreVpn) },
                    modifier = Modifier.weight(1f),
                )
                NavigationItem(
                    title = "DNS Gaming",
                    icon = AppIcon.Bolt,
                    selected = selected == HardVpnDestination.DnsGaming,
                    onClick = { onSelect(HardVpnDestination.DnsGaming) },
                    modifier = Modifier.weight(1f),
                )
                NavigationItem(
                    title = "Import",
                    icon = AppIcon.Download,
                    selected = selected == HardVpnDestination.ImportConfig,
                    onClick = { onSelect(HardVpnDestination.ImportConfig) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavigationItem(
    title: String,
    icon: AppIcon,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) Color(0xFF6BA8FF) else Color(0xFFA5B5C9)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DrawIcon(icon, tint, Modifier.size(21.dp))
        Text(
            text = title,
            color = if (selected) Color.White else Muted,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Rounded single-line input used by the source, DNS and rename sheets. */
@Composable
fun HardVpnTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    minHeight: Dp = 52.dp,
    contentDescription: String? = null,
) {
    val shape = remember { RoundedCornerShape(14.dp) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else Modifier
            ),
        singleLine = singleLine,
        textStyle = TextStyle(
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        ),
        cursorBrush = SolidColor(LightBlue),
        keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = keyboardType),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .clip(shape)
                    .background(CardColor)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val shape = remember { RoundedCornerShape(14.dp) }
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = placeholder },
        singleLine = true,
        textStyle = TextStyle(
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        ),
        cursorBrush = SolidColor(LightBlue),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(shape)
                    .background(CardColor)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DrawIcon(AppIcon.Search, LightBlue, Modifier.size(19.dp))
                Spacer(Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Clear search",
                                onClick = { onQueryChange("") },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        DrawIcon(AppIcon.Close, Muted, Modifier.size(16.dp))
                    }
                }
            }
        },
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
    destructive: Boolean = false,
) {
    val shape = remember { RoundedCornerShape(14.dp) }
    val background = when {
        !enabled -> Color(0xFF22314A)
        destructive -> Color(0xFF3A1D26)
        outlined -> Color.Transparent
        else -> ElectricBlue
    }
    val contentColor = when {
        !enabled -> Muted.copy(alpha = 0.6f)
        destructive -> Color(0xFFFF9DAF)
        outlined -> LightBlue
        else -> Color.White
    }
    Surface(
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { this.stateDescription = if (enabled) "Enabled" else "Disabled" },
        enabled = enabled,
        shape = shape,
        color = background,
        border = if (outlined) BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)) else null,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: AppIcon,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DrawIcon(icon, LightBlue.copy(alpha = 0.75f), Modifier.size(28.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = body,
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
fun InfoBanner(message: String, isError: Boolean, onDismiss: () -> Unit) {
    val accent = if (isError) Color(0xFFFF8A9B) else LightBlue
    Surface(
        onClick = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics { contentDescription = message },
        shape = RoundedCornerShape(14.dp),
        color = if (isError) Color(0xFF2A1720) else Color(0xFF152A44),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DrawIcon(AppIcon.Info, accent, Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "OK",
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardColor,
        titleContentColor = Color.White,
        textContentColor = Muted,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = { Text(text = message, lineHeight = 20.sp, fontWeight = FontWeight.Medium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) Color(0xFFFF9DAF) else LightBlue,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = Muted)
            }
        },
    )
}
