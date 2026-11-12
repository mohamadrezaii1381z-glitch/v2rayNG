package com.v2ray.ang.hardvpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.hardvpn.HardVpnAction
import com.v2ray.ang.hardvpn.domain.ConnectionPhase
import com.v2ray.ang.hardvpn.domain.HardVpnFormat
import com.v2ray.ang.hardvpn.domain.HardVpnUiState
import com.v2ray.ang.hardvpn.domain.ServerUiModel

/**
 * HardVPN Home.
 *
 * The page keeps the supplied design's composition — globe backdrop, header, large power control,
 * session clock, twin traffic cards, selected-server card, security card, sponsors — while every
 * value comes from the daemon or the domain repository.
 */
@Composable
fun HomeScreen(
    state: HardVpnUiState,
    onAction: (HardVpnAction) -> Unit,
    onOpenPremium: () -> Unit,
    onOpenServers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connection = state.connection
    val seconds = rememberSessionSeconds(connection.connectedSinceMillis)
    val connected = connection.phase == ConnectionPhase.CONNECTED

    HardVpnBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HomeHeader(onOpenPremium = onOpenPremium)
            Spacer(Modifier.height(20.dp))
            PowerButton(
                phase = connection.phase,
                onToggle = { onAction(HardVpnAction.ToggleConnection) },
            )
            Spacer(Modifier.height(6.dp))
            ConnectionStatusBlock(
                phase = connection.phase,
                serverName = connection.activeServerName,
                seconds = seconds,
                hasError = connection.error != null,
            )
            Spacer(Modifier.height(18.dp))
            TrafficRow(state = state)
            Spacer(Modifier.height(14.dp))
            SelectedServerCard(
                state = state,
                onClick = onOpenServers,
            )
            Spacer(Modifier.height(12.dp))
            BoostSecurityCard(
                state = state.boost,
                onToggle = { onAction(HardVpnAction.ToggleBoostSecurity) },
            )
            Spacer(Modifier.height(22.dp))
            SponsorsSection()
        }
    }
}

@Composable
private fun HomeHeader(onOpenPremium: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.hardvpn_brand),
                color = HardVpnColors.TextPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
            )
            Text(
                text = stringResource(R.string.hardvpn_brand_tagline),
                color = HardVpnColors.TextMuted,
                fontSize = 11.sp,
            )
        }
        PremiumPill(onClick = onOpenPremium)
    }
}

@Composable
private fun TrafficRow(state: HardVpnUiState) {
    val connection = state.connection
    val connected = connection.phase == ConnectionPhase.CONNECTED
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(
            title = stringResource(R.string.hardvpn_download),
            value = if (connected) HardVpnFormat.speed(connection.downloadBytesPerSecond)
            else stringResource(R.string.hardvpn_stat_idle),
            detail = if (connected) {
                stringResource(
                    R.string.hardvpn_stat_session_total,
                    HardVpnFormat.data(connection.downloadedBytes),
                )
            } else {
                stringResource(R.string.hardvpn_stat_idle_detail)
            },
            accent = HardVpnColors.AccentBright,
            downward = true,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            title = stringResource(R.string.hardvpn_upload),
            value = if (connected) HardVpnFormat.speed(connection.uploadBytesPerSecond)
            else stringResource(R.string.hardvpn_stat_idle),
            detail = if (connected) {
                stringResource(
                    R.string.hardvpn_stat_session_total,
                    HardVpnFormat.data(connection.uploadedBytes),
                )
            } else {
                stringResource(R.string.hardvpn_stat_idle_detail)
            },
            accent = HardVpnColors.Success,
            downward = false,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SelectedServerCard(state: HardVpnUiState, onClick: () -> Unit) {
    val domain = state.home
    val connection = state.connection
    val server: ServerUiModel? = domain.servers.firstOrNull { it.guid == domain.selectedGuid }
    val auto = domain.autoSelect
    val connected = connection.phase == ConnectionPhase.CONNECTED

    val flag: String? = server?.flag
    val title = when {
        auto && server == null -> stringResource(R.string.hardvpn_auto_select)
        server != null -> server.name
        else -> stringResource(R.string.hardvpn_choose_server)
    }
    val subtitle = when {
        auto && server == null -> stringResource(R.string.hardvpn_auto_select_subtitle)
        connected && connection.activeServerName != null ->
            stringResource(R.string.hardvpn_connected_to, connection.activeServerName.orEmpty())

        server != null -> stringResource(R.string.hardvpn_change)
        else -> stringResource(R.string.hardvpn_choose_server_subtitle)
    }

    HardVpnCard(
        onClickLabel = stringResource(R.string.hardvpn_servers_title),
        onClick = onClick,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF1B3568), Color(0xFF10224A)))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    flag != null -> Text(text = flag, fontSize = 20.sp)
                    auto -> Text(
                        text = stringResource(R.string.hardvpn_auto_select_badge),
                        color = HardVpnColors.AccentBright,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                    )

                    else -> Icon(
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
                    text = title,
                    color = HardVpnColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = HardVpnColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (server != null) {
                PingChip(server.pingMillis)
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = HardVpnColors.TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        if (domain.sources.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    R.string.hardvpn_servers_subtitle,
                    domain.servers.size,
                    domain.sources.size,
                ),
                color = HardVpnColors.TextMuted,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun SponsorsSection() {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.hardvpn_sponsors_title),
            color = HardVpnColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.hardvpn_sponsors_subtitle),
            color = HardVpnColors.TextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SponsorSlot(
                title = stringResource(R.string.hardvpn_sponsor_slot),
                body = stringResource(R.string.hardvpn_sponsor_slot_body),
                modifier = Modifier.weight(1f),
            )
            SponsorSlot(
                title = stringResource(R.string.hardvpn_support_slot),
                body = stringResource(R.string.hardvpn_support_slot_body),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SponsorSlot(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x660B1730))
            .border(1.dp, Color(0x1F8FB2FF), RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF16294F), Color(0xFF0D1A34)))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "HARDVPN",
                color = HardVpnColors.TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            color = HardVpnColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = body,
            color = HardVpnColors.TextMuted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.hardvpn_coming_soon),
            color = HardVpnColors.Gold.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
